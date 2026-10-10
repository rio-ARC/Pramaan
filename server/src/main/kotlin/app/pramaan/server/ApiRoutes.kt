package app.pramaan.server

import app.pramaan.server.DashboardPages.renderVerificationPage
import io.ktor.http.ContentDisposition
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.html.respondHtml
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class HealthStatusResponse(
    val status: String = "OK",
    val service: String = "PRAMAAN_VERIFICATION_SERVER",
    val version: String = "1.0",
    val timestamp: Long = 0L
)

fun Route.configureRoutes(
    certificateIssuer: ServerCertificateIssuer,
    uploadsDir: File = File("uploads").apply { if (!exists()) mkdirs() },
    photosDir: File = File("photos").apply { if (!exists()) mkdirs() },
    lanIp: String = "127.0.0.1"
) {
    val baseUrl = "http://$lanIp:8080"

    // 0. Diagnostic health check
    get("/api/health") {
        call.respond(
            HealthStatusResponse(
                status = "OK",
                service = "PRAMAAN_VERIFICATION_SERVER",
                version = "1.0",
                timestamp = System.currentTimeMillis()
            )
        )
    }

    // 1. Digital Certificate Verification (Public)
    get("/v/{jws}") {
        val jws = call.parameters["jws"] ?: ""
        val claims = certificateIssuer.verifyCertificate(jws)
        val isValid = claims != null
        call.respondHtml {
            renderVerificationPage(claims, isValid)
        }
    }

    // 2. Media Streaming
    get("/media/{id}/video.mp4") {
        val id = call.parameters["id"] ?: ""
        val videoFile = File(File(uploadsDir, id), "video.mp4")
        if (videoFile.exists()) {
            call.response.header(
                HttpHeaders.ContentDisposition,
                ContentDisposition.Inline.withParameter(ContentDisposition.Parameters.FileName, "video.mp4").toString()
            )
            call.respondFile(videoFile)
        } else {
            call.respond(HttpStatusCode.NotFound, "Video not found")
        }
    }

    // 3. Mount modular sub-routes
    configureAuthRoutes(photosDir = photosDir)
    configureAthleteRoutes(uploadsDir = uploadsDir, baseUrl = baseUrl)
    configureAdminRoutes(lanIp = lanIp, uploadsDir = uploadsDir)

    // 4. Backward-compatible read-only API sessions queries
    get("/api/sessions") {
        call.respond(ServerDb.getAll())
    }

    get("/api/sessions/{id}") {
        val id = call.parameters["id"] ?: ""
        val session = ServerDb.getById(id)
        if (session != null) {
            call.respond(session)
        } else {
            call.respond(HttpStatusCode.NotFound, "Session not found")
        }
    }
}
