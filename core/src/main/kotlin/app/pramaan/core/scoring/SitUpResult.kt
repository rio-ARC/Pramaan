package app.pramaan.core.scoring

import app.pramaan.core.manifest.RejectedRep
import kotlinx.serialization.Serializable

@Serializable
data class CompletedRep(
    val repNumber: Int,
    val startTimestampNs: Long,
    val endTimestampNs: Long,
    val durationSeconds: Double,
    val peakAngleDeg: Double
)

@Serializable
data class SitUpResult(
    val totalReps: Int,
    val completedReps: List<CompletedRep>,
    val rejectedReps: List<RejectedRep>,
    val durationSeconds: Double
)
