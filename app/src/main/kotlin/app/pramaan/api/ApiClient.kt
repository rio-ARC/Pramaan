package app.pramaan.api

import app.pramaan.api.dto.AccountProfileDto
import app.pramaan.api.dto.ApiErrorDto
import app.pramaan.api.dto.AthleteProfileRegisterRequest
import app.pramaan.api.dto.AthleteSessionDetailDto
import app.pramaan.api.dto.AthleteSessionSummaryDto
import app.pramaan.api.dto.AuthResponseDto
import app.pramaan.api.dto.CoachLoginRequest
import app.pramaan.api.dto.CoachQueueItemDto
import app.pramaan.api.dto.CoachSessionDetailDto
import app.pramaan.api.dto.CoachReviewRequest
import app.pramaan.api.dto.CoachReviewResponse
import app.pramaan.api.dto.CoachHistoryItemDto
import app.pramaan.api.dto.SessionUploadResponseDto
import app.pramaan.auth.AuthStore
import app.pramaan.data.SessionStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

class ApiClient(private val authStore: AuthStore) {

    private val json = Json { ignoreUnknownKeys = true }

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(json)
        }
        engine {
            config {
                connectTimeout(5, TimeUnit.SECONDS)
                readTimeout(60, TimeUnit.SECONDS)
                writeTimeout(60, TimeUnit.SECONDS)
            }
        }
    }

    suspend fun getBaseUrl(): String = authStore.getServerUrl()

    suspend fun testHealth(targetUrl: String? = null): Result<Boolean> {
        return try {
            val base = (targetUrl ?: getBaseUrl()).trimEnd('/')
            val response = client.get("$base/api/health")
            if (response.status == HttpStatusCode.OK) {
                Result.success(true)
            } else {
                Result.failure(Exception("HTTP ${response.status.value}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun registerAthlete(
        request: AthleteProfileRegisterRequest,
        photoBytes: ByteArray?
    ): Result<AuthResponseDto> {
        return try {
            val base = getBaseUrl()
            val profileJson = json.encodeToString(AthleteProfileRegisterRequest.serializer(), request)

            val response = client.post("$base/api/auth/athlete/register") {
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            append("profile", profileJson)
                            if (photoBytes != null && photoBytes.isNotEmpty()) {
                                append("photo", photoBytes, Headers.build {
                                    append(HttpHeaders.ContentType, "image/jpeg")
                                    append(HttpHeaders.ContentDisposition, "filename=\"selfie.jpg\"")
                                })
                            }
                        }
                    )
                )
            }

            val body = response.bodyAsText()
            if (response.status == HttpStatusCode.Created || response.status == HttpStatusCode.OK) {
                val parsed = json.decodeFromString<AuthResponseDto>(body)
                Result.success(parsed)
            } else {
                val err = parseError(body)
                Result.failure(Exception(err.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun loginCoach(request: CoachLoginRequest): Result<AuthResponseDto> {
        return try {
            val base = getBaseUrl()
            val response = client.post("$base/api/auth/coach/login") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }

            val body = response.bodyAsText()
            if (response.status == HttpStatusCode.OK) {
                val parsed = json.decodeFromString<AuthResponseDto>(body)
                Result.success(parsed)
            } else {
                val err = parseError(body)
                Result.failure(Exception(err.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMe(): Result<AccountProfileDto> {
        return try {
            val base = getBaseUrl()
            val token = authStore.getToken() ?: return Result.failure(Exception("Not logged in"))
            val response = client.get("$base/api/me") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }

            val body = response.bodyAsText()
            if (response.status == HttpStatusCode.OK) {
                Result.success(json.decodeFromString<AccountProfileDto>(body))
            } else {
                Result.failure(Exception("Failed to load profile: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun logout(): Result<Unit> {
        return try {
            val base = getBaseUrl()
            val token = authStore.getToken()
            if (token != null) {
                client.post("$base/api/auth/logout") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
            }
            authStore.clearAuth()
            Result.success(Unit)
        } catch (e: Exception) {
            authStore.clearAuth()
            Result.success(Unit)
        }
    }

    suspend fun uploadSession(
        sessionId: String,
        sessionStore: SessionStore
    ): Result<SessionUploadResponseDto> {
        return try {
            val base = getBaseUrl()
            val token = authStore.getToken() ?: return Result.failure(Exception("Not authenticated"))

            val manifestFile = sessionStore.getManifestFile(sessionId)
            val sigFile = sessionStore.getSigFile(sessionId)
            val videoFile = sessionStore.getVideoFile(sessionId)
            val framesFile = sessionStore.getFramesFile(sessionId)
            val gyroFile = sessionStore.getGyroFile(sessionId)
            val witnessFile = sessionStore.getWitnessFile(sessionId)

            if (!manifestFile.exists()) {
                return Result.failure(Exception("manifest.json not found for session $sessionId"))
            }

            val response = client.post("$base/api/sessions") {
                header(HttpHeaders.Authorization, "Bearer $token")
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

            val body = response.bodyAsText()
            if (response.status == HttpStatusCode.OK) {
                val parsed = json.decodeFromString<SessionUploadResponseDto>(body)
                Result.success(parsed)
            } else {
                val err = parseError(body)
                Result.failure(Exception(err.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMySessions(): Result<List<AthleteSessionSummaryDto>> {
        return try {
            val base = getBaseUrl()
            val token = authStore.getToken() ?: return Result.failure(Exception("Not logged in"))
            val response = client.get("$base/api/me/sessions") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }

            val body = response.bodyAsText()
            if (response.status == HttpStatusCode.OK) {
                Result.success(json.decodeFromString<List<AthleteSessionSummaryDto>>(body))
            } else {
                val err = parseError(body)
                Result.failure(Exception(err.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSessionDetail(sessionId: String): Result<AthleteSessionDetailDto> {
        return try {
            val base = getBaseUrl()
            val token = authStore.getToken() ?: return Result.failure(Exception("Not logged in"))
            val response = client.get("$base/api/me/sessions/$sessionId") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }

            val body = response.bodyAsText()
            if (response.status == HttpStatusCode.OK) {
                Result.success(json.decodeFromString<AthleteSessionDetailDto>(body))
            } else {
                val err = parseError(body)
                Result.failure(Exception(err.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getCoachQueue(): Result<List<CoachQueueItemDto>> {
        return try {
            val base = getBaseUrl()
            val token = authStore.getToken() ?: return Result.failure(Exception("Not logged in"))
            val response = client.get("$base/api/coach/queue") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            val body = response.bodyAsText()
            if (response.status == HttpStatusCode.OK) {
                Result.success(json.decodeFromString<List<CoachQueueItemDto>>(body))
            } else {
                val err = parseError(body)
                Result.failure(Exception(err.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getCoachSessionDetail(sessionId: String): Result<CoachSessionDetailDto> {
        return try {
            val base = getBaseUrl()
            val token = authStore.getToken() ?: return Result.failure(Exception("Not logged in"))
            val response = client.get("$base/api/coach/sessions/$sessionId") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            val body = response.bodyAsText()
            if (response.status == HttpStatusCode.OK) {
                Result.success(json.decodeFromString<CoachSessionDetailDto>(body))
            } else {
                val err = parseError(body)
                Result.failure(Exception(err.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun submitCoachReview(sessionId: String, request: CoachReviewRequest): Result<CoachReviewResponse> {
        return try {
            val base = getBaseUrl()
            val token = authStore.getToken() ?: return Result.failure(Exception("Not logged in"))
            val response = client.post("$base/api/coach/sessions/$sessionId/review") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(CoachReviewRequest.serializer(), request))
            }
            val body = response.bodyAsText()
            if (response.status == HttpStatusCode.OK) {
                Result.success(json.decodeFromString<CoachReviewResponse>(body))
            } else {
                val err = parseError(body)
                Result.failure(Exception(err.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getCoachHistory(): Result<List<CoachHistoryItemDto>> {
        return try {
            val base = getBaseUrl()
            val token = authStore.getToken() ?: return Result.failure(Exception("Not logged in"))
            val response = client.get("$base/api/coach/history") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            val body = response.bodyAsText()
            if (response.status == HttpStatusCode.OK) {
                Result.success(json.decodeFromString<List<CoachHistoryItemDto>>(body))
            } else {
                val err = parseError(body)
                Result.failure(Exception(err.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseError(body: String): ApiErrorDto {
        return try {
            json.decodeFromString<ApiErrorDto>(body)
        } catch (_: Exception) {
            ApiErrorDto("UNKNOWN_ERROR", body.take(150))
        }
    }
}
