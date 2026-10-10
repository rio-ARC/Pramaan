package app.pramaan.core.model

import kotlinx.serialization.Serializable

/**
 * A single analysed frame from video or camera.
 * @property t Frame timestamp in nanoseconds
 * @property landmarks 33 pose landmarks in normalised image coordinates
 */
@Serializable
data class PoseFrame(
    val t: Long,
    val landmarks: List<Landmark>
) {
    init {
        require(landmarks.isEmpty() || landmarks.size == 33) {
            "PoseFrame must contain either 0 (empty) or 33 landmarks, got ${landmarks.size}"
        }
    }
}
