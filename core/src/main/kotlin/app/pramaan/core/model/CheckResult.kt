package app.pramaan.core.model

import kotlinx.serialization.Serializable

@Serializable
data class CheckResult(
    val id: String,
    val pass: Boolean,
    val value: String,
    val limit: String,
    val reason: String? = null
)
