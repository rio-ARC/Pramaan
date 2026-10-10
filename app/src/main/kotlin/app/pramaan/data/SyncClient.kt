package app.pramaan.data

import android.os.SystemClock
import app.pramaan.core.model.Badge
import app.pramaan.core.model.CheckResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.TimeUnit

@Serializable
data class SyncResponse(
    val sessionId: String,
    val serverBadge: Badge,
    val checks: List<CheckResult>,
    val certificate: String? = null
)

@Serializable
data class HealthStatusResponse(
    val status: String = "",
    val service: String = "",
    val version: String = "",
    val lanIp: String? = null
)

data class HealthCheckResult(
    val isSuccess: Boolean,
    val latencyMs: Long,
    val message: String
)

class SyncClient(private val sessionStore: SessionStore) {

    private val json = Json { ignoreUnknownKeys = true }

    private val client = HttpClient(OkHttp) {
        engine {
            config {
                connectTimeout(5, TimeUnit.SECONDS)
                readTimeout(60, TimeUnit.SECONDS)
                writeTimeout(60, TimeUnit.SECONDS)
            }
        }
        install(ContentNegotiation) {
            json(json)
        }
    }

    suspend fun checkHealth(serverUrl: String): HealthCheckResult {
        val cleanUrl = serverUrl.trim().trimEnd('/')
        if (cleanUrl.isBlank()) {
            return HealthCheckResult(false, 0, "Server URL is empty.")
        }
        val healthUrl = "$cleanUrl/api/health"
        val startTime = SystemClock.elapsedRealtime()

        return try {
            withTimeout(3500L) {
                val response = client.get(healthUrl)
                val latency = SystemClock.elapsedRealtime() - startTime
                if (response.status == HttpStatusCode.OK) {
                    HealthCheckResult(true, latency, "Connected to Pramaan Server (Latency: ${latency}ms)")
                } else {
                    HealthCheckResult(false, latency, "Server responded with status: ${response.status}")
                }
            }
        } catch (e: Exception) {
            val latency = SystemClock.elapsedRealtime() - startTime
            HealthCheckResult(
                false,
                latency,
                "Cannot reach server at $cleanUrl (${e.message ?: "Connection timed out"}). Ensure both devices are on the same Wi-Fi or Hotspot, and run allow-firewall.bat on your laptop."
            )
        }
    }

    suspend fun uploadSession(serverUrl: String, sessionId: String): SyncResponse? {
        val cleanUrl = serverUrl.trim().trimEnd('/')
        val uploadUrl = "$cleanUrl/api/sessions"

        val manifestFile = sessionStore.getManifestFile(sessionId)
        val sigFile = sessionStore.getSigFile(sessionId)
        val videoFile = sessionStore.getVideoFile(sessionId)
        val framesFile = sessionStore.getFramesFile(sessionId)
        val gyroFile = sessionStore.getGyroFile(sessionId)
        val witnessFile = sessionStore.getWitnessFile(sessionId)

        if (!manifestFile.exists()) return null

        val response = client.post(uploadUrl) {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("manifest.json", manifestFile.readBytes(), Headers.build {
                            append(HttpHeaders.ContentDisposition, "filename=\"manifest.json\"")
                        })
                        if (sigFile.exists()) {
                            append("manifest.sig", sigFile.readBytes(), Headers.build {
                                append(HttpHeaders.ContentDisposition, "filename=\"manifest.sig\"")
                            })
                        }
                        if (videoFile.exists()) {
                            append("video.mp4", videoFile.readBytes(), Headers.build {
                                append(HttpHeaders.ContentDisposition, "filename=\"video.mp4\"")
                            })
                        }
                        if (framesFile.exists()) {
                            append("frames.jsonl", framesFile.readBytes(), Headers.build {
                                append(HttpHeaders.ContentDisposition, "filename=\"frames.jsonl\"")
                            })
                        }
                        if (gyroFile.exists()) {
                            append("gyro.jsonl", gyroFile.readBytes(), Headers.build {
                                append(HttpHeaders.ContentDisposition, "filename=\"gyro.jsonl\"")
                            })
                        }
                        if (witnessFile.exists()) {
                            append("witness.json", witnessFile.readBytes(), Headers.build {
                                append(HttpHeaders.ContentDisposition, "filename=\"witness.json\"")
                            })
                        }
                    }
                )
            )
        }

        val responseBody = response.bodyAsText()
        return try {
            json.decodeFromString<SyncResponse>(responseBody)
        } catch (_: Exception) {
            null
        }
    }
}
