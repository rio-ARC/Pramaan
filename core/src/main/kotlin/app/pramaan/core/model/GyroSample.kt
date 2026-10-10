package app.pramaan.core.model

import kotlinx.serialization.Serializable

/**
 * A single gyroscope reading sampled during recording.
 * @property t Timestamp in nanoseconds
 * @property x Angular velocity around X axis in rad/s
 * @property y Angular velocity around Y axis in rad/s
 * @property z Angular velocity around Z axis in rad/s
 */
@Serializable
data class GyroSample(
    val t: Long,
    val x: Double,
    val y: Double,
    val z: Double
)
