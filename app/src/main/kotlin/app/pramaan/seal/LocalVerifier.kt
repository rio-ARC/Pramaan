package app.pramaan.seal

import app.pramaan.core.integrity.IntegrityEngine
import app.pramaan.core.integrity.VerificationOutcome
import app.pramaan.data.SessionDao
import app.pramaan.data.SessionStore
import java.io.File

class LocalVerifier(
    private val sessionStore: SessionStore,
    private val sessionDao: SessionDao
) {
    suspend fun verify(sessionId: String): VerificationOutcome {
        val folder = sessionStore.getSessionDir(sessionId)
        val outcome = IntegrityEngine.verifySessionFolder(folder)

        // Update database record badge
        val existing = sessionDao.getById(sessionId)
        if (existing != null && existing.localIntegrity != outcome.badge.name) {
            sessionDao.update(existing.copy(localIntegrity = outcome.badge.name))
        }
        return outcome
    }

    suspend fun attachWitnessAttestation(sessionId: String, qrString: String): Boolean {
        val decoded = app.pramaan.core.manifest.WitnessQrCodec.decode(qrString) ?: return false
        if (decoded.type != 'W') return false

        val payloadBytes = app.pramaan.core.crypto.Base64Url.decode(decoded.payloadJsonBase64Url)
        val payload = app.pramaan.core.manifest.CanonicalJson.decodeFromByteArray(
            app.pramaan.core.manifest.WitnessQrPayload.serializer(),
            payloadBytes
        )

        if (payload.sid != sessionId) return false

        // Verify witness signature against payload
        val wpkBytes = app.pramaan.core.crypto.Base64Url.decode(payload.wpk)
        val sigDer = app.pramaan.core.crypto.DerRaw.rawToDer(
            app.pramaan.core.crypto.Base64Url.decode(decoded.signatureBase64Url)
        )
        val isValidSig = app.pramaan.core.crypto.EcdsaVerifier.verify(wpkBytes, payloadBytes, sigDer)
        if (!isValidSig) return false

        // Write witness.json into session dir
        val attestation = app.pramaan.core.manifest.WitnessAttestation(
            sid = payload.sid,
            d = payload.d,
            wpk = payload.wpk,
            t = payload.t,
            sig = decoded.signatureBase64Url
        )
        val witnessFile = sessionStore.getWitnessFile(sessionId)
        witnessFile.writeBytes(app.pramaan.core.manifest.CanonicalJson.encodeToByteArray(attestation))

        // Re-verify session to update badge in DB
        verify(sessionId)
        return true
    }
}
