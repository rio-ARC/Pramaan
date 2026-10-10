package app.pramaan.server

import app.pramaan.core.manifest.CanonicalJson
import app.pramaan.core.manifest.SessionManifest
import app.pramaan.core.model.Badge
import app.pramaan.core.model.CheckResult
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream

@Serializable
data class UploadSessionResponse(
    val sessionId: String,
    val status: String,
    val integrity: String,
    val checks: List<CheckResult>
)

@Serializable
data class AthleteSessionSummaryResponse(
    val id: String,
    val test: String,
    val best: Double,
    val unit: String,
    val integrity: String,
    val status: String,
    val reviewedByName: String? = null,
    val submittedAt: Long? = null
)

@Serializable
data class AthleteSessionDetailResponse(
    val id: String,
    val test: String,
    val best: Double,
    val unit: String,
    val integrity: String,
    val status: String,
    val checks: List<CheckResult> = emptyList(),
    val reviewedByName: String? = null,
    val reviewReason: String? = null,
    val certificateJws: String? = null,
    val verifyUrl: String? = null,
    val submittedAt: Long? = null,
    val reviewedAt: Long? = null
)

fun Route.configureAthleteRoutes(
    uploadsDir: File,
    baseUrl: String = "http://127.0.0.1:8080"
) {
    val json = Json { ignoreUnknownKeys = true }

    authenticate("api") {
        // 1. Submit session (multipart upload)
        post("/api/sessions") {
            val principal = call.ensureRole("ATHLETE") ?: return@post
            val account = principal.account

            val multipart = call.receiveMultipart()
            val tempFolder = File(uploadsDir, "temp_${System.currentTimeMillis()}_${principal.accountId}").apply { mkdirs() }
            var manifestBytes: ByteArray? = null

            try {
                multipart.forEachPart { part ->
                    when (part) {
                        is PartData.FileItem -> {
                            val fileName = part.originalFileName ?: part.name ?: "file"
                            val targetFile = File(tempFolder, fileName)
                            part.streamProvider().use { input ->
                                FileOutputStream(targetFile).use { output ->
                                    input.copyTo(output)
                                }
                            }
                            if (fileName == "manifest.json") {
                                manifestBytes = targetFile.readBytes()
                            }
                        }
                        else -> {}
                    }
                    part.dispose()
                }

                if (manifestBytes == null) {
                    tempFolder.deleteRecursively()
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("MISSING_MANIFEST", "Missing manifest.json"))
                    return@post
                }

                val manifest = try {
                    CanonicalJson.decodeFromByteArray(SessionManifest.serializer(), manifestBytes!!)
                } catch (e: Exception) {
                    tempFolder.deleteRecursively()
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("INVALID_MANIFEST", "Could not parse manifest.json: ${e.message}"))
                    return@post
                }

                // Check account & hardware key binding (Spec 5.2)
                val accountDeviceKey = account.devicePubkey?.trim() ?: ""
                val manifestDeviceKey = manifest.devicePublicKey.trim()
                if (manifest.athlete.id != account.id || (accountDeviceKey.isNotEmpty() && manifestDeviceKey != accountDeviceKey)) {
                    tempFolder.deleteRecursively()
                    call.respond(
                        HttpStatusCode.Forbidden,
                        ErrorResponse("WRONG_DEVICE", "Session was recorded on a device not bound to this athlete account")
                    )
                    return@post
                }

                // Run verification
                val verifyResult = SessionVerifier.verifyUploadedSession(tempFolder)
                val sessionId = manifest.sessionId

                val sessionFolder = File(uploadsDir, sessionId)
                if (sessionFolder.exists()) {
                    sessionFolder.deleteRecursively()
                }
                tempFolder.renameTo(sessionFolder)

                val finalBadge = verifyResult.outcome.badge
                val status = if (finalBadge == Badge.TAMPERED) "AUTO_REJECTED" else "AWAITING_REVIEW"
                val integrityStr = finalBadge.name
                val integrityJsonStr = json.encodeToString(verifyResult.outcome.checks)
                val now = System.currentTimeMillis()

                val record = ServerSessionRecord(
                    id = sessionId,
                    athleteId = manifest.athlete.id,
                    athleteInitials = manifest.athlete.initials,
                    test = manifest.test.name,
                    best = manifest.result.best,
                    unit = manifest.result.unit,
                    status = status,
                    integrity = integrityStr,
                    integrityJson = integrityJsonStr,
                    manifestSha256 = verifyResult.manifestSha256,
                    submittedAt = now,
                    capturedAt = manifest.capturedAt,
                    rawManifest = manifestBytes!!.decodeToString()
                )
                ServerDb.saveOrUpdate(record)

                // Log timeline events
                EventLog.log(sessionId, "SUBMITTED", actorId = account.id, detail = "Submitted by athlete ${account.name}", timestamp = now)
                EventLog.log(sessionId, "SERVER_VERIFIED", actorId = "SERVER", detail = "Integrity badge: $integrityStr, status: $status", timestamp = now)

                val response = UploadSessionResponse(
                    sessionId = sessionId,
                    status = status,
                    integrity = integrityStr,
                    checks = verifyResult.outcome.checks
                )
                call.respond(response)
            } catch (e: Exception) {
                tempFolder.deleteRecursively()
                e.printStackTrace()
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse("VERIFICATION_FAILED", "Verification failed: ${e.message}"))
            }
        }

        // 2. List sessions for current athlete
        get("/api/me/sessions") {
            val principal = call.ensureRole("ATHLETE") ?: return@get
            val sessions = ServerDb.getByAthleteId(principal.accountId)

            val summaries = sessions.map { sess ->
                AthleteSessionSummaryResponse(
                    id = sess.id,
                    test = sess.test,
                    best = sess.best,
                    unit = sess.unit,
                    integrity = sess.integrity ?: "VERIFIED",
                    status = sess.status,
                    reviewedByName = sess.reviewedBy?.let { AccountRepo.getAccount(it)?.name },
                    submittedAt = sess.submittedAt
                )
            }
            call.respond(summaries)
        }

        // 3. Get single session details for polling
        get("/api/me/sessions/{id}") {
            val principal = call.ensureRole("ATHLETE") ?: return@get
            val id = call.parameters["id"] ?: ""
            val session = ServerDb.getById(id)

            if (session == null || session.athleteId != principal.accountId) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Session not found"))
                return@get
            }

            val checks = if (!session.integrityJson.isNullOrBlank()) {
                try {
                    json.decodeFromString<List<CheckResult>>(session.integrityJson)
                } catch (_: Exception) {
                    emptyList()
                }
            } else emptyList()

            val verifyUrl = session.certificateJws?.let { "$baseUrl/v/$it" }
            val reviewerName = session.reviewedBy?.let { AccountRepo.getAccount(it)?.name }

            call.respond(
                AthleteSessionDetailResponse(
                    id = session.id,
                    test = session.test,
                    best = session.best,
                    unit = session.unit,
                    integrity = session.integrity ?: "VERIFIED",
                    status = session.status,
                    checks = checks,
                    reviewedByName = reviewerName,
                    reviewReason = session.reviewReason,
                    certificateJws = session.certificateJws,
                    verifyUrl = verifyUrl,
                    submittedAt = session.submittedAt,
                    reviewedAt = session.reviewedAt
                )
            )
        }
    }
}
