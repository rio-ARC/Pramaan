package app.pramaan.core.crypto

import java.util.Base64

/**
 * Base64URL encoding/decoding without padding.
 */
object Base64Url {
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun encode(bytes: ByteArray): String = encoder.encodeToString(bytes)

    fun decode(str: String): ByteArray {
        val trimmed = str.trim()
        return decoder.decode(trimmed)
    }
}
