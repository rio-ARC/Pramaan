package app.pramaan.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class AthleteProfileRegisterRequest(
    val name: String,
    val dob: String,
    val sex: String,
    val heightCm: Double,
    val weightKg: Double? = null,
    val district: String? = null,
    val phone: String? = null,
    val guardianConsent: Boolean = false,
    val devicePublicKey: String
)

@Serializable
data class CoachLoginRequest(
    val coachId: String,
    val pin: String,
    val devicePublicKey: String
)

@Serializable
data class AccountProfileDto(
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
data class AuthResponseDto(
    val accountId: String,
    val token: String,
    val role: String,
    val profile: AccountProfileDto
)

@Serializable
data class ApiErrorDto(
    val error: String,
    val message: String
)
