package app.pramaan.server

import app.pramaan.core.model.Badge
import app.pramaan.core.model.CheckResult
import app.pramaan.server.DashboardPages.renderDashboard
import app.pramaan.server.DashboardPages.renderVerificationPage
import io.ktor.http.ContentDisposition
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.server.html.respondHtml
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Serializable
data class ServerSyncResponse(
    val sessionId: String,
    val serverBadge: Badge,
    val checks: List<CheckResult>,
    val certificate: String? = null
)

fun Route.configureRoutes(
    certificateIssuer: ServerCertificateIssuer,
    uploadsDir: File = File("uploads").apply { if (!exists()) mkdirs() }
) {
    // 1. Dashboard UI
    get("/") {
        val sessions = ServerDb.getAll()
        call.respondHtml {
            renderDashboard(sessions)
        }
    }

    // 2. Standalone Certificate Verification Page
    get("/v/{jws}") {
        val jws = call.parameters["jws"] ?: ""
        val claims = certificateIssuer.verifyCertificate(jws)
        val isValid = claims != null
        call.respondHtml {
            renderVerificationPage(claims, isValid)
        }
    }

    // 3. Media Streaming for Web Dashboard
    get("/media/{id}/video.mp4") {
        val id = call.parameters["id"] ?: ""
        val videoFile = File(File(uploadsDir, id), "video.mp4")
        if (videoFile.exists()) {
            call.response.header(HttpHeaders.ContentDisposition, ContentDisposition.Inline.withParameter(ContentDisposition.Parameters.FileName, "video.mp4").toString())
            call.respondFile(videoFile)
        } else {
            call.respond(HttpStatusCode.NotFound, "Video not found")
        }
    }

    // 4. REST API: List Sessions
    get("/api/sessions") {
        call.respond(ServerDb.getAll())
    }

    // 5. REST API: Get Session Details
    get("/api/sessions/{id}") {
        val id = call.parameters["id"] ?: ""
        val session = ServerDb.getById(id)
        if (session != null) {
            call.respond(session)
        } else {
            call.respond(HttpStatusCode.NotFound, "Session not found")
        }
    }

    // 6. REST API: Multipart Upload Session
    post("/api/sessions") {
        val multipart = call.receiveMultipart()
        val tempFolder = File(uploadsDir, "temp_${System.currentTimeMillis()}").apply { mkdirs() }

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
                call.respond(HttpStatusCode.BadRequest, "Missing manifest.json")
                return@post
            }

            // Run verification
            val verifyResult = SessionVerifier.verifyUploadedSession(tempFolder)
            val manifest = verifyResult.manifest
            val sessionId = manifest.sessionId

            // Move temp folder to permanent sessionId folder
            val sessionFolder = File(uploadsDir, sessionId)
            if (sessionFolder.exists()) {
                sessionFolder.deleteRecursively()
            }
            tempFolder.renameTo(sessionFolder)

            val finalBadge = verifyResult.outcome.badge
            val witnessShortId = verifyResult.witnessAttestation?.witnessShortId

            // Issue digital certificate if not tampered
            val certificateJws = if (finalBadge != Badge.TAMPERED && finalBadge != Badge.FLAGGED) {
                certificateIssuer.issueCertificate(
                    manifest = manifest,
                    finalBadge = finalBadge,
                    witnessShortId = witnessShortId
                )
            } else null

            val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val syncedAt = isoFormat.format(Date())

            val record = ServerSessionRecord(
                id = sessionId,
                athleteId = manifest.athlete.id,
                athleteInitials = manifest.athlete.initials,
                test = manifest.test.name,
                best = manifest.result.best,
                unit = manifest.result.unit,
                badge = finalBadge,
                capturedAt = manifest.capturedAt,
                syncedAt = syncedAt,
                rawManifest = manifestBytes!!.decodeToString(),
                certJws = certificateJws
            )
            ServerDb.save(record)

            val response = ServerSyncResponse(
                sessionId = sessionId,
                serverBadge = finalBadge,
                checks = verifyResult.outcome.checks,
                certificate = certificateJws
            )

            call.respond(response)
        } catch (e: Exception) {
            tempFolder.deleteRecursively()
            e.printStackTrace()
            call.respond(HttpStatusCode.InternalServerError, "Verification failed: ${e.message}")
        }
    }
}
