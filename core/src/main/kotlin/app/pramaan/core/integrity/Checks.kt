package app.pramaan.core.integrity

import app.pramaan.core.crypto.EcdsaVerifier
import app.pramaan.core.crypto.Sha256
import app.pramaan.core.manifest.CanonicalJson
import app.pramaan.core.manifest.FileHashes
import app.pramaan.core.manifest.WitnessAttestation
import app.pramaan.core.manifest.WitnessQrPayload
import app.pramaan.core.model.CheckResult
import app.pramaan.core.model.GyroSample
import java.util.Locale

/**
 * Pure functions implementing the 5 PRD integrity checks (C1 - C5).
 */
object Checks {

    /**
     * C1: Tamper check.
     * Verifies manifest signature with device public key and verifies that SHA-256
     * hashes of video, frames, and gyro files match the hashes recorded in the manifest.
     */
    fun checkTamper(
        manifestBytes: ByteArray,
        signatureDerBase64Url: String,
        devicePublicKeyBase64Url: String,
        actualHashes: FileHashes,
        manifestHashes: FileHashes
    ): CheckResult {
        val sigValid = EcdsaVerifier.verify(
            publicKeyBase64Url = devicePublicKeyBase64Url,
            message = manifestBytes,
            signatureDerBase64Url = signatureDerBase64Url
        )

        if (!sigValid) {
            return CheckResult(
                id = "C1",
                pass = false,
                value = "Signature failed",
                limit = "Valid ECDSA P-256 signature",
                reason = "Manifest signature does not verify with device public key"
            )
        }

        val videoMatch = actualHashes.video == manifestHashes.video
        val framesMatch = actualHashes.frames == manifestHashes.frames
        val gyroMatch = actualHashes.gyro == manifestHashes.gyro

        if (!videoMatch || !framesMatch || !gyroMatch) {
            val mismatched = buildList {
                if (!videoMatch) add("video.mp4")
                if (!framesMatch) add("frames.jsonl")
                if (!gyroMatch) add("gyro.jsonl")
            }.joinToString(", ")

            return CheckResult(
                id = "C1",
                pass = false,
                value = "Hash mismatch",
                limit = "Hashes match manifest",
                reason = "Files changed after recording; signature no longer matches ($mismatched)"
            )
        }

        return CheckResult(
            id = "C1",
            pass = true,
            value = "Signed & intact",
            limit = "Signature & hashes match"
        )
    }

    /**
     * C2: Gravity check (jump only).
     * Parabola fitted to hip during flight gives 9.81 +/- 2.5 m/s^2 (7.31 - 12.31 m/s^2).
     */
    fun checkGravity(gravity: Double): CheckResult {
        val minG = 7.31
        val maxG = 12.31
        val pass = gravity in minG..maxG
        val formattedG = String.format(Locale.US, "%.1f", gravity)

        val reason = when {
            gravity < minG -> "Motion too slow for real gravity: measured $formattedG m/s², expected about 9.8"
            gravity > maxG -> "Motion too fast for real gravity: measured $formattedG m/s², expected about 9.8"
            else -> null
        }

        return CheckResult(
            id = "C2",
            pass = pass,
            value = "$formattedG m/s²",
            limit = "7.31–12.31 m/s²",
            reason = reason
        )
    }

    /**
     * C3: Two-method agreement check (jump only).
     * abs(flight-time height - hip-rise height) <= max(4 cm, 20% of flight height).
     */
    fun checkTwoMethodAgreement(flightCm: Double, hipRiseCm: Double): CheckResult {
        val diff = Math.abs(flightCm - hipRiseCm)
        val maxAllowed = Math.max(4.0, 0.20 * flightCm)
        val pass = diff <= maxAllowed

        val formattedDiff = String.format(Locale.US, "%.1f", diff)
        val formattedLimit = String.format(Locale.US, "%.1f", maxAllowed)
        val reason = if (!pass) {
            "Flight time (${String.format(Locale.US, "%.1f", flightCm)} cm) and hip rise (${String.format(Locale.US, "%.1f", hipRiseCm)} cm) disagree by $formattedDiff cm (max allowed $formattedLimit cm)"
        } else null

        return CheckResult(
            id = "C3",
            pass = pass,
            value = "$formattedDiff cm Δ",
            limit = "≤ max(4 cm, 20%)",
            reason = reason
        )
    }

    /**
     * C4: Camera steady check.
     * Gyroscope RMS < 0.05 rad/s during recording (skipped for imported videos).
     */
    fun checkCameraSteady(gyroSamples: List<GyroSample>, isImported: Boolean = false): CheckResult {
        if (isImported) {
            return CheckResult(
                id = "C4",
                pass = true,
                value = "Skipped",
                limit = "< 0.05 rad/s",
                reason = "Skipped for imported video"
            )
        }

        val rms = calculateGyroRms(gyroSamples)
        val pass = rms < 0.05
        val formattedRms = String.format(Locale.US, "%.3f", rms)

        val reason = if (!pass) {
            "Camera moved during recording: gyro RMS $formattedRms rad/s (limit 0.05)"
        } else null

        return CheckResult(
            id = "C4",
            pass = pass,
            value = "$formattedRms rad/s",
            limit = "< 0.05 rad/s",
            reason = reason
        )
    }

    fun calculateGyroRms(samples: List<GyroSample>): Double {
        if (samples.isEmpty()) return 0.0
        var sumSquares = 0.0
        for (s in samples) {
            sumSquares += (s.x * s.x + s.y * s.y + s.z * s.z)
        }
        return Math.sqrt(sumSquares / samples.size)
    }

    /**
     * C5: Witness co-sign check.
     * Witness signature valid over {sid, d, wpk, t}, d equals SHA-256 of manifest.json,
     * wpk != devicePublicKey.
     */
    fun checkWitness(
        witness: WitnessAttestation?,
        manifestSha256: String,
        devicePublicKeyBase64Url: String
    ): CheckResult {
        if (witness == null) {
            return CheckResult(
                id = "C5",
                pass = false,
                value = "None",
                limit = "Distinct witness key",
                reason = "No witness co-signature present"
            )
        }

        if (witness.wpk == devicePublicKeyBase64Url) {
            return CheckResult(
                id = "C5",
                pass = false,
                value = "Same key",
                limit = "Distinct witness key",
                reason = "Witness key cannot be the same as coach device key"
            )
        }

        if (witness.d != manifestSha256) {
            return CheckResult(
                id = "C5",
                pass = false,
                value = "Digest mismatch",
                limit = "Matching session digest",
                reason = "Witness signed a different session digest"
            )
        }

        val payload = WitnessQrPayload(
            sid = witness.sid,
            d = witness.d,
            wpk = witness.wpk,
            t = witness.t
        )
        val canonicalPayloadBytes = CanonicalJson.encodeToByteArray(payload)
        val validSig = EcdsaVerifier.verify(
            publicKeyBase64Url = witness.wpk,
            message = canonicalPayloadBytes,
            signatureDerBase64Url = witness.sig
        )

        if (!validSig) {
            return CheckResult(
                id = "C5",
                pass = false,
                value = "Invalid signature",
                limit = "Valid ECDSA P-256 signature",
                reason = "Witness signature verification failed"
            )
        }

        return CheckResult(
            id = "C5",
            pass = true,
            value = witness.witnessShortId,
            limit = "Distinct witness key"
        )
    }
}
