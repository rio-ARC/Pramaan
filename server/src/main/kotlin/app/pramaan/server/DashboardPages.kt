package app.pramaan.server

import app.pramaan.core.model.Badge
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.nimbusds.jwt.JWTClaimsSet
import kotlinx.html.HTML
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.button
import kotlinx.html.code
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.h3
import kotlinx.html.head
import kotlinx.html.header
import kotlinx.html.img
import kotlinx.html.link
import kotlinx.html.meta
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
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.ImageIO

object DashboardPages {

    fun generateQrBase64(content: String, size: Int = 180): String {
        return try {
            val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
            val image = BufferedImage(size, size, BufferedImage.TYPE_INT_RGB)
            for (x in 0 until size) {
                for (y in 0 until size) {
                    image.setRGB(x, y, if (bitMatrix.get(x, y)) 0x000000 else 0xFFFFFF)
                }
            }
            val baos = ByteArrayOutputStream()
            ImageIO.write(image, "PNG", baos)
            Base64.getEncoder().encodeToString(baos.toByteArray())
        } catch (e: Exception) {
            ""
        }
    }

    fun HTML.renderDashboard(sessions: List<ServerSessionRecord>, lanIp: String = ServerConfig.primaryLanIp) {
        val serverUrl = "http://$lanIp:8080"
        val qrBase64 = generateQrBase64(serverUrl, 160)

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
                        
                        .pair-card { display: flex; align-items: center; gap: 24px; background: white; border: 1.5px solid #FFEDD5; border-radius: 16px; padding: 20px; margin-bottom: 24px; box-shadow: 0 4px 12px rgba(234, 88, 12, 0.05); }
                        .pair-qr { flex-shrink: 0; background: white; padding: 8px; border-radius: 12px; border: 1px solid #E2E8F0; display: flex; align-items: center; justify-content: center; }
                        .pair-info { flex: 1; }
                        .pair-badge { display: inline-block; background: #FFF7ED; color: #EA580C; font-size: 11px; font-weight: 700; padding: 3px 8px; border-radius: 6px; margin-bottom: 6px; letter-spacing: 0.5px; }
                        .pair-info h2 { font-size: 18px; font-weight: 700; color: #131B2E; margin-bottom: 4px; }
                        .pair-info p { font-size: 13px; color: #64748B; margin-bottom: 12px; }
                        .url-box { display: inline-flex; align-items: center; gap: 10px; background: #F8FAFC; border: 1px solid #E2E8F0; border-radius: 8px; padding: 6px 12px; }
                        .url-box code { font-family: 'Space Grotesk', monospace; font-size: 14px; font-weight: 600; color: #0F172A; }
                        .copy-btn { background: #EA580C; color: white; border: none; border-radius: 6px; font-size: 12px; font-weight: 600; padding: 4px 10px; cursor: pointer; transition: background 0.2s; }
                        .copy-btn:hover { background: #C2410C; }

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

                // Pair Field Station Hero Card
                div(classes = "pair-card") {
                    div(classes = "pair-qr") {
                        if (qrBase64.isNotEmpty()) {
                            img(src = "data:image/png;base64,$qrBase64", alt = "Scan to Pair with Pramaan App") {
                                attributes["width"] = "140"
                                attributes["height"] = "140"
                                attributes["style"] = "display: block;"
                            }
                        }
                    }
                    div(classes = "pair-info") {
                        span(classes = "pair-badge") { +"PAIR FIELD STATION" }
                        h2 { +"Connect Pramaan Android App" }
                        p {
                            unsafe { raw("Point your phone's camera at this QR code via <b>Central Server Sync → Scan Server QR</b>, or copy the endpoint URL below:") }
                        }
                        div(classes = "url-box") {
                            code { +serverUrl }
                            button(classes = "copy-btn") {
                                attributes["onclick"] = "navigator.clipboard.writeText('$serverUrl'); this.innerText='Copied!';"
                                +"Copy URL"
                            }
                        }
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
