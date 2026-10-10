package app.pramaan.auth

import app.pramaan.api.ApiClient
import app.pramaan.api.dto.AccountProfileDto
import app.pramaan.api.dto.AthleteProfileRegisterRequest
import app.pramaan.api.dto.CoachLoginRequest
import app.pramaan.seal.DeviceKey

class AuthRepository(
    val authStore: AuthStore,
    private val apiClient: ApiClient,
    private val deviceKey: DeviceKey
) {

    suspend fun registerAthlete(
        name: String,
        dob: String,
        sex: String,
        heightCm: Double,
        weightKg: Double?,
        district: String?,
        phone: String?,
        guardianConsent: Boolean,
        photoBytes: ByteArray?
    ): Result<AccountProfileDto> {
        val pubKey = deviceKey.getPublicKeyBase64Url()
        val request = AthleteProfileRegisterRequest(
            name = name,
            dob = dob,
            sex = sex,
            heightCm = heightCm,
            weightKg = weightKg,
            district = district,
            phone = phone,
            guardianConsent = guardianConsent,
            devicePublicKey = pubKey
        )

        val result = apiClient.registerAthlete(request, photoBytes)
        return result.map { authResponse ->
            authStore.saveAuth(
                accountId = authResponse.accountId,
                role = Role.ATHLETE,
                token = authResponse.token,
                profile = authResponse.profile
            )
            authResponse.profile
        }
    }

    suspend fun loginCoach(
        coachId: String,
        pin: String
    ): Result<AccountProfileDto> {
        val pubKey = deviceKey.getPublicKeyBase64Url()
        val request = CoachLoginRequest(
            coachId = coachId.trim(),
            pin = pin.trim(),
            devicePublicKey = pubKey
        )

        val result = apiClient.loginCoach(request)
        return result.map { authResponse ->
            authStore.saveAuth(
                accountId = authResponse.accountId,
                role = Role.COACH,
                token = authResponse.token,
                profile = authResponse.profile
            )
            authResponse.profile
        }
    }

    suspend fun testConnection(url: String): Result<Boolean> {
        val res = apiClient.testHealth(url)
        if (res.isSuccess) {
            authStore.setServerUrl(url)
        }
        return res
    }

    suspend fun logout(): Result<Unit> {
        return apiClient.logout()
    }
}
