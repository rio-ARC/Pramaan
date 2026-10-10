package app.pramaan.server

import app.pramaan.server.DashboardPages.renderAthletesPage
import app.pramaan.server.DashboardPages.renderCoachesPage
import app.pramaan.server.DashboardPages.renderDashboard
import app.pramaan.server.DashboardPages.renderSubmissionDetailPage
import app.pramaan.server.DashboardPages.renderSubmissionsPage
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.html.respondHtml
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import java.io.File

fun Route.configureAdminRoutes(
    lanIp: String = "127.0.0.1",
    uploadsDir: File
) {
    authenticate("admin") {
        // 1. Dashboard
        get("/") {
            val sessions = ServerDb.getAll()
            val athleteCount = AccountRepo.getAllAthletes().size
            val coachCount = AccountRepo.getAllCoaches().size
            call.respondHtml {
                renderDashboard(
                    sessions = sessions,
                    lanIp = lanIp,
                    athleteCount = athleteCount,
                    coachCount = coachCount
                )
            }
        }

        // 2. Submissions list
        get("/submissions") {
            val sessions = ServerDb.getAll()
            call.respondHtml {
                renderSubmissionsPage(sessions)
            }
        }

        // 3. Submission detail
        get("/submissions/{id}") {
            val id = call.parameters["id"] ?: ""
            val session = ServerDb.getById(id)
            if (session == null) {
                call.respondHtml(HttpStatusCode.NotFound) {
                    // Not found
                }
                return@get
            }
            val events = EventLog.getEvents(id)
            val athlete = AccountRepo.getAccount(session.athleteId)
            val reviewer = session.reviewedBy?.let { AccountRepo.getAccount(it) }

            call.respondHtml {
                renderSubmissionDetailPage(
                    session = session,
                    athlete = athlete,
                    reviewer = reviewer,
                    events = events
                )
            }
        }

        // 4. Athletes directory
        get("/athletes") {
            val athletes = AccountRepo.getAllAthletes()
            val sessions = ServerDb.getAll()
            val athleteSubmissionCounts = sessions.groupBy { it.athleteId }.mapValues { it.value.size }
            call.respondHtml {
                renderAthletesPage(athletes, athleteSubmissionCounts)
            }
        }

        // 5. Coaches list and add
        get("/coaches") {
            val coaches = AccountRepo.getAllCoaches()
            val newlyCreatedId = call.request.queryParameters["created"]
            call.respondHtml {
                renderCoachesPage(coaches, newlyCreatedId)
            }
        }

        post("/coaches") {
            val params = call.receiveParameters()
            val name = params["name"]?.trim() ?: ""
            val district = params["district"]?.trim()
            val pin = params["pin"]?.trim() ?: ""

            if (name.isNotBlank() && pin.length >= 4) {
                val newCoach = AccountRepo.createCoach(
                    name = name,
                    district = district,
                    pin = pin
                )
                call.respondRedirect("/coaches?created=${newCoach.id}")
            } else {
                call.respondRedirect("/coaches?error=invalid")
            }
        }
    }
}
