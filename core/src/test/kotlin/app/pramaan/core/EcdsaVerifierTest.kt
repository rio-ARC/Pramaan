package app.pramaan.core

import app.pramaan.core.crypto.Base64Url
import app.pramaan.core.crypto.DerRaw
import app.pramaan.core.crypto.EcdsaVerifier
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec

class EcdsaVerifierTest {

    @Test
    fun `p256 key pair verification passes for valid message and fails on one-byte change`() {
        // Generate P-256 (secp256r1) key pair using JDK
        val keyGen = KeyPairGenerator.getInstance("EC")
        keyGen.initialize(ECGenParameterSpec("secp256r1"))
        val keyPair = keyGen.generateKeyPair()

        val publicKeyBase64Url = Base64Url.encode(keyPair.public.encoded)

        val message = "PRAMAAN canonical manifest content to sign".toByteArray(Charsets.UTF_8)

        // Sign using JDK SHA256withECDSA
        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(keyPair.private)
        signer.update(message)
        val derSignature = signer.sign()
        val signatureDerBase64Url = Base64Url.encode(derSignature)

        // 1. Verification of genuine message must pass
        val isValid = EcdsaVerifier.verify(
            publicKeyBase64Url = publicKeyBase64Url,
            message = message,
            signatureDerBase64Url = signatureDerBase64Url
        )
        isValid shouldBe true

        // 2. Verification of modified message (one byte changed) must fail
        val tamperedMessage = message.copyOf()
        tamperedMessage[0] = (tamperedMessage[0].toInt() xor 0x01).toByte()

        val isTamperedValid = EcdsaVerifier.verify(
            publicKeyBase64Url = publicKeyBase64Url,
            message = tamperedMessage,
            signatureDerBase64Url = signatureDerBase64Url
        )
        isTamperedValid shouldBe false

        // 3. Verification of tampered signature must fail
        val tamperedSignature = derSignature.copyOf()
        tamperedSignature[tamperedSignature.size - 1] = (tamperedSignature[tamperedSignature.size - 1].toInt() xor 0x01).toByte()
        val isTamperedSigValid = EcdsaVerifier.verify(
            publicKeyBase64Url = publicKeyBase64Url,
            message = message,
            signatureDerBase64Url = Base64Url.encode(tamperedSignature)
        )
        isTamperedSigValid shouldBe false

        // 4. Test DerRaw roundtrip and raw signature conversion
        val rawSig = DerRaw.derToRaw(derSignature)
        rawSig.size shouldBe 64

        val reconstructedDer = DerRaw.rawToDer(rawSig)
        val isReconstructedValid = EcdsaVerifier.verify(
            publicKeyBytes = keyPair.public.encoded,
            message = message,
            signatureDerBytes = reconstructedDer
        )
        isReconstructedValid shouldBe true
    }
}
