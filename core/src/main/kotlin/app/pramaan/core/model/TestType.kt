package app.pramaan.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class TestType {
    VERTICAL_JUMP,
    SIT_UPS;

    val displayName: String
        get() = when (this) {
            VERTICAL_JUMP -> "Standing Vertical Jump"
            SIT_UPS -> "Sit-ups (30 Seconds)"
        }

    val unit: String
        get() = when (this) {
            VERTICAL_JUMP -> "cm"
            SIT_UPS -> "reps"
        }
}
