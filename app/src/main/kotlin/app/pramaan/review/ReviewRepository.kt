package app.pramaan.review

import app.pramaan.api.ApiClient
import app.pramaan.api.dto.CoachHistoryItemDto
import app.pramaan.api.dto.CoachQueueItemDto
import app.pramaan.api.dto.CoachReviewRequest
import app.pramaan.api.dto.CoachReviewResponse
import app.pramaan.api.dto.CoachSessionDetailDto

class ReviewRepository(
    private val apiClient: ApiClient,
    private val reviewSigner: ReviewSigner
) {

    suspend fun getQueue(): Result<List<CoachQueueItemDto>> {
        return apiClient.getCoachQueue()
    }

    suspend fun getSessionDetail(sessionId: String): Result<CoachSessionDetailDto> {
        return apiClient.getCoachSessionDetail(sessionId)
    }

    suspend fun submitReview(
        sessionId: String,
        manifestSha256: String,
        decision: String,
        method: String,
        reason: String? = null,
        scannedQr: String? = null
    ): Result<CoachReviewResponse> {
        val reviewedAt = System.currentTimeMillis()
        val (payload, coachSignature) = reviewSigner.createSignedReview(
            sessionId = sessionId,
            manifestSha256 = manifestSha256,
            decision = decision,
            method = method,
            scannedQr = scannedQr,
            reviewedAt = reviewedAt
        )

        val request = CoachReviewRequest(
            decision = decision,
            method = method,
            reason = reason,
            scannedQr = scannedQr,
            reviewedAt = reviewedAt,
            coachSignature = coachSignature
        )

        return apiClient.submitCoachReview(sessionId, request)
    }

    suspend fun getHistory(): Result<List<CoachHistoryItemDto>> {
        return apiClient.getCoachHistory()
    }
}
