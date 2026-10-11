package app.pramaan.server

import app.pramaan.core.crypto.Sha256
import app.pramaan.core.integrity.IntegrityEngine
import app.pramaan.core.integrity.VerificationOutcome
import app.pramaan.core.manifest.CanonicalJson
import app.pramaan.core.manifest.SessionManifest
import app.pramaan.core.manifest.WitnessAttestation
import app.pramaan.core.model.TestType
import app.pramaan.core.scoring.broadjump.BroadJumpResult
import java.io.File

object SessionVerifier {

    data class ServerVerifyResult(
        val manifest: SessionManifest,
        val manifestSha256: String,
        val outcome: VerificationOutcome,
        val witnessAttestation: WitnessAttestation?,
        val serverBroadJumpResult: BroadJumpResult? = null
    )

    fun verifyUploadedSession(
        sessionFolder: File,
        account: AccountRecord? = null
    ): ServerVerifyResult {
        val manifestFile = File(sessionFolder, "manifest.json")
        require(manifestFile.exists()) { "Missing manifest.json in session folder" }

        val manifestBytes = manifestFile.readBytes()
        val manifest = CanonicalJson.decodeFromByteArray(
            SessionManifest.serializer(),
            manifestBytes
        )
        val manifestSha256 = Sha256.hash(manifestBytes)

        val (outcome, serverBroadJumpResult) = if (manifest.test == TestType.BROAD_JUMP) {
            val rescore = BroadJumpRescorer.rescore(sessionFolder, manifest, account)
            rescore.outcome to rescore.serverResult
        } else {
            IntegrityEngine.verifySessionFolder(sessionFolder) to null
        }

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
            manifestSha256 = manifestSha256,
            outcome = outcome,
            witnessAttestation = witnessAttestation,
            serverBroadJumpResult = serverBroadJumpResult
        )
    }
}
