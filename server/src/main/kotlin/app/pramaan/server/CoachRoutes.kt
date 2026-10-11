package app.pramaan.server

import app.pramaan.core.model.CheckResult
import io.ktor.http.ContentDisposition
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.time.LocalDate
import java.time.Period

@Serializable
data class CoachQueueItemResponse(
    val sessionId: String,
    val athleteId: String,
    val athleteName: String,
    val athletePhotoUrl: String?,
    val age: Int?,
    val sex: String?,
    val test: String,
    val best: Double,
    val unit: String,
    val integrity: String,
    val submittedAt: Long?
)

@Serializable
data class CoachSessionDetailResponse(
    val sessionId: String,
    val athleteId: String,
    val athleteName: String,
    val athletePhotoUrl: String?,
    val age: Int?,
    val sex: String?,
    val heightCm: Double?,
    val weightKg: Double?,
    val test: String,
    val best: Double,
    val unit: String,
    val integrity: String,
    val status: String,
    val manifestSha256: String,
    val videoUrl: String,
    val checks: List<CheckResult>,
    val submittedAt: Long?,
    val reviewedBy: String? = null,
    val reviewedByName: String? = null,
    val reviewMethod: String? = null,
    val reviewReason: String? = null,
    val certificateJws: String? = null
)

@Serializable
data class CoachReviewRequest(
    val decision: String,
    val method: String = "REMOTE",
    val reason: String? = null,
    val scannedQr: String? = null,
    val reviewedAt: Long,
    val coachSignature: String
)

@Serializable
data class CoachReviewResponse(
    val sessionId: String,
    val status: String,
    val integrity: String,
    val certificateJws: String?,
    val verifyUrl: String?
)

@Serializable
data class CoachHistoryItemResponse(
    val sessionId: String,
    val athleteId: String,
    val athleteName: String,
    val test: String,
    val best: Double,
    val unit: String,
    val status: String,
    val integrity: String,
    val reviewMethod: String?,
    val reviewedAt: Long?,
    val certificateJws: String?
)

fun Route.configureCoachRoutes(
    accountRepo: AccountRepo,
    reviewService: ReviewService,
    uploadsDir: File = File("uploads").apply { if (!exists()) mkdirs() },
    baseUrl: () -> String = { "http://localhost:8080" }
) {
    authenticate("api") {
        route("/api/coach") {
            // GET /api/coach/queue
            get("/queue") {
                val principal = call.ensureRole("COACH") ?: return@get
                val items = transaction {
                    SessionsTable.selectAll()
                        .where { SessionsTable.status eq "AWAITING_REVIEW" }
                        .orderBy(SessionsTable.submittedAt to SortOrder.DESC)
                        .map { row ->
                            val aId = row[SessionsTable.athleteId] ?: ""
                            val athlete = accountRepo.getAccount(aId)
                            val age = athlete?.dob?.let {
                                try {
                                    Period.between(LocalDate.parse(it), LocalDate.now()).years
                                } catch (_: Exception) {
                                    null
                                }
                            }
                            val photoUrl = if (athlete?.photoPath != null) {
                                "${baseUrl().removeSuffix("/")}/api/accounts/$aId/photo"
                            } else null

                            CoachQueueItemResponse(
                                sessionId = row[SessionsTable.id],
                                athleteId = aId,
                                athleteName = athlete?.name ?: "Unknown Athlete",
                                athletePhotoUrl = photoUrl,
                                age = age,
                                sex = athlete?.sex,
                                test = row[SessionsTable.test],
                                best = row[SessionsTable.best],
                                unit = row[SessionsTable.unit],
                                integrity = row[SessionsTable.integrity] ?: "UNKNOWN",
                                submittedAt = row[SessionsTable.submittedAt]
                            )
                        }
                }
                call.respond(items)
            }

            // GET /api/coach/history
            get("/history") {
                val principal = call.ensureRole("COACH") ?: return@get
                val coachId = principal.accountId
                val history = transaction {
                    SessionsTable.selectAll()
                        .where { SessionsTable.reviewedBy eq coachId }
                        .orderBy(SessionsTable.reviewedAt to SortOrder.DESC)
                        .map { row ->
                            val aId = row[SessionsTable.athleteId] ?: ""
                            val athlete = accountRepo.getAccount(aId)
                            CoachHistoryItemResponse(
                                sessionId = row[SessionsTable.id],
                                athleteId = aId,
                                athleteName = athlete?.name ?: "Athlete",
                                test = row[SessionsTable.test],
                                best = row[SessionsTable.best],
                                unit = row[SessionsTable.unit],
                                status = row[SessionsTable.status],
                                integrity = row[SessionsTable.integrity] ?: "UNKNOWN",
                                reviewMethod = row[SessionsTable.reviewMethod],
                                reviewedAt = row[SessionsTable.reviewedAt],
                                certificateJws = row[SessionsTable.certificateJws]
                            )
                        }
                }
                call.respond(history)
            }

            // GET /api/coach/sessions/{id}
            get("/sessions/{id}") {
                val principal = call.ensureRole("COACH") ?: return@get
                val sessionId = call.parameters["id"] ?: ""
                val row = transaction {
                    SessionsTable.selectAll().where { SessionsTable.id eq sessionId }.singleOrNull()
                }

                if (row == null) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ErrorResponse(
                            error = "SESSION_NOT_FOUND",
                            message = "Session record has not arrived on server yet. Please ensure athlete has submitted."
                        )
                    )
                    return@get
                }

                val aId = row[SessionsTable.athleteId] ?: ""
                val athlete = accountRepo.getAccount(aId)
                val age = athlete?.dob?.let {
                    try {
                        Period.between(LocalDate.parse(it), LocalDate.now()).years
                    } catch (_: Exception) {
                        null
                    }
                }
                val photoUrl = if (athlete?.photoPath != null) {
                    "${baseUrl().removeSuffix("/")}/api/accounts/$aId/photo"
                } else null

                val checks = row[SessionsTable.integrityJson]?.let { jsonStr ->
                    try {
                        Json.decodeFromString<List<CheckResult>>(jsonStr)
                    } catch (_: Exception) {
                        emptyList()
                    }
                } ?: emptyList()

                val reviewer = row[SessionsTable.reviewedBy]?.let { accountRepo.getAccount(it) }

                call.respond(
                    CoachSessionDetailResponse(
                        sessionId = row[SessionsTable.id],
                        athleteId = aId,
                        athleteName = athlete?.name ?: "Athlete",
                        athletePhotoUrl = photoUrl,
                        age = age,
                        sex = athlete?.sex,
                        heightCm = athlete?.heightCm,
                        weightKg = athlete?.weightKg,
                        test = row[SessionsTable.test],
                        best = row[SessionsTable.best],
                        unit = row[SessionsTable.unit],
                        integrity = row[SessionsTable.integrity] ?: "UNKNOWN",
                        status = row[SessionsTable.status],
                        manifestSha256 = row[SessionsTable.manifestSha256] ?: "",
                        videoUrl = "${baseUrl().removeSuffix("/")}/api/coach/sessions/$sessionId/video",
                        checks = checks,
                        submittedAt = row[SessionsTable.submittedAt],
                        reviewedBy = row[SessionsTable.reviewedBy],
                        reviewedByName = reviewer?.name,
                        reviewMethod = row[SessionsTable.reviewMethod],
                        reviewReason = row[SessionsTable.reviewReason],
                        certificateJws = row[SessionsTable.certificateJws]
                    )
                )
            }

            // GET /api/coach/sessions/{id}/video
            get("/sessions/{id}/video") {
                val principal = call.ensureRole("COACH") ?: return@get
                val sessionId = call.parameters["id"] ?: ""
                val sessionDir = File(uploadsDir, sessionId)
                val videoFile = File(sessionDir, "video.mp4")

                if (!videoFile.exists()) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Video not found"))
                    return@get
                }

                call.response.header(
                    HttpHeaders.ContentDisposition,
                    ContentDisposition.Inline.withParameter(ContentDisposition.Parameters.FileName, "video.mp4").toString()
                )
                call.respondFile(videoFile)
            }

            // POST /api/coach/sessions/{id}/review
            post("/sessions/{id}/review") {
                val principal = call.ensureRole("COACH") ?: return@post
                val coachId = principal.accountId
                val sessionId = call.parameters["id"] ?: ""
                val request = call.receive<CoachReviewRequest>()

                val outcome = reviewService.reviewSession(
                    sessionId = sessionId,
                    coachId = coachId,
                    decision = request.decision,
                    requestedMethod = request.method,
                    reason = request.reason,
                    scannedQr = request.scannedQr,
                    reviewedAt = request.reviewedAt,
                    coachSignature = request.coachSignature
                )

                when (outcome) {
                    is ReviewResult.Success -> {
                        call.respond(
                            HttpStatusCode.OK,
                            CoachReviewResponse(
                                sessionId = outcome.sessionId,
                                status = outcome.status,
                                integrity = outcome.integrity,
                                certificateJws = outcome.certificateJws,
                                verifyUrl = outcome.verifyUrl
                            )
                        )
                    }
                    is ReviewResult.Error -> {
                        call.respond(
                            HttpStatusCode.fromValue(outcome.statusCode),
                            mapOf("error" to outcome.error, "message" to outcome.message)
                        )
                    }
                }
            }
        }
    }
}
