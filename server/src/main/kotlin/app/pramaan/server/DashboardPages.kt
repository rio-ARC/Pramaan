package app.pramaan.server

import app.pramaan.core.model.Badge
import com.nimbusds.jwt.JWTClaimsSet
import kotlinx.html.HTML
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.h3
import kotlinx.html.head
import kotlinx.html.header
import kotlinx.html.link
import kotlinx.html.meta
import kotlinx.html.p
import kotlinx.html.script
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

object DashboardPages {

    fun HTML.renderDashboard(sessions: List<ServerSessionRecord>) {
        head {
            title { +"Pramaan • Sports Talent Verification Authority" }
            meta(charset = "utf-8")
            meta(name = "viewport", content = "width=device-width, initial-scale=1.0")
            meta {
                unsafe { raw("""<meta http-equiv="refresh" content="5">""") }
            }
            link(rel = "preconnect", href = "https://fonts.googleapis.com")
            link(rel = "stylesheet", href = "https://fonts.googleapis.com/css2?family=Lexend:wght@400;500;600;700&family=Space+Grotesk:wght@500;600;700&display=swap")
            style {
                unsafe {
                    raw(
                        """
                        * { box-sizing: border-box; margin: 0; padding: 0; }
                        body { font-family: 'Lexend', sans-serif; background: #FAF8FF; color: #131B2E; padding: 24px; line-height: 1.5; }
                        .container { max-width: 1200px; margin: 0 auto; }
                        header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; padding-bottom: 16px; border-bottom: 1px solid #E2E8F0; }
                        .title-block h1 { font-size: 26px; font-weight: 700; color: #131B2E; letter-spacing: -0.5px; }
                        .title-block p { font-size: 13px; color: #64748B; font-weight: 500; }
                        .live-pill { display: inline-flex; align-items: center; gap: 6px; background: #DCFCE7; color: #16A34A; padding: 6px 12px; border-radius: 999px; font-size: 12px; font-weight: 700; }
                        .stats-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 16px; margin-bottom: 24px; }
                        .stat-card { background: white; border-radius: 14px; padding: 18px; border: 1px solid #E2E8F0; box-shadow: 0 1px 4px rgba(0,0,0,0.03); }
                        .stat-card h3 { font-size: 11px; text-transform: uppercase; color: #64748B; letter-spacing: 0.5px; }
                        .stat-card .val { font-family: 'Space Grotesk', sans-serif; font-size: 32px; font-weight: 700; color: #131B2E; margin-top: 4px; }
                        .table-card { background: white; border-radius: 14px; border: 1px solid #E2E8F0; overflow: hidden; box-shadow: 0 1px 4px rgba(0,0,0,0.03); }
                        table { width: 100%; border-collapse: collapse; text-align: left; }
                        th { background: #F8FAFC; padding: 14px 16px; font-size: 11px; text-transform: uppercase; color: #64748B; letter-spacing: 0.5px; border-bottom: 1px solid #E2E8F0; }
                        td { padding: 14px 16px; border-bottom: 1px solid #F1F5F9; font-size: 14px; vertical-align: middle; }
                        tr:hover td { background: #F8FAFC; }
                        .badge-chip { display: inline-block; padding: 4px 10px; border-radius: 6px; font-size: 11px; font-weight: 700; text-transform: uppercase; }
                        .badge-VERIFIED { background: #DCFCE7; color: #16A34A; }
                        .badge-WITNESSED { background: #DBEAFE; color: #2563EB; }
                        .badge-OFFLINE_VERIFIED { background: #FEF3C7; color: #D97706; }
                        .badge-FLAGGED { background: #FEE2E2; color: #DC2626; }
                        .badge-TAMPERED { background: #7F1D1D; color: #FEE2E2; }
                        .mono { font-family: 'Space Grotesk', monospace; font-size: 13px; font-weight: 600; }
                        .btn { display: inline-block; padding: 6px 12px; border-radius: 6px; font-size: 12px; font-weight: 600; text-decoration: none; color: #EA580C; background: #FFF7ED; border: 1px solid #FFEDD5; }
                        .btn:hover { background: #FFEDD5; }
                        """.trimIndent()
                    )
                }
            }
        }
        body {
            div(classes = "container") {
                header {
                    div(classes = "title-block") {
                        h1 { +"PRAMAAN CENTRAL VERIFICATION AUTHORITY" }
                        p { +"National Sports Talent Re-Audit Engine • Auto-refreshing every 5s" }
                    }
                    div(classes = "live-pill") {
                        span { +"● SERVER RUNNING • PORT 8080" }
                    }
                }

                div(classes = "stats-grid") {
                    div(classes = "stat-card") {
                        h3 { +"Total Uploaded Sessions" }
                        div(classes = "val") { +"${sessions.size}" }
                    }
                    div(classes = "stat-card") {
                        h3 { +"Hardware Verified" }
                        div(classes = "val") { +"${sessions.count { it.badge == Badge.VERIFIED || it.badge == Badge.WITNESSED }}" }
                    }
                    div(classes = "stat-card") {
                        h3 { +"Co-Signed by Witness" }
                        div(classes = "val") { +"${sessions.count { it.badge == Badge.WITNESSED }}" }
                    }
                    div(classes = "stat-card") {
                        h3 { +"Flagged / Tampered" }
                        div(classes = "val") { +"${sessions.count { it.badge == Badge.FLAGGED || it.badge == Badge.TAMPERED }}" }
                    }
                }

                div(classes = "table-card") {
                    table {
                        thead {
                            tr {
                                th { +"Session ID" }
                                th { +"Candidate" }
                                th { +"Test Type" }
                                th { +"Score" }
                                th { +"Audit Badge" }
                                th { +"Captured At" }
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
                                for (sess in sessions) {
                                    tr {
                                        td(classes = "mono") { +"#${sess.id.take(8).uppercase()}" }
                                        td { +"${sess.athleteInitials} (${sess.athleteId})" }
                                        td { +sess.test.replace('_', ' ') }
                                        td(classes = "mono") { +"${sess.best} ${sess.unit}" }
                                        td {
                                            span(classes = "badge-chip badge-${sess.badge.name}") {
                                                +sess.badge.name.replace('_', ' ')
                                            }
                                        }
                                        td { +sess.capturedAt }
                                        td {
                                            if (sess.certJws != null) {
                                                a(classes = "btn", href = "/v/${sess.certJws}") {
                                                    +"View Certificate"
                                                }
                                            } else {
                                                a(classes = "btn", href = "/api/sessions/${sess.id}") {
                                                    +"Details"
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
    }

    fun HTML.renderVerificationPage(claims: JWTClaimsSet?, isValid: Boolean) {
        head {
            title { +"Pramaan Certificate Verification" }
            meta(charset = "utf-8")
            meta(name = "viewport", content = "width=device-width, initial-scale=1.0")
            link(rel = "stylesheet", href = "https://fonts.googleapis.com/css2?family=Lexend:wght@400;500;600;700&family=Space+Grotesk:wght@500;600;700&display=swap")
            style {
                unsafe {
                    raw(
                        """
                        body { font-family: 'Lexend', sans-serif; background: #FAF8FF; color: #131B2E; display: flex; align-items: center; justify-content: center; min-height: 100vh; padding: 20px; }
                        .card { background: white; border-radius: 20px; max-width: 520px; width: 100%; padding: 32px; border: 1px solid #E2E8F0; box-shadow: 0 4px 20px rgba(0,0,0,0.06); }
                        .banner { display: flex; align-items: center; gap: 10px; margin-bottom: 20px; }
                        .status { display: inline-block; padding: 6px 14px; border-radius: 999px; font-weight: 700; font-size: 13px; }
                        .valid { background: #DCFCE7; color: #16A34A; }
                        .invalid { background: #FEE2E2; color: #DC2626; }
                        h2 { font-size: 24px; font-weight: 700; margin-bottom: 6px; }
                        .grid { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; margin: 20px 0; background: #F8FAFC; padding: 16px; border-radius: 12px; }
                        .item h4 { font-size: 11px; text-transform: uppercase; color: #64748B; }
                        .item p { font-size: 16px; font-weight: 600; margin-top: 2px; }
                        .mono { font-family: 'Space Grotesk', monospace; word-break: break-all; font-size: 11px; background: #F1F5F9; padding: 10px; border-radius: 8px; margin-top: 14px; color: #475569; }
                        a { display: block; text-align: center; margin-top: 20px; color: #EA580C; font-weight: 600; text-decoration: none; font-size: 14px; }
                        """.trimIndent()
                    )
                }
            }
        }
        body {
            div(classes = "card") {
                if (isValid && claims != null) {
                    div(classes = "banner") {
                        span(classes = "status valid") { +"OFFICIAL TALENT CREDENTIAL • VERIFIED" }
                    }
                    h2 { +"Sports Authority of India Partner System" }
                    p { +"Digital Certificate cryptographically authenticated by Pramaan Authority." }

                    div(classes = "grid") {
                        div(classes = "item") {
                            h3 { +"Candidate ID" }
                            p { +"${claims.subject} (${claims.getClaim("ini")})" }
                        }
                        div(classes = "item") {
                            h3 { +"Standardized Test" }
                            p { +"${claims.getClaim("tst")}".replace('_', ' ') }
                        }
                        div(classes = "item") {
                            h3 { +"Validated Score" }
                            p { +"${claims.getClaim("val")} ${claims.getClaim("unt")}" }
                        }
                        div(classes = "item") {
                            h3 { +"Audit Level" }
                            p { +"${claims.getClaim("bdg")}".replace('_', ' ') }
                        }
                    }

                    div(classes = "mono") {
                        +"ISSUER: ${claims.issuer} | WITNESS_ID: ${claims.getClaim("wit") ?: "NONE"} | ISSUED_AT: ${claims.issueTime}"
                    }
                } else {
                    div(classes = "banner") {
                        span(classes = "status invalid") { +"FRAUD DETECTED • INVALID SIGNATURE" }
                    }
                    h2 { +"Credential Verification Failed" }
                    p { +"This digital certificate has been altered, forged, or the cryptographic signature does not match the authority." }
                }

                a(href = "/") { +"← Back to Central Dashboard" }
            }
        }
    }
}
