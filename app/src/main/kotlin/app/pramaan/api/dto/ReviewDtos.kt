package app.pramaan.api.dto

import app.pramaan.core.model.CheckResult
import kotlinx.serialization.Serializable

@Serializable
data class CoachQueueItemDto(
    val sessionId: String,
    val athleteId: String,
    val athleteName: String,
    val athletePhotoUrl: String? = null,
    val age: Int? = null,
    val sex: String? = null,
    val test: String,
    val best: Double,
    val unit: String,
    val integrity: String,
    val submittedAt: Long? = null
)

@Serializable
data class CoachSessionDetailDto(
    val sessionId: String,
    val athleteId: String,
    val athleteName: String,
    val athletePhotoUrl: String? = null,
    val age: Int? = null,
    val sex: String? = null,
    val heightCm: Double? = null,
    val weightKg: Double? = null,
    val test: String,
    val best: Double,
    val unit: String,
    val integrity: String,
    val status: String,
    val manifestSha256: String,
    val videoUrl: String,
    val checks: List<CheckResult> = emptyList(),
    val submittedAt: Long? = null,
    val reviewedBy: String? = null,
    val reviewedByName: String? = null,
    val reviewMethod: String? = null,
    val reviewReason: String? = null,
    val certificateJws: String? = null
)

@Serializable
data class CoachReviewRequest(
    val decision: String,
    val method: String = "REMOTE",
    val reason: String? = null,
    val scannedQr: String? = null,
    val reviewedAt: Long,
    val coachSignature: String
)

@Serializable
data class CoachReviewResponse(
    val sessionId: String,
    val status: String,
    val integrity: String,
    val certificateJws: String? = null,
    val verifyUrl: String? = null
)

@Serializable
data class CoachHistoryItemDto(
    val sessionId: String,
    val athleteId: String,
    val athleteName: String,
    val test: String,
    val best: Double,
    val unit: String,
    val status: String,
    val integrity: String,
    val reviewMethod: String? = null,
    val reviewedAt: Long? = null,
    val certificateJws: String? = null
)
