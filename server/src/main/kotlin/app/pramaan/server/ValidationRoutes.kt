package app.pramaan.server

import app.pramaan.core.validation.AccuracyStatsCalculator
import app.pramaan.server.DashboardPages.renderValidationPage
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.server.html.respondHtml
import io.ktor.server.request.contentType
import io.ktor.server.request.receiveMultipart
import io.ktor.server.request.receiveParameters
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.configureValidationRoutes() {
    // 1. Validation Dashboard Page
    get("/validation") {
        val comparisons = ValidationService.getComparisons()
        val stats = AccuracyStatsCalculator.compute(comparisons)
        val imported = call.request.queryParameters["imported"]?.toIntOrNull()
        call.respondHtml {
            renderValidationPage(
                stats = stats,
                comparisons = comparisons,
                importedCount = imported
            )
        }
    }

    // 2. CSV Bulk Import
    post("/validation/import") {
        var csvText = ""
        val contentType = call.request.contentType()

        if (contentType.match(ContentType.MultiPart.FormData)) {
            val multipart = call.receiveMultipart()
            multipart.forEachPart { part ->
                if (part is PartData.FileItem) {
                    val bytes = part.streamProvider().readBytes()
                    csvText = String(bytes)
                } else if (part is PartData.FormItem && part.name == "csv") {
                    csvText = part.value
                }
                part.dispose()
            }
        } else if (contentType.match(ContentType.Application.FormUrlEncoded)) {
            val params = call.receiveParameters()
            csvText = params["csv"] ?: ""
        } else {
            csvText = call.receiveText()
        }

        val count = ValidationRepo.bulkImportCsv(csvText)

        // If client requested JSON or is API client
        if (call.request.headers[HttpHeaders.Accept]?.contains("application/json") == true) {
            call.respond(HttpStatusCode.OK, mapOf("imported" to count))
        } else {
            call.respondRedirect("/validation?imported=$count")
        }
    }

    // 3. Export CSV
    get("/validation/export.csv") {
        val entries = ValidationRepo.getAll()
        val sb = StringBuilder()
        sb.append("sessionId,attempt,tapeCm,truthFoul,dataSet,enteredAt\n")
        for (e in entries) {
            sb.append("${e.sessionId},${e.attempt},${e.tapeCm},${e.truthFoul},${e.dataSet},${e.enteredAt}\n")
        }
        call.response.header(
            HttpHeaders.ContentDisposition,
            ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, "pramaan_validation_entries.csv").toString()
        )
        call.respondText(sb.toString(), ContentType.Text.CSV)
    }

    // 4. Record single tape entry from form
    post("/validation/entry") {
        val params = call.receiveParameters()
        val sessionId = params["sessionId"]?.trim() ?: ""
        val attempt = params["attempt"]?.toIntOrNull() ?: 1
        val tapeCm = params["tapeCm"]?.toDoubleOrNull() ?: 0.0
        val truthFoul = params["truthFoul"]?.trim() ?: "NONE"
        val dataSet = params["dataSet"]?.trim() ?: "REPORT"

        if (sessionId.isNotBlank() && tapeCm > 0.0) {
            ValidationRepo.upsert(
                ValidationEntryRecord(
                    sessionId = sessionId,
                    attempt = attempt,
                    tapeCm = tapeCm,
                    truthFoul = truthFoul,
                    dataSet = dataSet,
                    enteredAt = System.currentTimeMillis()
                )
            )
        }

        val referer = call.request.headers[HttpHeaders.Referrer]
        if (!referer.isNullOrBlank()) {
            call.respondRedirect(referer)
        } else {
            call.respondRedirect("/submissions/$sessionId")
        }
    }

    // 5. JSON stats API
    get("/api/validation/stats") {
        val stats = ValidationService.getAccuracyStats()
        call.respond(stats)
    }
}
