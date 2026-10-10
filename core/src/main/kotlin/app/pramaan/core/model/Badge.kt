package app.pramaan.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class Badge {
    WITNESSED,
    VERIFIED,
    UNSEALED,
    FLAGGED,
    TAMPERED
}
