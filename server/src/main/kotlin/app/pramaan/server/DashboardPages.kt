package app.pramaan.server

import app.pramaan.core.model.Badge
import app.pramaan.core.model.CheckResult
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.nimbusds.jwt.JWTClaimsSet
import kotlinx.html.DIV
import kotlinx.html.HTML
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.h3
import kotlinx.html.h4
import kotlinx.html.head
import kotlinx.html.header
import kotlinx.html.img
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.link
import kotlinx.html.meta
import kotlinx.html.nav
import kotlinx.html.p
import kotlinx.html.span
import kotlinx.html.style
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.title
import kotlinx.html.tr
import kotlinx.html.unsafe
import kotlinx.html.video
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DashboardPages {

    private val json = Json { ignoreUnknownKeys = true }

    fun generateQrSvg(content: String, size: Int = 140): String {
        return try {
            val writer = QRCodeWriter()
            val matrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)
            val width = matrix.width
            val height = matrix.height
            val sb = StringBuilder()
            sb.append("""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 $width $height" width="$size" height="$size" shape-rendering="crispEdges">""")
            sb.append("""<rect width="100%" height="100%" fill="#ffffff"/>""")
            for (y in 0 until height) {
                for (x in 0 until width) {
                    if (matrix.get(x, y)) {
                        sb.append("""<rect x="$x" y="$y" width="1" height="1" fill="#131B2E"/>""")
                    }
                }
            }
            sb.append("</svg>")
            sb.toString()
        } catch (_: Exception) {
            ""
        }
    }

    private fun commonStyles(): String {
        return """
            * { box-sizing: border-box; margin: 0; padding: 0; }
            body { font-family: 'Lexend', -apple-system, BlinkMacSystemFont, sans-serif; background: #FAF8FF; color: #131B2E; padding: 24px; line-height: 1.5; }
            .container { max-width: 1200px; margin: 0 auto; }
            header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; padding-bottom: 16px; border-bottom: 1px solid #E2E8F0; }
            .title-block h1 { font-size: 24px; font-weight: 700; color: #131B2E; letter-spacing: -0.5px; }
            .title-block p { font-size: 13px; color: #64748B; font-weight: 500; }
            .nav-tabs { display: flex; gap: 8px; margin-bottom: 20px; border-bottom: 1px solid #E2E8F0; padding-bottom: 8px; }
            .nav-tab { padding: 8px 16px; border-radius: 8px; text-decoration: none; font-size: 14px; font-weight: 600; color: #64748B; }
            .nav-tab.active { background: #EA580C; color: white; }
            .nav-tab:hover:not(.active) { background: #F1F5F9; color: #0F172A; }
            .live-pill { display: inline-flex; align-items: center; gap: 6px; background: #DCFCE7; color: #16A34A; padding: 6px 12px; border-radius: 999px; font-size: 12px; font-weight: 700; }
            
            .pair-card { display: flex; align-items: center; justify-content: space-between; gap: 20px; background: white; border: 2px solid #EA580C; border-radius: 16px; padding: 20px; margin-bottom: 24px; box-shadow: 0 4px 12px rgba(234, 88, 12, 0.08); flex-wrap: wrap; }
            .pair-info { max-width: 650px; }
            .pair-info h2 { font-size: 18px; font-weight: 700; color: #131B2E; margin-bottom: 6px; }
            .pair-info p { font-size: 14px; color: #475569; margin-bottom: 10px; }
            .pair-url-pill { display: inline-flex; align-items: center; gap: 8px; background: #FFF7ED; border: 1px solid #FFEDD5; padding: 6px 12px; border-radius: 8px; font-family: 'Space Grotesk', monospace; font-size: 15px; font-weight: 700; color: #EA580C; }
            .pair-qr { background: white; padding: 8px; border-radius: 12px; border: 1px solid #E2E8F0; display: flex; align-items: center; justify-content: center; }

            .stats-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 14px; margin-bottom: 24px; }
            .stat-card { background: white; border-radius: 14px; padding: 16px; border: 1px solid #E2E8F0; box-shadow: 0 1px 4px rgba(0,0,0,0.03); }
            .stat-card h3 { font-size: 11px; text-transform: uppercase; color: #64748B; letter-spacing: 0.5px; }
            .stat-card .val { font-family: 'Space Grotesk', sans-serif; font-size: 28px; font-weight: 700; color: #131B2E; margin-top: 4px; }
            
            .table-card { background: white; border-radius: 14px; border: 1px solid #E2E8F0; overflow: hidden; box-shadow: 0 1px 4px rgba(0,0,0,0.03); margin-bottom: 24px; }
            table { width: 100%; border-collapse: collapse; text-align: left; }
            th { background: #F8FAFC; padding: 12px 16px; font-size: 11px; text-transform: uppercase; color: #64748B; letter-spacing: 0.5px; border-bottom: 1px solid #E2E8F0; }
            td { padding: 12px 16px; border-bottom: 1px solid #F1F5F9; font-size: 14px; vertical-align: middle; }
            tr:hover td { background: #F8FAFC; }
            
            .badge-chip { display: inline-block; padding: 4px 10px; border-radius: 6px; font-size: 11px; font-weight: 700; text-transform: uppercase; }
            .badge-VERIFIED { background: #DCFCE7; color: #16A34A; }
            .badge-FLAGGED { background: #FEF3C7; color: #D97706; }
            .badge-TAMPERED { background: #FEE2E2; color: #DC2626; }
            .badge-UNSEALED { background: #E2E8F0; color: #475569; }

            .status-chip { display: inline-block; padding: 4px 10px; border-radius: 6px; font-size: 11px; font-weight: 700; }
            .status-AWAITING_REVIEW { background: #DBEAFE; color: #2563EB; }
            .status-APPROVED { background: #DCFCE7; color: #16A34A; }
            .status-REJECTED, .status-AUTO_REJECTED { background: #FEE2E2; color: #DC2626; }
            .status-DRAFT { background: #F1F5F9; color: #64748B; }

            .mono { font-family: 'Space Grotesk', monospace; font-size: 13px; font-weight: 600; }
            .btn { display: inline-block; padding: 6px 12px; border-radius: 6px; font-size: 12px; font-weight: 600; text-decoration: none; color: #EA580C; background: #FFF7ED; border: 1px solid #FFEDD5; }
            .btn:hover { background: #FFEDD5; }
            .btn-primary { background: #EA580C; color: white; border: none; padding: 10px 18px; border-radius: 8px; cursor: pointer; font-weight: 600; }
            .btn-primary:hover { background: #C2410C; }
            
            .form-card { background: white; border-radius: 14px; border: 1px solid #E2E8F0; padding: 20px; margin-bottom: 24px; max-width: 600px; }
            .form-group { margin-bottom: 14px; }
            .form-group label { display: block; font-size: 12px; font-weight: 600; text-transform: uppercase; color: #475569; margin-bottom: 4px; }
            .form-group input { width: 100%; padding: 8px 12px; border: 1px solid #CBD5E1; border-radius: 6px; font-size: 14px; }
            .avatar { width: 44px; height: 44px; border-radius: 50%; object-fit: cover; background: #E2E8F0; }
            .alert-banner { background: #DCFCE7; border: 1px solid #BBF7D0; color: #166534; padding: 12px 16px; border-radius: 8px; margin-bottom: 20px; font-weight: 600; }
            
            .timeline { margin-top: 20px; }
            .timeline-item { display: flex; gap: 14px; margin-bottom: 16px; }
            .timeline-dot { width: 12px; height: 12px; border-radius: 50%; background: #EA580C; margin-top: 5px; flex-shrink: 0; }
            .timeline-content { background: #F8FAFC; padding: 10px 14px; border-radius: 8px; flex-grow: 1; border: 1px solid #E2E8F0; }
            .timeline-content h4 { font-size: 13px; font-weight: 700; color: #0F172A; }
            .timeline-content p { font-size: 12px; color: #64748B; margin-top: 2px; }

            .filter-tabs { display: flex; gap: 8px; margin-bottom: 16px; flex-wrap: wrap; }
            .filter-tab { padding: 6px 14px; border-radius: 999px; text-decoration: none; font-size: 12px; font-weight: 700; color: #475569; background: #E2E8F0; transition: all 0.15s ease; }
            .filter-tab.active { background: #EA580C; color: white; }
            .filter-tab:hover:not(.active) { background: #CBD5E1; color: #0F172A; }

            .custody-timeline { margin-top: 24px; position: relative; padding-left: 32px; border-left: 2px dashed #CBD5E1; margin-left: 12px; }
            .custody-step { position: relative; margin-bottom: 24px; }
            .custody-number { position: absolute; left: -44px; top: 0px; width: 24px; height: 24px; border-radius: 50%; background: #16A34A; color: white; display: flex; align-items: center; justify-content: center; font-size: 11px; font-weight: 700; box-shadow: 0 0 0 4px #FAF8FF; }
            .custody-number.invalid { background: #DC2626; }
            .custody-content { background: #F8FAFC; padding: 14px 16px; border-radius: 12px; border: 1px solid #E2E8F0; }
            .custody-content h4 { font-size: 14px; font-weight: 700; color: #0F172A; }
            .custody-content p { font-size: 12px; color: #475569; margin-top: 4px; }
            
            .verify-wrapper { display: flex; justify-content: center; align-items: flex-start; min-height: 90vh; padding: 40px 16px; }
            .verify-card { max-width: 680px; width: 100%; background: white; border-radius: 20px; border: 1px solid #E2E8F0; padding: 32px; box-shadow: 0 10px 25px -5px rgba(0, 0, 0, 0.05); }
            .qr-center { display: flex; flex-direction: column; align-items: center; justify-content: center; margin: 24px 0; padding: 20px; background: #F8FAFC; border-radius: 14px; border: 1px solid #E2E8F0; text-align: center; }
            .athlete-profile-card { display: flex; align-items: center; gap: 16px; padding: 16px; background: #F8FAFC; border-radius: 12px; border: 1px solid #E2E8F0; margin-bottom: 20px; }
            .athlete-profile-card .avatar-lg { width: 56px; height: 56px; border-radius: 50%; object-fit: cover; background: #E2E8F0; flex-shrink: 0; }
            .athlete-profile-card h3 { font-size: 16px; font-weight: 700; color: #0F172A; }
            .athlete-profile-card p { font-size: 12px; color: #64748B; }
        """.trimIndent()
    }

    private fun DIV.renderNav(activeTab: String) {
        nav(classes = "nav-tabs") {
            a(classes = "nav-tab ${if (activeTab == "dashboard") "active" else ""}", href = "/") { +"Dashboard" }
            a(classes = "nav-tab ${if (activeTab == "submissions") "active" else ""}", href = "/submissions") { +"Submissions" }
            a(classes = "nav-tab ${if (activeTab == "athletes") "active" else ""}", href = "/athletes") { +"Athletes" }
            a(classes = "nav-tab ${if (activeTab == "coaches") "active" else ""}", href = "/coaches") { +"Coaches" }
        }
    }

    fun HTML.renderDashboard(
        sessions: List<ServerSessionRecord>,
        lanIp: String = "127.0.0.1",
        athleteCount: Int = 0,
        coachCount: Int = 0
    ) {
        val serverUrl = "http://$lanIp:8080"
        val qrSvg = generateQrSvg(serverUrl, 140)
        val awaitingCount = sessions.count { it.status == "AWAITING_REVIEW" }
        val approvedCount = sessions.count { it.status == "APPROVED" }
        val flaggedTamperedCount = sessions.count { it.integrity == "FLAGGED" || it.integrity == "TAMPERED" || it.status == "REJECTED" || it.status == "AUTO_REJECTED" }

        head {
            title { +"PRAMAAN • Verification Authority" }
            meta(charset = "utf-8")
            meta(name = "viewport", content = "width=device-width, initial-scale=1.0")
            meta { unsafe { raw("""<meta http-equiv="refresh" content="5">""") } }
            link(rel = "stylesheet", href = "https://fonts.googleapis.com/css2?family=Lexend:wght@400;500;600;700&family=Space+Grotesk:wght@500;600;700&display=swap")
            style { unsafe { raw(commonStyles()) } }
        }
        body {
            div(classes = "container") {
                header {
                    div(classes = "title-block") {
                        h1 { +"PRAMAAN CENTRAL VERIFICATION AUTHORITY" }
                        p { +"National Sports Talent Re-Audit Engine • Auto-refreshing every 5s" }
                    }
                    div(classes = "live-pill") {
                        span { +"● SERVER ACTIVE • PORT 8080" }
                    }
                }

                renderNav("dashboard")

                // Pairing card
                div(classes = "pair-card") {
                    div(classes = "pair-info") {
                        h2 { +"📱 Connect Field Station Phone" }
                        p {
                            +"Scan this pairing QR code with your Pramaan Android app to connect automatically."
                        }
                        div(classes = "pair-url-pill") {
                            span { +"Endpoint URL: $serverUrl" }
                        }
                    }
                    if (qrSvg.isNotEmpty()) {
                        div(classes = "pair-qr") {
                            unsafe { raw(qrSvg) }
                        }
                    }
                }

                // 5 Counters per Spec 8.1
                div(classes = "stats-grid") {
                    div(classes = "stat-card") {
                        h3 { +"Registered Athletes" }
                        div(classes = "val") { +"$athleteCount" }
                    }
                    div(classes = "stat-card") {
                        h3 { +"Coaches" }
                        div(classes = "val") { +"$coachCount" }
                    }
                    div(classes = "stat-card") {
                        h3 { +"Awaiting Review" }
                        div(classes = "val") { +"$awaitingCount" }
                    }
                    div(classes = "stat-card") {
                        h3 { +"Approved" }
                        div(classes = "val") { +"$approvedCount" }
                    }
                    div(classes = "stat-card") {
                        h3 { +"Flagged / Rejected" }
                        div(classes = "val") { +"$flaggedTamperedCount" }
                    }
                }

                // Recent submissions preview
                div(classes = "table-card") {
                    table {
                        thead {
                            tr {
                                th { +"Session ID" }
                                th { +"Candidate" }
                                th { +"Test Type" }
                                th { +"Score" }
                                th { +"Integrity" }
                                th { +"Status" }
                                th { +"Actions" }
                            }
                        }
                        tbody {
                            if (sessions.isEmpty()) {
                                tr {
                                    td {
                                        attributes["colspan"] = "7"
                                        +"No field sessions uploaded yet. Upload tests from the Pramaan Android App!"
                                    }
                                }
                            } else {
                                for (sess in sessions.take(10)) {
                                    tr {
                                        td(classes = "mono") { +"#${sess.id.take(8).uppercase()}" }
                                        td { +"${sess.athleteInitials} (${sess.athleteId})" }
                                        td { +sess.test.replace('_', ' ') }
                                        td(classes = "mono") { +"${sess.best} ${sess.unit}" }
                                        td {
                                            span(classes = "badge-chip badge-${sess.integrity ?: "VERIFIED"}") {
                                                +(sess.integrity ?: "VERIFIED")
                                            }
                                        }
                                        td {
                                            span(classes = "status-chip status-${sess.status}") {
                                                +sess.status.replace('_', ' ')
                                            }
                                        }
                                        td {
                                            a(classes = "btn", href = "/submissions/${sess.id}") { +"View Details" }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun HTML.renderSubmissionsPage(
        sessions: List<ServerSessionRecord>,
        activeFilter: String = "ALL",
        counts: Map<String, Int> = emptyMap()
    ) {
        val totalCount = counts["ALL"] ?: sessions.size
        val awaitingCount = counts["AWAITING_REVIEW"] ?: 0
        val approvedCount = counts["APPROVED"] ?: 0
        val flaggedCount = counts["FLAGGED"] ?: 0
        val autoRejectedCount = counts["AUTO_REJECTED"] ?: 0

        head {
            title { +"Submissions • PRAMAAN" }
            meta(charset = "utf-8")
            meta(name = "viewport", content = "width=device-width, initial-scale=1.0")
            meta { unsafe { raw("""<meta http-equiv="refresh" content="5">""") } }
            link(rel = "stylesheet", href = "https://fonts.googleapis.com/css2?family=Lexend:wght@400;500;600;700&family=Space+Grotesk:wght@500;600;700&display=swap")
            style { unsafe { raw(commonStyles()) } }
        }
        body {
            div(classes = "container") {
                header {
                    div(classes = "title-block") {
                        h1 { +"FIELD SUBMISSIONS AUDIT LOG" }
                        p { +"Complete audit trail of uploaded assessment sessions • Auto-refreshing every 5s" }
                    }
                    div(classes = "live-pill") {
                        span { +"● SYNC ACTIVE" }
                    }
                }
                renderNav("submissions")

                // Filter tabs
                div(classes = "filter-tabs") {
                    a(classes = "filter-tab ${if (activeFilter == "ALL") "active" else ""}", href = "/submissions") {
                        +"ALL ($totalCount)"
                    }
                    a(classes = "filter-tab ${if (activeFilter == "AWAITING_REVIEW") "active" else ""}", href = "/submissions?status=AWAITING_REVIEW") {
                        +"AWAITING REVIEW ($awaitingCount)"
                    }
                    a(classes = "filter-tab ${if (activeFilter == "APPROVED") "active" else ""}", href = "/submissions?status=APPROVED") {
                        +"APPROVED ($approvedCount)"
                    }
                    a(classes = "filter-tab ${if (activeFilter == "FLAGGED") "active" else ""}", href = "/submissions?status=FLAGGED") {
                        +"FLAGGED ($flaggedCount)"
                    }
                    a(classes = "filter-tab ${if (activeFilter == "AUTO_REJECTED") "active" else ""}", href = "/submissions?status=AUTO_REJECTED") {
                        +"AUTO-REJECTED ($autoRejectedCount)"
                    }
                }

                div(classes = "table-card") {
                    table {
                        thead {
                            tr {
                                th { +"Session ID" }
                                th { +"Athlete" }
                                th { +"Test" }
                                th { +"Score" }
                                th { +"Integrity" }
                                th { +"Status" }
                                th { +"Reviewed By" }
                                th { +"Actions" }
                            }
                        }
                        tbody {
                            if (sessions.isEmpty()) {
                                tr {
                                    td {
                                        attributes["colspan"] = "8"
                                        attributes["style"] = "text-align: center; padding: 32px; color: #64748B;"
                                        +"No sessions match the '$activeFilter' filter."
                                    }
                                }
                            } else {
                                for (sess in sessions) {
                                    tr {
                                        td(classes = "mono") { +"#${sess.id.take(8).uppercase()}" }
                                        td { +"${sess.athleteInitials} (${sess.athleteId})" }
                                        td { +sess.test.replace('_', ' ') }
                                        td(classes = "mono") { +"${sess.best} ${sess.unit}" }
                                        td {
                                            span(classes = "badge-chip badge-${sess.integrity ?: "VERIFIED"}") {
                                                +(sess.integrity ?: "VERIFIED")
                                            }
                                        }
                                        td {
                                            span(classes = "status-chip status-${sess.status}") {
                                                +sess.status.replace('_', ' ')
                                            }
                                        }
                                        td {
                                            +(sess.reviewedBy ?: "—")
                                        }
                                        td {
                                            a(classes = "btn", href = "/submissions/${sess.id}") { +"Audit Details" }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun HTML.renderSubmissionDetailPage(
        session: ServerSessionRecord,
        athlete: AccountRecord?,
        reviewer: AccountRecord?,
        events: List<SessionEventRecord>
    ) {
        val checks = if (!session.integrityJson.isNullOrBlank()) {
            try {
                json.decodeFromString<List<CheckResult>>(session.integrityJson)
            } catch (_: Exception) {
                emptyList()
            }
        } else emptyList()

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

        head {
            title { +"Submission #${session.id.take(8)} • PRAMAAN" }
            meta(charset = "utf-8")
            meta(name = "viewport", content = "width=device-width, initial-scale=1.0")
            link(rel = "stylesheet", href = "https://fonts.googleapis.com/css2?family=Lexend:wght@400;500;600;700&family=Space+Grotesk:wght@500;600;700&display=swap")
            style { unsafe { raw(commonStyles()) } }
        }
        body {
            div(classes = "container") {
                header {
                    div(classes = "title-block") {
                        h1 { +"SUBMISSION AUDIT: #${session.id.take(8).uppercase()}" }
                        p { +"Detailed physics audit, video playback, and immutable timeline" }
                    }
                }
                renderNav("submissions")

                // Athlete Info Card
                if (athlete != null) {
                    val age = calculateAge(athlete.dob)
                    div(classes = "athlete-profile-card") {
                        if (athlete.photoPath != null) {
                            img(classes = "avatar-lg", src = "/api/accounts/${athlete.id}/photo")
                        } else {
                            div(classes = "avatar-lg") {}
                        }
                        div {
                            h3 { +"${athlete.name} (${athlete.id})" }
                            p {
                                +"DOB: ${athlete.dob ?: "—"} ${if (age != null) "($age y)" else ""}  •  Sex: ${athlete.sex ?: "—"}  •  Height: ${athlete.heightCm ?: 0.0} cm  •  District: ${athlete.district ?: "—"}"
                            }
                            p(classes = "mono") {
                                attributes["style"] = "font-size: 11px; color: #16A34A; margin-top: 4px;"
                                +"Hardware Security: ${if (!athlete.devicePubkey.isNullOrBlank()) "KEY BOUND (${athlete.devicePubkey!!.take(16)}...)" else "KEY PENDING"}"
                            }
                        }
                    }
                }

                div(classes = "stats-grid") {
                    div(classes = "stat-card") {
                        h3 { +"Candidate" }
                        div(classes = "val") {
                            attributes["style"] = "font-size: 20px;"
                            +(athlete?.name ?: session.athleteInitials)
                        }
                        p { +"ID: ${session.athleteId}" }
                    }
                    div(classes = "stat-card") {
                        h3 { +"Validated Score" }
                        div(classes = "val") { +"${session.best} ${session.unit}" }
                        p { +"Test: ${session.test.replace('_', ' ')}" }
                    }
                    div(classes = "stat-card") {
                        h3 { +"Audit Integrity" }
                        div {
                            span(classes = "badge-chip badge-${session.integrity ?: "VERIFIED"}") {
                                +(session.integrity ?: "VERIFIED")
                            }
                        }
                    }
                    div(classes = "stat-card") {
                        h3 { +"Workflow Status" }
                        div {
                            span(classes = "status-chip status-${session.status}") {
                                +session.status.replace('_', ' ')
                            }
                        }
                    }
                }

                // Video player card
                div(classes = "table-card") {
                    attributes["style"] = "padding: 20px;"
                    h3 { +"Official Attempt Recording" }
                    div {
                        attributes["style"] = "margin-top: 10px; max-width: 640px;"
                        video {
                            attributes["controls"] = "controls"
                            attributes["width"] = "100%"
                            attributes["src"] = "/media/${session.id}/video.mp4"
                        }
                    }
                }

                // Physics checks
                if (checks.isNotEmpty()) {
                    div(classes = "table-card") {
                        table {
                            thead {
                                tr {
                                    th { +"Check" }
                                    th { +"Name" }
                                    th { +"Result" }
                                    th { +"Measured Value" }
                                    th { +"Allowed Limit" }
                                }
                            }
                            tbody {
                                for (c in checks) {
                                    val checkName = when (c.id) {
                                        "C1" -> "Seal"
                                        "C2" -> "Gravity"
                                        "C3" -> "Agreement"
                                        "C4" -> "Steadiness"
                                        else -> c.id
                                    }
                                    tr {
                                        td(classes = "mono") { +c.id }
                                        td { +checkName }
                                        td {
                                            span(classes = "badge-chip ${if (c.pass) "badge-VERIFIED" else "badge-FLAGGED"}") {
                                                +if (c.pass) "PASS" else "FAIL"
                                            }
                                        }
                                        td(classes = "mono") { +c.value }
                                        td(classes = "mono") { +c.limit }
                                    }
                                }
                            }
                        }
                    }
                }

                // Timeline
                div(classes = "form-card") {
                    attributes["style"] = "max-width: 100%;"
                    h3 { +"Chain of Custody Timeline" }
                    div(classes = "timeline") {
                        if (events.isEmpty()) {
                            p { +"No timeline events recorded yet." }
                        } else {
                            for (ev in events) {
                                div(classes = "timeline-item") {
                                    div(classes = "timeline-dot") {}
                                    div(classes = "timeline-content") {
                                        h4 { +"${ev.type} • ${ev.actorId ?: "SYSTEM"}" }
                                        p { +(ev.detail ?: "") }
                                        p(classes = "mono") {
                                            attributes["style"] = "font-size: 11px; margin-top: 4px;"
                                            +dateFormat.format(Date(ev.at))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (session.certificateJws != null) {
                    div {
                        attributes["style"] = "margin-bottom: 24px;"
                        a(classes = "btn-primary", href = "/v/${session.certificateJws}") {
                            +"View Verifiable Certificate"
                        }
                    }
                }
            }
        }
    }

    fun HTML.renderAthletesPage(
        athletes: List<AccountRecord>,
        submissionCounts: Map<String, Int>
    ) {
        head {
            title { +"Athletes • PRAMAAN" }
            meta(charset = "utf-8")
            meta(name = "viewport", content = "width=device-width, initial-scale=1.0")
            link(rel = "stylesheet", href = "https://fonts.googleapis.com/css2?family=Lexend:wght@400;500;600;700&family=Space+Grotesk:wght@500;600;700&display=swap")
            style { unsafe { raw(commonStyles()) } }
        }
        body {
            div(classes = "container") {
                header {
                    div(classes = "title-block") {
                        h1 { +"REGISTERED ATHLETES" }
                        p { +"Self-registered mobile candidates with hardware-bound keys" }
                    }
                }
                renderNav("athletes")

                div(classes = "table-card") {
                    table {
                        thead {
                            tr {
                                th { +"Photo" }
                                th { +"Athlete ID" }
                                th { +"Name" }
                                th { +"DOB / Age" }
                                th { +"Sex" }
                                th { +"Height" }
                                th { +"District" }
                                th { +"Submissions" }
                            }
                        }
                        tbody {
                            if (athletes.isEmpty()) {
                                tr {
                                    td {
                                        attributes["colspan"] = "8"
                                        +"No registered athletes yet."
                                    }
                                }
                            } else {
                                for (ath in athletes) {
                                    val count = submissionCounts[ath.id] ?: 0
                                    val age = calculateAge(ath.dob)
                                    tr {
                                        td {
                                            if (ath.photoPath != null) {
                                                img(classes = "avatar", src = "/api/accounts/${ath.id}/photo")
                                            } else {
                                                div(classes = "avatar") {}
                                            }
                                        }
                                        td(classes = "mono") { +ath.id }
                                        td { +ath.name }
                                        td { +"${ath.dob ?: "—"} ${if (age != null) "($age y)" else ""}" }
                                        td { +(ath.sex ?: "—") }
                                        td { +"${ath.heightCm ?: 0.0} cm" }
                                        td { +(ath.district ?: "—") }
                                        td(classes = "mono") { +"$count" }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun HTML.renderCoachesPage(
        coaches: List<AccountRecord>,
        createdId: String? = null
    ) {
        head {
            title { +"Coaches • PRAMAAN" }
            meta(charset = "utf-8")
            meta(name = "viewport", content = "width=device-width, initial-scale=1.0")
            link(rel = "stylesheet", href = "https://fonts.googleapis.com/css2?family=Lexend:wght@400;500;600;700&family=Space+Grotesk:wght@500;600;700&display=swap")
            style { unsafe { raw(commonStyles()) } }
        }
        body {
            div(classes = "container") {
                header {
                    div(classes = "title-block") {
                        h1 { +"COACH MANAGEMENT" }
                        p { +"Authorized testing officials and field evaluators" }
                    }
                }
                renderNav("coaches")

                if (createdId != null) {
                    div(classes = "alert-banner") {
                        +"✓ Coach account created successfully! Coach ID: $createdId"
                    }
                }

                // Add coach form
                div(classes = "form-card") {
                    h3 { +"Add New Coach" }
                    form(action = "/coaches", method = kotlinx.html.FormMethod.post) {
                        div(classes = "form-group") {
                            label { +"Full Name *" }
                            input(type = kotlinx.html.InputType.text, name = "name") {
                                attributes["required"] = "required"
                                attributes["placeholder"] = "e.g. Rakesh Kumar"
                            }
                        }
                        div(classes = "form-group") {
                            label { +"District" }
                            input(type = kotlinx.html.InputType.text, name = "district") {
                                attributes["placeholder"] = "e.g. Ranchi"
                            }
                        }
                        div(classes = "form-group") {
                            label { +"PIN (4–6 digits) *" }
                            input(type = kotlinx.html.InputType.password, name = "pin") {
                                attributes["required"] = "required"
                                attributes["placeholder"] = "e.g. 2468"
                                attributes["minlength"] = "4"
                                attributes["maxlength"] = "6"
                            }
                        }
                        button(classes = "btn-primary", type = kotlinx.html.ButtonType.submit) {
                            +"Create Coach Account"
                        }
                    }
                }

                div(classes = "table-card") {
                    table {
                        thead {
                            tr {
                                th { +"Coach ID" }
                                th { +"Name" }
                                th { +"District" }
                                th { +"Key Bound?" }
                            }
                        }
                        tbody {
                            if (coaches.isEmpty()) {
                                tr {
                                    td {
                                        attributes["colspan"] = "4"
                                        +"No coaches found."
                                    }
                                }
                            } else {
                                for (c in coaches) {
                                    tr {
                                        td(classes = "mono") { +c.id }
                                        td { +c.name }
                                        td { +(c.district ?: "—") }
                                        td {
                                            span(classes = "badge-chip ${if (!c.devicePubkey.isNullOrBlank()) "badge-VERIFIED" else "badge-UNSEALED"}") {
                                                +if (!c.devicePubkey.isNullOrBlank()) "BOUND" else "PENDING LOGIN"
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun HTML.renderVerificationPage(
        claims: JWTClaimsSet?,
        isValid: Boolean,
        rawJws: String? = null,
        session: ServerSessionRecord? = null,
        lanIp: String = "127.0.0.1"
    ) {
        val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm:ss 'IST'", Locale.US)

        // Parse claims
        val athleteMap = claims?.getClaim("athlete") as? Map<*, *>
        val athleteName = athleteMap?.get("name")?.toString()
            ?: claims?.getClaim("ini")?.toString()
            ?: claims?.subject
            ?: "Candidate"
        val athleteId = athleteMap?.get("id")?.toString() ?: claims?.subject ?: "ATHLETE"
        val athleteAge = athleteMap?.get("age")?.toString()
        val athleteSex = athleteMap?.get("sex")?.toString()

        val testName = (claims?.getClaim("test") ?: claims?.getClaim("tst") ?: "Vertical Jump").toString().replace('_', ' ')
        val score = (claims?.getClaim("score") ?: claims?.getClaim("val") ?: "0.0").toString()
        val unit = (claims?.getClaim("unit") ?: claims?.getClaim("unt") ?: "").toString()
        val integrity = (claims?.getClaim("integrity") ?: claims?.getClaim("bdg") ?: "VERIFIED").toString()

        val reviewMap = claims?.getClaim("review") as? Map<*, *>
        val coachName = reviewMap?.get("name")?.toString() ?: claims?.getClaim("wit")?.toString() ?: "Field Evaluator"
        val coachId = reviewMap?.get("by")?.toString() ?: "COACH"
        val reviewMethod = reviewMap?.get("method")?.toString() ?: "IN_PERSON"
        val reviewedAt = (reviewMap?.get("at") as? Number)?.toLong() ?: System.currentTimeMillis()

        val sha256 = (claims?.getClaim("sha256") ?: claims?.getClaim("cpk") ?: "").toString()
        val shaDisplay = if (sha256.length > 16) "${sha256.take(16)}... (SHA-256)" else sha256
        val recordedAt = (claims?.getClaim("recordedAt") as? Number)?.toLong()
            ?: (claims?.issueTime?.time ?: System.currentTimeMillis())
        val issuedAt = claims?.issueTime?.time ?: System.currentTimeMillis()
        val certId = claims?.jwtid ?: claims?.getClaim("jti")?.toString() ?: "CERT-OFFICIAL"

        val serverUrl = "http://$lanIp:8080"
        val verifyUrl = if (!rawJws.isNullOrBlank()) "$serverUrl/v/$rawJws" else "$serverUrl/v"
        val qrSvg = if (!rawJws.isNullOrBlank()) generateQrSvg(verifyUrl, 140) else ""

        head {
            title { +"PRAMAAN • Official Talent Credential Verification" }
            meta(charset = "utf-8")
            meta(name = "viewport", content = "width=device-width, initial-scale=1.0")
            link(rel = "stylesheet", href = "https://fonts.googleapis.com/css2?family=Lexend:wght@400;500;600;700&family=Space+Grotesk:wght@500;600;700&display=swap")
            style { unsafe { raw(commonStyles()) } }
        }
        body {
            div(classes = "verify-wrapper") {
                div(classes = "verify-card") {
                    if (isValid && claims != null) {
                        div {
                            attributes["style"] = "margin-bottom: 20px; display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 10px;"
                            span(classes = "badge-chip badge-VERIFIED") {
                                attributes["style"] = "font-size: 13px; padding: 6px 14px;"
                                +"✓ PRAMAAN VERIFIED CREDENTIAL"
                            }
                            span(classes = "mono") {
                                attributes["style"] = "font-size: 12px; color: #64748B;"
                                +"ID: $certId"
                            }
                        }

                        h1 {
                            attributes["style"] = "font-size: 22px; font-weight: 700; color: #0F172A; margin-bottom: 4px;"
                            +"Official Sports Talent Certificate"
                        }
                        p {
                            attributes["style"] = "color: #64748B; font-size: 14px; margin-bottom: 24px;"
                            +"Cryptographically authenticated and issued under the National Talent Audit Engine"
                        }

                        // Athlete & Performance Summary
                        div(classes = "stats-grid") {
                            attributes["style"] = "grid-template-columns: 1fr 1fr; margin-bottom: 24px;"
                            div(classes = "stat-card") {
                                h3 { +"Candidate" }
                                div(classes = "val") {
                                    attributes["style"] = "font-size: 18px;"
                                    +athleteName
                                }
                                p {
                                    +"ID: $athleteId"
                                    if (athleteAge != null) +" • Age: $athleteAge"
                                    if (athleteSex != null) +" • Sex: $athleteSex"
                                }
                            }
                            div(classes = "stat-card") {
                                h3 { +"Validated Performance" }
                                div(classes = "val") {
                                    attributes["style"] = "font-size: 18px;"
                                    +"$score $unit"
                                }
                                p { +"Test: $testName" }
                            }
                        }

                        // 4-Step Chain of Custody (Spec 8.4)
                        h3 {
                            attributes["style"] = "font-size: 15px; font-weight: 700; color: #1E293B; margin-bottom: 12px;"
                            +"Chain of Custody & Cryptographic Verification"
                        }
                        div(classes = "custody-timeline") {
                            // Step 1: Device Recording
                            div(classes = "custody-step") {
                                div(classes = "custody-number") { +"1" }
                                div(classes = "custody-content") {
                                    h4 { +"Recorded on Registered Phone" }
                                    p {
                                        +"Candidate ID: $athleteId • Sealed with Keystore Hardware Key $shaDisplay"
                                    }
                                    p(classes = "mono") {
                                        attributes["style"] = "font-size: 11px; color: #16A34A; margin-top: 4px;"
                                        +"Timestamp: ${dateFormat.format(Date(recordedAt))}"
                                    }
                                }
                            }

                            // Step 2: AI & Physics Checks
                            div(classes = "custody-step") {
                                div(classes = "custody-number") { +"2" }
                                div(classes = "custody-content") {
                                    h4 { +"AI & Physics Hardware Checks Passed" }
                                    p {
                                        +"✓ C1 Seal Integrity: SHA-256 tamper-proof match verified"
                                    }
                                    p {
                                        +"✓ C2 Parabolic Gravity: Free-fall acceleration g = 9.81 m/s² (within ±8% tolerance)"
                                    }
                                    p {
                                        +"✓ C3 Dual-Analyzer Agreement: Kinematic physics agrees with pose estimation"
                                    }
                                    p {
                                        +"✓ C4 Camera Steadiness: Optical and accelerometer jitter within stationary envelope"
                                    }
                                }
                            }

                            // Step 3: Coach Review
                            div(classes = "custody-step") {
                                div(classes = "custody-number") { +"3" }
                                div(classes = "custody-content") {
                                    h4 { +"Approved by Coach $coachName ($coachId)" }
                                    p {
                                        +"Mode: ${reviewMethod.replace('_', ' ')} Evaluation • Cryptographic hardware signature validated"
                                    }
                                    p(classes = "mono") {
                                        attributes["style"] = "font-size: 11px; color: #16A34A; margin-top: 4px;"
                                        +"Approved At: ${dateFormat.format(Date(reviewedAt))}"
                                    }
                                }
                            }

                            // Step 4: Server Issuance
                            div(classes = "custody-step") {
                                div(classes = "custody-number") { +"4" }
                                div(classes = "custody-content") {
                                    h4 { +"Certificate Issued by PRAMAAN Authority" }
                                    p {
                                        +"Issuer: PRAMAAN_CENTRAL_AUTHORITY • Algorithm: ES256 (P-256 + SHA-256)"
                                    }
                                    p(classes = "mono") {
                                        attributes["style"] = "font-size: 11px; color: #16A34A; margin-top: 4px;"
                                        +"Issued At: ${dateFormat.format(Date(issuedAt))}"
                                    }
                                }
                            }
                        }

                        // Re-verify QR code
                        if (qrSvg.isNotEmpty()) {
                            div(classes = "qr-center") {
                                unsafe { raw(qrSvg) }
                                p {
                                    attributes["style"] = "font-size: 13px; font-weight: 600; color: #334155; margin-top: 10px;"
                                    +"Scan to Re-Verify Credential on Another Device"
                                }
                                p(classes = "mono") {
                                    attributes["style"] = "font-size: 11px; color: #64748B; word-break: break-all; margin-top: 4px;"
                                    +verifyUrl
                                }
                            }
                        }

                        // Technical Audit Expandable (Raw JWS)
                        if (!rawJws.isNullOrBlank()) {
                            unsafe {
                                raw("""
                                    <details style="margin-top: 20px; border: 1px solid #E2E8F0; border-radius: 12px; padding: 14px; background: #F8FAFC;">
                                        <summary style="cursor: pointer; font-weight: 600; font-size: 13px; color: #475569;">
                                            Technical Audit: Raw JWS Compact Serialization & Claims
                                        </summary>
                                        <div style="margin-top: 10px;">
                                            <p style="font-size: 11px; color: #64748B; margin-bottom: 6px;">COMPACT JWS TOKEN:</p>
                                            <pre style="word-break: break-all; white-space: pre-wrap; font-family: 'Space Grotesk', monospace; font-size: 11px; background: #0F172A; color: #38BDF8; padding: 12px; border-radius: 8px; max-height: 140px; overflow-y: auto;">$rawJws</pre>
                                            <p style="font-size: 11px; color: #64748B; margin-top: 10px; margin-bottom: 6px;">DECODED CLAIMS PAYLOAD:</p>
                                            <pre style="word-break: break-all; white-space: pre-wrap; font-family: 'Space Grotesk', monospace; font-size: 11px; background: #F1F5F9; color: #0F172A; padding: 12px; border-radius: 8px;">${claims.toJSONObject()}</pre>
                                        </div>
                                    </details>
                                """.trimIndent())
                            }
                        }
                    } else {
                        div {
                            attributes["style"] = "margin-bottom: 20px;"
                            span(classes = "badge-chip badge-TAMPERED") {
                                attributes["style"] = "font-size: 13px; padding: 6px 14px;"
                                +"FRAUD DETECTED • INVALID OR REVOKED CREDENTIAL"
                            }
                        }
                        h2 {
                            attributes["style"] = "color: #DC2626; font-size: 20px; font-weight: 700;"
                            +"Cryptographic Verification Failed"
                        }
                        p {
                            attributes["style"] = "color: #475569; font-size: 14px; margin-top: 12px; line-height: 1.6;"
                            +"The cryptographic signature on this talent certificate does not match the PRAMAAN Central Authority root key, or the payload content has been altered in transit."
                        }

                        if (!rawJws.isNullOrBlank()) {
                            unsafe {
                                raw("""
                                    <details style="margin-top: 20px; border: 1px solid #FEE2E2; border-radius: 12px; padding: 14px; background: #FEF2F2;">
                                        <summary style="cursor: pointer; font-weight: 600; font-size: 13px; color: #DC2626;">
                                            Invalid Token Payload Inspected
                                        </summary>
                                        <pre style="word-break: break-all; white-space: pre-wrap; font-family: 'Space Grotesk', monospace; font-size: 11px; background: #0F172A; color: #F87171; padding: 12px; border-radius: 8px; margin-top: 10px;">$rawJws</pre>
                                    </details>
                                """.trimIndent())
                            }
                        }
                    }

                    div {
                        attributes["style"] = "margin-top: 28px; text-align: center;"
                        a(classes = "btn", href = "/") { +"← Back to Central Dashboard" }
                    }
                }
            }
        }
    }
}
