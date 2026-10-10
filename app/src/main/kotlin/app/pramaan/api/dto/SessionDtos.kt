package app.pramaan.api.dto

import app.pramaan.core.model.CheckResult
import kotlinx.serialization.Serializable

@Serializable
data class SessionUploadResponseDto(
    val sessionId: String,
    val status: String,
    val integrity: String,
    val checks: List<CheckResult>
)

@Serializable
data class AthleteSessionSummaryDto(
    val id: String,
    val test: String,
    val best: Double,
    val unit: String,
    val integrity: String,
    val status: String,
    val reviewedByName: String? = null,
    val submittedAt: Long? = null
)

@Serializable
data class AthleteSessionDetailDto(
    val id: String,
    val test: String,
    val best: Double,
    val unit: String,
    val integrity: String,
    val status: String,
    val checks: List<CheckResult> = emptyList(),
    val reviewedByName: String? = null,
    val reviewReason: String? = null,
    val certificateJws: String? = null,
    val verifyUrl: String? = null,
    val submittedAt: Long? = null,
    val reviewedAt: Long? = null
)
