package app.pramaan.core.crypto

import java.io.InputStream
import java.security.MessageDigest

/**
 * SHA-256 hashing for byte arrays and streams, returning base64url digests.
 */
object Sha256 {
    fun digest(bytes: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(bytes)
    }

    fun digest(stream: InputStream): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        var read: Int
        while (stream.read(buffer).also { read = it } != -1) {
            md.update(buffer, 0, read)
        }
        return md.digest()
    }

    fun hash(bytes: ByteArray): String {
        return Base64Url.encode(digest(bytes))
    }

    fun hash(stream: InputStream): String {
        return Base64Url.encode(digest(stream))
    }
}
