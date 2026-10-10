package app.pramaan.core

import app.pramaan.core.crypto.Sha256
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

class Sha256Test {

    @Test
    fun `sha256 matches known NIST and standard test vectors`() {
        // Empty string
        Sha256.hash("".toByteArray(Charsets.UTF_8)) shouldBe "47DEQpj8HBSa-_TImW-5JCeuQeRkm5NMpJWZG3hSuFU"

        // "abc"
        Sha256.hash("abc".toByteArray(Charsets.UTF_8)) shouldBe "ungWv48Bz-pBQUDeXa4iI7ADYaOWF3qctBD_YfIAFa0"

        // "The quick brown fox jumps over the lazy dog"
        val fox = "The quick brown fox jumps over the lazy dog".toByteArray(Charsets.UTF_8)
        Sha256.hash(fox) shouldBe "16j7swfXgJRpypq8sAguT41WUeRtPNt2LQLQvzfJ5ZI"
    }

    @Test
    fun `sha256 stream hashing matches byte hashing`() {
        val data = "PRAMAAN verifiable fitness measurement".toByteArray(Charsets.UTF_8)
        val byteHash = Sha256.hash(data)
        val streamHash = Sha256.hash(ByteArrayInputStream(data))

        byteHash shouldBe streamHash
    }
}
