package app.pramaan.core.crypto

import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 * Verifies ECDSA signatures using SHA256withECDSA and JDK java.security.
 */
object EcdsaVerifier {
    /**
     * Verifies an ECDSA signature over message bytes.
     * @param publicKeyBase64Url X.509 SubjectPublicKeyInfo public key in base64url
     * @param message Message payload bytes
     * @param signatureDerBase64Url ASN.1 DER-encoded signature in base64url
     * @return true if signature is valid, false otherwise
     */
    fun verify(
        publicKeyBase64Url: String,
        message: ByteArray,
        signatureDerBase64Url: String
    ): Boolean {
        return try {
            val publicKeyBytes = Base64Url.decode(publicKeyBase64Url)
            val signatureDerBytes = Base64Url.decode(signatureDerBase64Url)
            verify(publicKeyBytes, message, signatureDerBytes)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Verifies an ECDSA signature using raw byte arrays.
     */
    fun verify(
        publicKeyBytes: ByteArray,
        message: ByteArray,
        signatureDerBytes: ByteArray
    ): Boolean {
        return try {
            val keyFactory = KeyFactory.getInstance("EC")
            val keySpec = X509EncodedKeySpec(publicKeyBytes)
            val publicKey = keyFactory.generatePublic(keySpec)

            val verifier = Signature.getInstance("SHA256withECDSA")
            verifier.initVerify(publicKey)
            verifier.update(message)
            verifier.verify(signatureDerBytes)
        } catch (_: Exception) {
            false
        }
    }
}
