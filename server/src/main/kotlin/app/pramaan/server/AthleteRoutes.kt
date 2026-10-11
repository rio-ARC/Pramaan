package app.pramaan.server

import app.pramaan.core.manifest.CanonicalJson
import app.pramaan.core.manifest.SessionManifest
import app.pramaan.core.model.Badge
import app.pramaan.core.model.CheckResult
import app.pramaan.core.model.TestType
import app.pramaan.core.scoring.broadjump.BjAttempt
import app.pramaan.core.scoring.broadjump.GroundCalibration
import io.ktor.http.ContentDisposition
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
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
    val attempts: List<BjAttempt>? = null,
    val calibration: GroundCalibration? = null,
    val calibFrameUrl: String? = null,
    val stillUrls: List<String>? = null,
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
                            val partName = part.name ?: ""
                            val originalName = part.originalFileName ?: ""
                            val rawName = originalName.ifBlank { partName.ifBlank { "file" } }

                            val targetFile = if (partName == "calib_frame" || rawName.contains("calib_frame")) {
                                File(tempFolder, "calib_frame.jpg")
                            } else if (partName.startsWith("still_") || rawName.contains("still") || rawName.contains("landing") || rawName.startsWith("stills")) {
                                val stillsDir = File(tempFolder, "stills").apply { if (!exists()) mkdirs() }
                                val base = File(rawName).name
                                val outName = if (base.endsWith(".jpg", ignoreCase = true)) base else "$base.jpg"
                                File(stillsDir, outName)
                            } else {
                                val clean = File(rawName).name
                                File(tempFolder, clean)
                            }

                            targetFile.parentFile?.mkdirs()
                            part.streamProvider().use { input ->
                                FileOutputStream(targetFile).use { output ->
                                    input.copyTo(output)
                                }
                            }
                            if (targetFile.name == "manifest.json") {
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

                // Rule 3: Test gating (§9.3)
                if (manifest.test == TestType.BROAD_JUMP) {
                    val broadAttempts = manifest.result.broadJumpAttempts ?: emptyList()
                    if (manifest.result.best == null && broadAttempts.isEmpty()) {
                        tempFolder.deleteRecursively()
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse("NO_ATTEMPTS", "Cannot submit broad jump session with no attempts recorded")
                        )
                        return@post
                    }
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
                val verifyResult = SessionVerifier.verifyUploadedSession(tempFolder, account)
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

            val manifest = session.rawManifest?.let { raw ->
                try {
                    CanonicalJson.decodeFromString(SessionManifest.serializer(), raw)
                } catch (_: Exception) {
                    null
                }
            }

            val sessionDir = File(uploadsDir, id)
            val calibFrameUrl = if (File(sessionDir, "calib_frame.jpg").exists()) {
                "$baseUrl/api/me/sessions/$id/files/calib_frame.jpg"
            } else null

            val stillsDir = File(sessionDir, "stills")
            val stillUrls = if (stillsDir.exists() && stillsDir.isDirectory) {
                stillsDir.listFiles()?.filter { it.isFile }?.sortedBy { it.name }?.map {
                    "$baseUrl/api/me/sessions/$id/files/${it.name}"
                }
            } else null

            call.respond(
                AthleteSessionDetailResponse(
                    id = session.id,
                    test = session.test,
                    best = session.best,
                    unit = session.unit,
                    integrity = session.integrity ?: "VERIFIED",
                    status = session.status,
                    checks = checks,
                    attempts = manifest?.result?.broadJumpAttempts,
                    calibration = manifest?.calibration,
                    calibFrameUrl = calibFrameUrl,
                    stillUrls = stillUrls,
                    reviewedByName = reviewerName,
                    reviewReason = session.reviewReason,
                    certificateJws = session.certificateJws,
                    verifyUrl = verifyUrl,
                    submittedAt = session.submittedAt,
                    reviewedAt = session.reviewedAt
                )
            )
        }

        // 4. File download endpoint for athlete
        get("/api/me/sessions/{id}/files/{name}") {
            val principal = call.ensureRole("ATHLETE") ?: return@get
            val id = call.parameters["id"] ?: ""
            val name = call.parameters["name"] ?: ""
            if (name.contains("..")) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid file path"))
                return@get
            }

            val session = ServerDb.getById(id)
            if (session == null || session.athleteId != principal.accountId) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Session not found"))
                return@get
            }

            val sessionDir = File(uploadsDir, id)
            val targetFile = File(sessionDir, name).takeIf { it.exists() && it.isFile }
                ?: File(File(sessionDir, "stills"), name).takeIf { it.exists() && it.isFile }

            if (targetFile == null || !targetFile.exists()) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "File not found"))
                return@get
            }

            call.response.header(
                HttpHeaders.ContentDisposition,
                ContentDisposition.Inline.withParameter(ContentDisposition.Parameters.FileName, targetFile.name).toString()
            )
            call.respondFile(targetFile)
        }
    }
}
