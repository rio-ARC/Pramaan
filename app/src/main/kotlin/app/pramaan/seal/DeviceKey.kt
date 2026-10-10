package app.pramaan.seal

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import app.pramaan.core.crypto.Base64Url
import app.pramaan.core.crypto.Sha256
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/**
 * Manages the non-exportable hardware-backed Android Keystore EC key (PRD 10.5).
 * Alias: "pramaan_device"
 */
class DeviceKey {
    companion object {
        const val KEY_ALIAS = "pramaan_device"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    }

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }

    init {
        ensureKeyExists()
    }

    private fun ensureKeyExists() {
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE)
            val parameterSpec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_SIGN
            )
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build()

            kpg.initialize(parameterSpec)
            kpg.generateKeyPair()
        }
    }

    fun getPublicKeyBytes(): ByteArray {
        val cert = keyStore.getCertificate(KEY_ALIAS)
            ?: throw IllegalStateException("Certificate for $KEY_ALIAS not found")
        return cert.publicKey.encoded
    }

    fun getPublicKeyBase64Url(): String {
        return Base64Url.encode(getPublicKeyBytes())
    }

    /**
     * First 8 hex characters of SHA-256(publicKey) representing the device / station ID.
     */
    fun getKeyFingerprint(): String {
        val digest = Sha256.digest(getPublicKeyBytes())
        return digest.take(4).joinToString("") { "%02x".format(it) }
    }

    /**
     * Signs data using hardware-backed private key with SHA256withECDSA, returning DER base64url.
     */
    fun sign(data: ByteArray): String {
        val privateKey = keyStore.getKey(KEY_ALIAS, null) as? PrivateKey
            ?: throw IllegalStateException("Private key for $KEY_ALIAS not found")
        val signature = Signature.getInstance("SHA256withECDSA").apply {
            initSign(privateKey)
            update(data)
        }
        val derBytes = signature.sign()
        return Base64Url.encode(derBytes)
    }
}
