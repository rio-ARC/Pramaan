package app.pramaan.server

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.basic
import io.ktor.server.auth.bearer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface

fun getLanIpAddresses(): List<String> {
    val ips = mutableListOf<String>()
    try {
        val interfaces = NetworkInterface.getNetworkInterfaces()
        for (iface in interfaces) {
            if (iface.isLoopback || !iface.isUp) continue
            val name = iface.name.lowercase()
            if (name.contains("docker") || name.contains("vethernet") || name.contains("wsl") || name.contains("virtual")) continue
            for (addr in iface.inetAddresses) {
                if (addr is Inet4Address && !addr.isLoopbackAddress) {
                    ips.add(addr.hostAddress)
                }
            }
        }
    } catch (_: Exception) {}
    return ips
}

fun Application.module() {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }

    install(Authentication) {
        bearer("api") {
            authenticate { credential ->
                val hash = TokenRepo.hashToken(credential.token)
                TokenRepo.findAccount(hash)?.let { account ->
                    AccountPrincipal(account.id, account.role, account)
                }
            }
        }
        basic("admin") {
            realm = "Pramaan Admin Dashboard"
            validate { credentials ->
                val expectedPass = System.getenv("ADMIN_PASSWORD") ?: "pramaan"
                if (credentials.name == "admin" && credentials.password == expectedPass) {
                    UserIdPrincipal(credentials.name)
                } else null
            }
        }
    }

    val dataDir = (System.getenv("PRAMAAN_DATA_DIR")?.let { File(it) }
        ?: if (File("server").isDirectory) File("server/data") else File("data")).apply { mkdirs() }

    val dbFile = File(dataDir, "pramaan_server.db")
    val keyFile = File(dataDir, "server_key.json")
    val uploadsDir = File(dataDir, "uploads").apply { if (!exists()) mkdirs() }
    val photosDir = File(dataDir, "photos").apply { if (!exists()) mkdirs() }

    ServerDb.init(dbFile)

    // Seed coaches from coaches.json if empty
    val seedFile = when {
        File("server/seed/coaches.json").exists() -> File("server/seed/coaches.json")
        File("seed/coaches.json").exists() -> File("seed/coaches.json")
        else -> File(dataDir, "coaches.json")
    }
    AccountRepo.seedCoachesIfEmpty(seedFile)

    val certificateIssuer = ServerCertificateIssuer(keyFile)

    val lanIps = getLanIpAddresses()
    val primaryLanIp = lanIps.firstOrNull() ?: "127.0.0.1"

    routing {
        configureRoutes(
            certificateIssuer = certificateIssuer,
            uploadsDir = uploadsDir,
            photosDir = photosDir,
            lanIp = primaryLanIp
        )
    }
}

fun main() {
    val lanIps = getLanIpAddresses()
    val primaryLanIp = lanIps.firstOrNull() ?: "127.0.0.1"

    println(
        """
        ========================================================================
           PRAMAAN SPORTS TALENT VERIFICATION SERVER (PORT 8080)
        ========================================================================
         [✓] Laptop Web Dashboard:  http://localhost:8080
         [✓] Mobile Endpoint (LAN): http://$primaryLanIp:8080
         ${if (lanIps.size > 1) "    Additional LAN IPs: " + lanIps.joinToString(", ") else ""}

         HOW TO CONNECT YOUR PHONE:
         1. Open http://localhost:8080 in your laptop browser to view the pairing QR.
         2. Make sure phone and laptop are on the same Wi-Fi or Mobile Hotspot.
         3. On the Pramaan phone app, go to Connect -> Scan Server QR.
         4. Point phone at the pairing QR on your laptop screen!
        ========================================================================
        """.trimIndent()
    )

    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}
