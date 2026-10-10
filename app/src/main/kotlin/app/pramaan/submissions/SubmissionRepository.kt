package app.pramaan.submissions

import app.pramaan.api.ApiClient
import app.pramaan.api.dto.AthleteSessionDetailDto
import app.pramaan.api.dto.AthleteSessionSummaryDto
import app.pramaan.api.dto.SessionUploadResponseDto
import app.pramaan.auth.AuthStore
import app.pramaan.data.SessionDao
import app.pramaan.data.SessionEntity
import app.pramaan.data.SessionStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class SubmissionRepository(
    private val apiClient: ApiClient,
    private val sessionDao: SessionDao,
    private val sessionStore: SessionStore,
    private val authStore: AuthStore
) {

    suspend fun getMySubmissionsFlow(): Flow<List<SessionEntity>> {
        val accountId = authStore.getAccountId() ?: return flowOf(emptyList())
        return sessionDao.getByOwnerAccountIdFlow(accountId)
    }

    suspend fun submitSession(sessionId: String): Result<SessionUploadResponseDto> {
        val result = apiClient.uploadSession(sessionId, sessionStore)
        if (result.isSuccess) {
            val response = result.getOrThrow()
            val existing = sessionDao.getById(sessionId)
            if (existing != null) {
                sessionDao.update(
                    existing.copy(
                        status = response.status,
                        serverIntegrity = response.integrity
                    )
                )
            }
        }
        return result
    }

    suspend fun pollSessionStatus(sessionId: String): Result<AthleteSessionDetailDto> {
        val result = apiClient.getSessionDetail(sessionId)
        if (result.isSuccess) {
            val detail = result.getOrThrow()
            val existing = sessionDao.getById(sessionId)
            if (existing != null) {
                sessionDao.update(
                    existing.copy(
                        status = detail.status,
                        serverIntegrity = detail.integrity,
                        reviewedByName = detail.reviewedByName,
                        reviewReason = detail.reviewReason,
                        certificateJws = detail.certificateJws
                    )
                )
            }
        }
        return result
    }

    suspend fun refreshSubmissions(): Result<List<AthleteSessionSummaryDto>> {
        return apiClient.getMySessions()
    }
}
