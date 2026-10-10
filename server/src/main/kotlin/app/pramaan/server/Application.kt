package app.pramaan.server

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface

object ServerConfig {
    var primaryLanIp: String = "127.0.0.1"
    var allLanIps: List<String> = emptyList()

    val dataDir = File("data").apply { if (!exists()) mkdirs() }
    val uploadsDir = File(dataDir, "uploads").apply { if (!exists()) mkdirs() }
    val dbFile = File(dataDir, "pramaan_server.db")
    val keyFile = File(dataDir, "server_key.json")
}

private fun detectLanIps(): List<String> {
    val detected = mutableListOf<String>()
    try {
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return detected
        for (iface in interfaces) {
            if (!iface.isUp || iface.isLoopback || iface.isVirtual) continue
            val name = (iface.displayName ?: iface.name).lowercase()
            // Filter out obvious virtual / container adapters
            if (name.contains("virtual") || name.contains("vmware") ||
                name.contains("vbox") || name.contains("hyper-v") ||
                name.contains("wsl") || name.contains("docker") ||
                name.contains("vethernet")
            ) {
                continue
            }

            for (addr in iface.inetAddresses) {
                if (addr is Inet4Address && !addr.isLoopbackAddress) {
                    val ip = addr.hostAddress
                    if (ip != null && !ip.startsWith("127.")) {
                        detected.add(ip)
                    }
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return detected
}

fun Application.module() {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }

    ServerDb.init(ServerConfig.dbFile)
    val certificateIssuer = ServerCertificateIssuer(ServerConfig.keyFile)

    routing {
        configureRoutes(certificateIssuer, ServerConfig.uploadsDir)
    }
}

fun main() {
    val lanIps = detectLanIps()
    ServerConfig.allLanIps = lanIps
    ServerConfig.primaryLanIp = lanIps.firstOrNull() ?: "127.0.0.1"

    println(
        """
        ========================================================================
           PRAMAAN SPORTS TALENT VERIFICATION SERVER (PORT 8080)
        ========================================================================
         [✓] Laptop Web Dashboard:  http://localhost:8080
         [✓] LAN Endpoint for Phone: http://${ServerConfig.primaryLanIp}:8080 (or mobile hotspot)
         
         To pair your phone:
         1. Open http://localhost:8080 on your laptop browser.
         2. In the Pramaan app, go to Central Server Sync -> Tap 'Scan Server QR'.
         3. Point phone at the pairing QR on your laptop screen!
        ========================================================================
        """.trimIndent()
    )

    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}
