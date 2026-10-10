package app.pramaan.server

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.authentication
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import java.time.Period

@Serializable
data class CoachLoginRequest(
    val coachId: String,
    val pin: String,
    val devicePublicKey: String
)

data class AccountPrincipal(
    val accountId: String,
    val role: String,
    val account: AccountRecord
) : io.ktor.server.auth.Principal

suspend fun io.ktor.server.application.ApplicationCall.ensureRole(expectedRole: String): AccountPrincipal? {
    val principal = authentication.principal<AccountPrincipal>()
    if (principal == null) {
        respond(HttpStatusCode.Unauthorized, ErrorResponse("UNAUTHORIZED", "Missing or invalid bearer token"))
        return null
    }
    if (principal.role != expectedRole) {
        respond(HttpStatusCode.Forbidden, ErrorResponse("FORBIDDEN_ROLE", "Forbidden: requires role $expectedRole"))
        return null
    }
    return principal
}

@Serializable
data class AccountProfileResponse(
    val id: String,
    val role: String,
    val name: String,
    val dob: String? = null,
    val age: Int? = null,
    val sex: String? = null,
    val heightCm: Double? = null,
    val weightKg: Double? = null,
    val district: String? = null,
    val phone: String? = null,
    val guardianConsent: Boolean = false,
    val hasPhoto: Boolean = false,
    val devicePublicKey: String? = null
)

@Serializable
data class AuthResponse(
    val accountId: String,
    val token: String,
    val role: String,
    val profile: AccountProfileResponse
)

@Serializable
data class ErrorResponse(
    val error: String,
    val message: String
)

fun calculateAge(dobStr: String?): Int? {
    if (dobStr.isNullOrBlank()) return null
    return try {
        val dob = LocalDate.parse(dobStr.trim())
        Period.between(dob, LocalDate.now()).years
    } catch (_: Exception) {
        null
    }
}

fun AccountRecord.toProfileResponse(): AccountProfileResponse {
    return AccountProfileResponse(
        id = id,
        role = role,
        name = name,
        dob = dob,
        age = calculateAge(dob),
        sex = sex,
        heightCm = heightCm,
        weightKg = weightKg,
        district = district,
        phone = phone,
        guardianConsent = guardianConsent,
        hasPhoto = photoPath != null && File(photoPath).exists(),
        devicePublicKey = devicePubkey
    )
}

fun Route.configureAuthRoutes(photosDir: File) {
    val json = Json { ignoreUnknownKeys = true }

    // 1. Athlete registration
    post("/api/auth/athlete/register") {
        try {
            val multipart = call.receiveMultipart()
            var profileJson: String? = null
            var photoBytes: ByteArray? = null

            multipart.forEachPart { part ->
                when (part) {
                    is PartData.FormItem -> {
                        if (part.name == "profile") {
                            profileJson = part.value
                        }
                    }
                    is PartData.FileItem -> {
                        val out = ByteArrayOutputStream()
                        part.streamProvider().use { input ->
                            input.copyTo(out)
                        }
                        photoBytes = out.toByteArray()
                    }
                    else -> {}
                }
                part.dispose()
            }

            if (profileJson.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("MISSING_PROFILE", "Missing profile data"))
                return@post
            }

            val input = try {
                json.decodeFromString<AthleteProfileInput>(profileJson!!)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("INVALID_JSON", "Invalid profile JSON: ${e.message}"))
                return@post
            }

            // Validations per Spec 5.1
            if (input.name.isBlank() || input.dob.isBlank() || input.sex.isBlank() || input.heightCm <= 0) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("VALIDATION_FAILED", "Name, DOB, sex, and height (cm) are required.")
                )
                return@post
            }

            val age = calculateAge(input.dob)
            if (age != null && age < 18 && !input.guardianConsent) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("CONSENT_REQUIRED", "Guardian consent is required for athletes under 18.")
                )
                return@post
            }

            val account = AccountRepo.createAthlete(input, photoBytes, photosDir)
            val token = TokenRepo.createToken(account.id)

            call.respond(
                HttpStatusCode.Created,
                AuthResponse(
                    accountId = account.id,
                    token = token,
                    role = account.role,
                    profile = account.toProfileResponse()
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("SERVER_ERROR", e.message ?: "Unknown error"))
        }
    }

    // 2. Coach login
    post("/api/auth/coach/login") {
        val request = try {
            call.receive<CoachLoginRequest>()
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("INVALID_REQUEST", "Invalid login payload"))
            return@post
        }

        val account = AccountRepo.authenticateCoach(
            coachId = request.coachId.trim(),
            pin = request.pin.trim(),
            devicePublicKey = request.devicePublicKey.trim()
        )

        if (account == null) {
            call.respond(
                HttpStatusCode.Unauthorized,
                ErrorResponse("BAD_CREDENTIALS", "Coach ID or PIN is incorrect")
            )
            return@post
        }

        val token = TokenRepo.createToken(account.id)
        call.respond(
            AuthResponse(
                accountId = account.id,
                token = token,
                role = account.role,
                profile = account.toProfileResponse()
            )
        )
    }

    // 3. Photo streaming endpoint
    get("/api/accounts/{id}/photo") {
        val id = call.parameters["id"] ?: ""
        val account = AccountRepo.getAccount(id)
        if (account?.photoPath != null) {
            val file = File(account.photoPath)
            if (file.exists()) {
                call.respondBytes(file.readBytes(), ContentType.Image.JPEG)
                return@get
            }
        }
        call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Photo not found"))
    }

    // Protected Auth Endpoints
    authenticate("api") {
        // 4. /api/me
        get("/api/me") {
            val principal = call.authentication.principal<AccountPrincipal>()
            if (principal == null) {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("UNAUTHORIZED", "Not authenticated"))
                return@get
            }
            call.respond(principal.account.toProfileResponse())
        }

        // 5. /api/auth/logout
        post("/api/auth/logout") {
            val authHeader = call.request.headers["Authorization"]
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                val token = authHeader.removePrefix("Bearer ").trim()
                TokenRepo.revokeToken(token)
            }
            call.respond(HttpStatusCode.NoContent, "")
        }
    }
}
