package app.pramaan.core.crypto

import java.math.BigInteger

/**
 * Converts ECDSA signatures between ASN.1 DER format (used by JCA SHA256withECDSA)
 * and raw concatenated 64-byte r||s format (used by JWS / WebCrypto / IEEE P1363).
 */
object DerRaw {
    fun derToRaw(der: ByteArray): ByteArray {
        require(der.size >= 8 && der[0] == 0x30.toByte()) { "Invalid DER sequence header" }
        var offset = 1
        val seqLenByte = der[offset++].toInt() and 0xFF
        if (seqLenByte > 0x80) {
            val numBytes = seqLenByte and 0x7F
            offset += numBytes // Skip multi-byte sequence length
        }

        require(der[offset++] == 0x02.toByte()) { "Expected ASN.1 INTEGER marker for r" }
        val rLen = der[offset++].toInt() and 0xFF
        val rBytes = der.copyOfRange(offset, offset + rLen)
        offset += rLen

        require(der[offset++] == 0x02.toByte()) { "Expected ASN.1 INTEGER marker for s" }
        val sLen = der[offset++].toInt() and 0xFF
        val sBytes = der.copyOfRange(offset, offset + sLen)

        val rBig = BigInteger(1, rBytes)
        val sBig = BigInteger(1, sBytes)

        val raw = ByteArray(64)
        writeUnsigned32(rBig, raw, 0)
        writeUnsigned32(sBig, raw, 32)
        return raw
    }

    fun rawToDer(raw: ByteArray): ByteArray {
        require(raw.size == 64) { "Raw P-256 signature must be exactly 64 bytes (got ${raw.size})" }
        val rBig = BigInteger(1, raw.copyOfRange(0, 32))
        val sBig = BigInteger(1, raw.copyOfRange(32, 64))

        val rBytes = rBig.toByteArray()
        val sBytes = sBig.toByteArray()

        val totalLen = 2 + rBytes.size + 2 + sBytes.size
        val der = ByteArray(2 + totalLen)
        der[0] = 0x30.toByte()
        der[1] = totalLen.toByte()
        der[2] = 0x02.toByte()
        der[3] = rBytes.size.toByte()
        System.arraycopy(rBytes, 0, der, 4, rBytes.size)

        val sOffset = 4 + rBytes.size
        der[sOffset] = 0x02.toByte()
        der[sOffset + 1] = sBytes.size.toByte()
        System.arraycopy(sBytes, 0, der, sOffset + 2, sBytes.size)

        return der
    }

    fun derToRawBase64Url(derBase64Url: String): String {
        val der = Base64Url.decode(derBase64Url)
        return Base64Url.encode(derToRaw(der))
    }

    fun rawToDerBase64Url(rawBase64Url: String): String {
        val raw = Base64Url.decode(rawBase64Url)
        return Base64Url.encode(rawToDer(raw))
    }

    private fun writeUnsigned32(n: BigInteger, out: ByteArray, offset: Int) {
        val bytes = n.toByteArray()
        val srcPos = if (bytes.size > 32) bytes.size - 32 else 0
        val destPos = offset + if (bytes.size < 32) 32 - bytes.size else 0
        val len = minOf(bytes.size, 32)
        System.arraycopy(bytes, srcPos, out, destPos, len)
    }
}
