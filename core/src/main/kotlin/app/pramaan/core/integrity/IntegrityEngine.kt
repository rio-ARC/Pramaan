package app.pramaan.core.integrity

import app.pramaan.core.crypto.Sha256
import app.pramaan.core.manifest.FileHashes
import app.pramaan.core.manifest.GyroJsonl
import app.pramaan.core.manifest.PoseFrameJsonl
import app.pramaan.core.manifest.SessionManifest
import app.pramaan.core.manifest.WitnessAttestation
import app.pramaan.core.model.Badge
import app.pramaan.core.model.CheckResult
import app.pramaan.core.model.GyroSample
import app.pramaan.core.model.TestType
import app.pramaan.core.scoring.JumpResult
import app.pramaan.core.scoring.SitUpAnalyzer
import app.pramaan.core.scoring.SitUpResult
import app.pramaan.core.scoring.VerticalJumpAnalyzer
import app.pramaan.core.scoring.broadjump.AthleteInfo
import app.pramaan.core.scoring.broadjump.BroadJumpAnalyzer
import app.pramaan.core.scoring.broadjump.BroadJumpResult
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream

data class VerificationOutcome(
    val manifest: SessionManifest?,
    val checks: List<CheckResult>,
    val badge: Badge,
    val jumpResult: JumpResult? = null,
    val sitUpResult: SitUpResult? = null,
    val broadJumpResult: BroadJumpResult? = null
)

/**
 * Shared integrity evaluation engine (PRD 10.4).
 * Used identically by both :app (LocalVerifier) and :server (SessionVerifier).
 */
object IntegrityEngine {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Decides the single official Badge based on source, checks, and witness status.
     * PRD 5.1 rules:
     * - Tampered: Signature or any hash does not match (C1 fails)
     * - Flagged: Gravity (C2), agreement (C3), steadiness (C4), or SBJ calibration (C5) fails
     * - Unsealed: Imported video, physics checks pass
     * - Witnessed: Sealed on this device, all checks pass, valid witness co-sign (C5 passes)
     * - Verified: Sealed on this device, all checks pass, no witness
     */
    fun decideBadge(
        source: String,
        checks: List<CheckResult>,
        witnessValid: Boolean
    ): Badge {
        val c1 = checks.find { it.id == "C1" }
        if (c1 != null && !c1.pass) {
            return Badge.TAMPERED
        }

        val physicsFailed = checks.any { check ->
            if (check.id in listOf("C2", "C3", "C4")) {
                !check.pass
            } else if (check.id == "C5" && (check.reason == "CALIB_APPROX" || check.reason == "HEIGHT_MISMATCH" || check.limit.contains("MARKER") || check.limit.contains("Diff") || check.limit.contains("Height") || check.limit.contains("tilt") || check.limit.contains("scale"))) {
                !check.pass
            } else {
                false
            }
        }
        if (physicsFailed) {
            return Badge.FLAGGED
        }

        if (source.equals("IMPORT", ignoreCase = true)) {
            return Badge.UNSEALED
        }

        if (witnessValid) {
            return Badge.WITNESSED
        }

        return Badge.VERIFIED
    }

    /**
     * Evaluates initial physics checks at sealing time (before witness co-sign).
     */
    fun evaluateAtSeal(
        testType: TestType,
        source: String,
        jumpResult: JumpResult?,
        gyroSamples: List<GyroSample>,
        broadJumpResult: BroadJumpResult? = null,
        athleteHeightCm: Double = 165.0
    ): Pair<List<CheckResult>, Badge> {
        val checks = mutableListOf<CheckResult>()
        val isImported = source.equals("IMPORT", ignoreCase = true)

        if (testType == TestType.VERTICAL_JUMP && jumpResult != null) {
            val gravity = jumpResult.bestGravity
            checks.add(Checks.checkGravity(gravity))

            val bestAttempt = jumpResult.bestAttempt
            val flightCm = bestAttempt?.flightCm ?: jumpResult.bestFlightCm
            val hipRiseCm = bestAttempt?.hipRiseCm ?: jumpResult.bestHipRiseCm
            checks.add(Checks.checkTwoMethodAgreement(flightCm, hipRiseCm))
        } else if (testType == TestType.BROAD_JUMP && broadJumpResult != null) {
            checks.add(BroadJumpChecks.checkGravity(broadJumpResult))
            checks.add(BroadJumpChecks.checkHorizontalConsistency(broadJumpResult, athleteHeightCm))
            checks.add(BroadJumpChecks.checkCalibrationAgreement(broadJumpResult))
        }

        checks.add(Checks.checkCameraSteady(gyroSamples, isImported = isImported))

        val badge = decideBadge(
            source = source,
            checks = checks,
            witnessValid = false
        )

        return checks to badge
    }

    /**
     * Re-verifies a complete session folder from disk.
     */
    fun verifySessionFolder(folder: File): VerificationOutcome {
        val manifestFile = File(folder, "manifest.json")
        val sigFile = File(folder, "manifest.sig")
        val videoFile = File(folder, "video.mp4")
        val framesFile = File(folder, "frames.jsonl")
        val gyroFile = File(folder, "gyro.jsonl")
        val witnessFile = File(folder, "witness.json")
        val calibFrameFile = File(folder, "calib_frame.jpg")
        val stillsDir = File(folder, "stills")

        if (!manifestFile.exists()) {
            val check = CheckResult(
                id = "C1",
                pass = false,
                value = "Missing manifest",
                limit = "manifest.json present",
                reason = "manifest.json not found in session folder"
            )
            return VerificationOutcome(null, listOf(check), Badge.TAMPERED)
        }

        val manifestBytes = manifestFile.readBytes()
        val manifest = try {
            json.decodeFromString<SessionManifest>(manifestBytes.toString(Charsets.UTF_8))
        } catch (_: Exception) {
            val check = CheckResult(
                id = "C1",
                pass = false,
                value = "Corrupt manifest",
                limit = "Valid manifest JSON",
                reason = "Could not parse manifest.json"
            )
            return VerificationOutcome(null, listOf(check), Badge.TAMPERED)
        }

        val sigString = if (sigFile.exists()) sigFile.readText().trim() else ""

        // Compute actual hashes of files on disk
        val actualVideoHash = if (videoFile.exists()) {
            FileInputStream(videoFile).use { Sha256.hash(it) }
        } else ""

        val actualFramesHash = if (framesFile.exists()) {
            FileInputStream(framesFile).use { Sha256.hash(it) }
        } else ""

        val actualGyroHash = if (gyroFile.exists()) {
            FileInputStream(gyroFile).use { Sha256.hash(it) }
        } else ""

        val actualCalibHash = if (calibFrameFile.exists()) {
            FileInputStream(calibFrameFile).use { Sha256.hash(it) }
        } else null

        val actualStillsMap = if (stillsDir.exists() && stillsDir.isDirectory) {
            val map = mutableMapOf<String, String>()
            stillsDir.listFiles()?.filter { it.isFile }?.forEach { f ->
                val key = f.nameWithoutExtension
                map[key] = FileInputStream(f).use { Sha256.hash(it) }
            }
            if (map.isNotEmpty()) map else null
        } else null

        val actualHashes = FileHashes(
            video = actualVideoHash,
            frames = actualFramesHash,
            gyro = actualGyroHash,
            calibFrame = actualCalibHash,
            stills = actualStillsMap
        )

        // C1: Tamper check
        val c1 = Checks.checkTamper(
            manifestBytes = manifestBytes,
            signatureDerBase64Url = sigString,
            devicePublicKeyBase64Url = manifest.devicePublicKey,
            actualHashes = actualHashes,
            manifestHashes = manifest.hashes
        )

        val checks = mutableListOf<CheckResult>()
        checks.add(c1)

        // Re-score frames from disk to verify physics
        val frames = if (framesFile.exists()) {
            framesFile.reader().use { PoseFrameJsonl.readAll(it) }
        } else emptyList()

        val gyroSamples = if (gyroFile.exists()) {
            gyroFile.reader().use { GyroJsonl.readAll(it) }
        } else emptyList()

        var jumpResult: JumpResult? = null
        var sitUpResult: SitUpResult? = null
        var broadJumpResult: BroadJumpResult? = null

        val isImported = manifest.source.equals("IMPORT", ignoreCase = true)

        if (manifest.test == TestType.VERTICAL_JUMP) {
            val rescoredJump = VerticalJumpAnalyzer.analyze(frames, manifest.athlete.heightCm)
            jumpResult = rescoredJump

            val c2 = Checks.checkGravity(rescoredJump.bestGravity)
            checks.add(c2)

            val bestAttempt = rescoredJump.bestAttempt
            val flightCm = bestAttempt?.flightCm ?: rescoredJump.bestFlightCm
            val hipRiseCm = bestAttempt?.hipRiseCm ?: rescoredJump.bestHipRiseCm
            val c3 = Checks.checkTwoMethodAgreement(flightCm, hipRiseCm)
            checks.add(c3)
        } else if (manifest.test == TestType.SIT_UPS) {
            sitUpResult = SitUpAnalyzer.analyze(frames)
        } else if (manifest.test == TestType.BROAD_JUMP && manifest.calibration != null) {
            val rescoredBj = BroadJumpAnalyzer.analyze(
                track = frames,
                calib = manifest.calibration,
                athlete = AthleteInfo(
                    heightCm = manifest.athlete.heightCm,
                    ageYears = manifest.athlete.age,
                    sex = manifest.athlete.sex
                )
            )
            broadJumpResult = rescoredBj

            checks.add(BroadJumpChecks.checkGravity(rescoredBj))
            checks.add(BroadJumpChecks.checkHorizontalConsistency(rescoredBj, manifest.athlete.heightCm))
            checks.add(BroadJumpChecks.checkCalibrationAgreement(rescoredBj))
        }

        val c4 = Checks.checkCameraSteady(gyroSamples, isImported = isImported)
        checks.add(c4)

        // C5: Witness check (for non-broad jump tests or additional witness attestation)
        var witnessValid = false
        if (witnessFile.exists()) {
            val witness = try {
                json.decodeFromString<WitnessAttestation>(witnessFile.readText())
            } catch (_: Exception) {
                null
            }
            val manifestSha256 = Sha256.hash(manifestBytes)
            val c5 = Checks.checkWitness(
                witness = witness,
                manifestSha256 = manifestSha256,
                devicePublicKeyBase64Url = manifest.devicePublicKey
            )
            checks.add(c5)
            witnessValid = c5.pass
        }

        val badge = decideBadge(
            source = manifest.source,
            checks = checks,
            witnessValid = witnessValid
        )

        return VerificationOutcome(
            manifest = manifest,
            checks = checks,
            badge = badge,
            jumpResult = jumpResult,
            sitUpResult = sitUpResult,
            broadJumpResult = broadJumpResult
        )
    }
}
