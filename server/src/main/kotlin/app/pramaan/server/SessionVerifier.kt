package app.pramaan.server

import app.pramaan.core.integrity.IntegrityEngine
import app.pramaan.core.integrity.VerificationOutcome
import app.pramaan.core.manifest.CanonicalJson
import app.pramaan.core.manifest.SessionManifest
import app.pramaan.core.manifest.WitnessAttestation
import java.io.File

object SessionVerifier {

    data class ServerVerifyResult(
        val manifest: SessionManifest,
        val outcome: VerificationOutcome,
        val witnessAttestation: WitnessAttestation?
    )

    fun verifyUploadedSession(sessionFolder: File): ServerVerifyResult {
        val manifestFile = File(sessionFolder, "manifest.json")
        require(manifestFile.exists()) { "Missing manifest.json in session folder" }

        val manifest = CanonicalJson.decodeFromByteArray(
            SessionManifest.serializer(),
            manifestFile.readBytes()
        )

        val outcome = IntegrityEngine.verifySessionFolder(sessionFolder)

        val witnessFile = File(sessionFolder, "witness.json")
        val witnessAttestation = if (witnessFile.exists()) {
            try {
                CanonicalJson.decodeFromByteArray(
                    WitnessAttestation.serializer(),
                    witnessFile.readBytes()
                )
            } catch (_: Exception) {
                null
            }
        } else null

        return ServerVerifyResult(
            manifest = manifest,
            outcome = outcome,
            witnessAttestation = witnessAttestation
        )
    }
}
