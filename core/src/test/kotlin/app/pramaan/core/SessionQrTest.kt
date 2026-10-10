package app.pramaan.core

import app.pramaan.core.crypto.Base64Url
import app.pramaan.core.manifest.SessionQr
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec

class SessionQrTest {

    @Test
    fun `test S2 session QR encode, decode, and signature verification`() {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        val keyPair = kpg.generateKeyPair()
        val pubKeyB64 = Base64Url.encode(keyPair.public.encoded)

        val sessionId = "ses_1730000000_abcd"
        val manifestSha256Hex = "3a8fb7e100112233445566778899aabbccddeeff"
        val nonce = "abc123nonce45"
        val issuedAtMs = 1730000123456L

        val messageBytes = SessionQr.buildSignedMessageBytes(sessionId, manifestSha256Hex, nonce, issuedAtMs)

        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(keyPair.private)
        signer.update(messageBytes)
        val sigDer = signer.sign()
        val sigB64Url = Base64Url.encode(sigDer)

        val encodedQr = SessionQr.encode(
            sessionId = sessionId,
            manifestSha256Hex = manifestSha256Hex,
            nonce = nonce,
            issuedAtMs = issuedAtMs,
            athleteSigB64url = sigB64Url
        )

        encodedQr.startsWith("PRAMAAN:S2:") shouldBe true

        val decoded = SessionQr.decode(encodedQr)
        decoded shouldNotBe null
        decoded!!.sessionId shouldBe sessionId
        decoded.manifestSha256Hex shouldBe manifestSha256Hex
        decoded.nonce shouldBe nonce
        decoded.issuedAtMs shouldBe issuedAtMs
        decoded.athleteSigB64url shouldBe sigB64Url

        // Verify valid signature
        decoded.verifySignature(pubKeyB64) shouldBe true

        // Verify wrong key fails
        val otherKeyPair = kpg.generateKeyPair()
        val otherPubKeyB64 = Base64Url.encode(otherKeyPair.public.encoded)
        decoded.verifySignature(otherPubKeyB64) shouldBe false
    }

    @Test
    fun `test invalid S2 QR formats fail gracefully`() {
        SessionQr.decode("") shouldBe null
        SessionQr.decode("PRAMAAN:W:something") shouldBe null
        SessionQr.decode("PRAMAAN:S2:missing:parts") shouldBe null
        SessionQr.decode("PRAMAAN:S2:s:m:n:not_a_number:sig") shouldBe null
    }
}
