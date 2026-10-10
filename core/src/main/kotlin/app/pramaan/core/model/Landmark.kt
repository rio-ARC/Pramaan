package app.pramaan.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Landmark(
    val x: Float,
    val y: Float,
    val visibility: Float = 1.0f
)
