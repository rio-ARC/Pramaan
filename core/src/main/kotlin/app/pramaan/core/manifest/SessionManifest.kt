package app.pramaan.core.manifest

import app.pramaan.core.model.Badge
import app.pramaan.core.model.CheckResult
import app.pramaan.core.model.TestType
import app.pramaan.core.scoring.broadjump.BjAttempt
import app.pramaan.core.scoring.broadjump.GroundCalibration
import kotlinx.serialization.Serializable

@Serializable
data class AthleteManifestInfo(
    val id: String,
    val initials: String,
    val age: Int,
    val sex: String,
    val heightCm: Double
)

@Serializable
data class FileHashes(
    val video: String,
    val frames: String,
    val gyro: String,
    val calibFrame: String? = null,
    val stills: Map<String, String>? = null
)

@Serializable
data class JumpAttempt(
    val flightCm: Double,
    val hipRiseCm: Double,
    val gravity: Double,
    val valid: Boolean
)

@Serializable
data class RejectedRep(
    val t: Double,
    val reason: String
)

@Serializable
data class SessionResult(
    val unit: String,
    val best: Double,
    val attempts: List<JumpAttempt>? = null,
    val rejected: List<RejectedRep>? = null,
    val broadJumpAttempts: List<BjAttempt>? = null
)

@Serializable
data class AnalysisFrameDimensions(
    val w: Int,
    val h: Int
)

@Serializable
data class SessionManifest(
    val v: Int = 1,
    val sessionId: String,
    val test: TestType,
    val source: String,
    val athlete: AthleteManifestInfo,
    val devicePublicKey: String,
    val capturedAt: String,
    val fps: Int,
    val hashes: FileHashes,
    val result: SessionResult,
    val checks: List<CheckResult>,
    val badge: Badge,
    val analyzerVersion: String? = null,
    val analysisFrame: AnalysisFrameDimensions? = null,
    val calibration: GroundCalibration? = null
)
