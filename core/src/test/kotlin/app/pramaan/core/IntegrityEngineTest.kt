package app.pramaan.core

import app.pramaan.core.crypto.Base64Url
import app.pramaan.core.crypto.Sha256
import app.pramaan.core.integrity.IntegrityEngine
import app.pramaan.core.manifest.AthleteManifestInfo
import app.pramaan.core.manifest.CanonicalJson
import app.pramaan.core.manifest.FileHashes
import app.pramaan.core.manifest.GyroJsonl
import app.pramaan.core.manifest.JumpAttempt
import app.pramaan.core.manifest.PoseFrameJsonl
import app.pramaan.core.manifest.SessionManifest
import app.pramaan.core.manifest.SessionResult
import app.pramaan.core.manifest.WitnessAttestation
import app.pramaan.core.manifest.WitnessQrPayload
import app.pramaan.core.model.Badge
import app.pramaan.core.model.GyroSample
import app.pramaan.core.model.TestType
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec

class IntegrityEngineTest {

    private fun generateEcKeyPair(): KeyPair {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        return kpg.generateKeyPair()
    }

    private fun signDer(privateKey: java.security.PrivateKey, data: ByteArray): String {
        val sig = Signature.getInstance("SHA256withECDSA")
        sig.initSign(privateKey)
        sig.update(data)
        return Base64Url.encode(sig.sign())
    }

    private fun createFixtureSession(
        dir: File,
        coachKeys: KeyPair,
        slowMotion: Boolean = false,
        shakyCamera: Boolean = false
    ): SessionManifest {
        dir.mkdirs()
        val videoFile = File(dir, "video.mp4")
        videoFile.writeBytes("synthetic-video-bytes-123456789".toByteArray())

        val heightCm = 175.0
        val baseTrack = SyntheticTracks.countermovementJump(
            flightTimeSeconds = 0.500,
            fps = 30,
            heightCm = heightCm,
            noiseStdDev = 0.0
        )
        val track = if (slowMotion) SyntheticTracks.scaleTimestamps(baseTrack, 2.0) else baseTrack
        val framesFile = File(dir, "frames.jsonl")
        framesFile.writer().use { PoseFrameJsonl.writeAll(it, track) }

        val gyroSamples = if (shakyCamera) {
            listOf(
                GyroSample(100, 0.12, 0.15, 0.08),
                GyroSample(200, 0.18, 0.20, 0.14)
            )
        } else {
            listOf(
                GyroSample(100, 0.001, 0.002, 0.001),
                GyroSample(200, 0.002, 0.001, 0.002)
            )
        }
        val gyroFile = File(dir, "gyro.jsonl")
        gyroFile.writer().use { GyroJsonl.writeAll(it, gyroSamples) }

        val videoHash = videoFile.inputStream().use { Sha256.hash(it) }
        val framesHash = framesFile.inputStream().use { Sha256.hash(it) }
        val gyroHash = gyroFile.inputStream().use { Sha256.hash(it) }

        val coachPubKeyB64Url = Base64Url.encode(coachKeys.public.encoded)

        val manifest = SessionManifest(
            v = 1,
            sessionId = "01JA7Q8R4C2D3E4F5G6H7J8K9M",
            test = TestType.VERTICAL_JUMP,
            source = "CAMERA",
            athlete = AthleteManifestInfo("01JA", "M.K.", 16, "F", heightCm),
            devicePublicKey = coachPubKeyB64Url,
            capturedAt = "2026-10-09T12:00:00Z",
            fps = 30,
            hashes = FileHashes(video = videoHash, frames = framesHash, gyro = gyroHash),
            result = SessionResult(
                unit = "cm",
                best = 30.7,
                attempts = listOf(JumpAttempt(30.7, 30.0, if (slowMotion) 2.45 else 9.81, true))
            ),
            checks = emptyList(),
            badge = Badge.VERIFIED
        )

        val manifestBytes = CanonicalJson.encodeToByteArray(manifest)
        File(dir, "manifest.json").writeBytes(manifestBytes)

        val sigB64Url = signDer(coachKeys.private, manifestBytes)
        File(dir, "manifest.sig").writeText(sigB64Url)

        return manifest
    }

    @Test
    fun `genuine session passes all checks and verifies as VERIFIED`(@TempDir tempDir: Path) {
        val coachKeys = generateEcKeyPair()
        val sessionDir = tempDir.toFile()
        createFixtureSession(sessionDir, coachKeys)

        val outcome = IntegrityEngine.verifySessionFolder(sessionDir)

        outcome.badge shouldBe Badge.VERIFIED
        outcome.checks.all { it.pass } shouldBe true
        outcome.checks.find { it.id == "C1" }?.pass shouldBe true
        outcome.checks.find { it.id == "C2" }?.pass shouldBe true
        outcome.checks.find { it.id == "C3" }?.pass shouldBe true
        outcome.checks.find { it.id == "C4" }?.pass shouldBe true
    }

    @Test
    fun `T5 changing one byte of frames file causes C1 to fail and badge becomes TAMPERED`(@TempDir tempDir: Path) {
        val coachKeys = generateEcKeyPair()
        val sessionDir = tempDir.toFile()
        createFixtureSession(sessionDir, coachKeys)

        // Alter frames.jsonl on disk
        val framesFile = File(sessionDir, "frames.jsonl")
        val content = framesFile.readText()
        framesFile.writeText(content + " ")

        val outcome = IntegrityEngine.verifySessionFolder(sessionDir)

        outcome.badge shouldBe Badge.TAMPERED
        val c1 = outcome.checks.find { it.id == "C1" }
        c1?.pass shouldBe false
        c1?.reason?.contains("changed", ignoreCase = true)?.shouldBe(true)
    }

    @Test
    fun `T6 witness signature from same key as device fails C5 and badge stays VERIFIED`(@TempDir tempDir: Path) {
        val coachKeys = generateEcKeyPair()
        val sessionDir = tempDir.toFile()
        val manifest = createFixtureSession(sessionDir, coachKeys)

        val manifestBytes = File(sessionDir, "manifest.json").readBytes()
        val manifestHash = Sha256.hash(manifestBytes)
        val coachPubKeyB64Url = Base64Url.encode(coachKeys.public.encoded)

        // Sign witness attestation using coach's own key
        val witnessPayload = WitnessQrPayload(
            sid = manifest.sessionId,
            d = manifestHash,
            wpk = coachPubKeyB64Url,
            t = 1791364461000L
        )
        val witnessSig = signDer(coachKeys.private, CanonicalJson.encodeToByteArray(witnessPayload))
        val witnessAttestation = WitnessAttestation(
            sid = witnessPayload.sid,
            d = witnessPayload.d,
            wpk = witnessPayload.wpk,
            t = witnessPayload.t,
            sig = witnessSig
        )
        File(sessionDir, "witness.json").writeText(CanonicalJson.encodeToString(witnessAttestation))

        val outcome = IntegrityEngine.verifySessionFolder(sessionDir)

        // Badge must stay VERIFIED, not WITNESSED, because C5 fails on same key
        outcome.badge shouldBe Badge.VERIFIED
        val c5 = outcome.checks.find { it.id == "C5" }
        c5?.pass shouldBe false
        c5?.reason?.contains("same as coach")?.shouldBe(true)
    }

    @Test
    fun `witness signature from distinct device upgrades badge to WITNESSED`(@TempDir tempDir: Path) {
        val coachKeys = generateEcKeyPair()
        val witnessKeys = generateEcKeyPair()
        val sessionDir = tempDir.toFile()
        val manifest = createFixtureSession(sessionDir, coachKeys)

        val manifestBytes = File(sessionDir, "manifest.json").readBytes()
        val manifestHash = Sha256.hash(manifestBytes)
        val witnessPubKeyB64Url = Base64Url.encode(witnessKeys.public.encoded)

        val witnessPayload = WitnessQrPayload(
            sid = manifest.sessionId,
            d = manifestHash,
            wpk = witnessPubKeyB64Url,
            t = 1791364461000L
        )
        val witnessSig = signDer(witnessKeys.private, CanonicalJson.encodeToByteArray(witnessPayload))
        val witnessAttestation = WitnessAttestation(
            sid = witnessPayload.sid,
            d = witnessPayload.d,
            wpk = witnessPayload.wpk,
            t = witnessPayload.t,
            sig = witnessSig
        )
        File(sessionDir, "witness.json").writeText(CanonicalJson.encodeToString(witnessAttestation))

        val outcome = IntegrityEngine.verifySessionFolder(sessionDir)

        outcome.badge shouldBe Badge.WITNESSED
        val c5 = outcome.checks.find { it.id == "C5" }
        c5?.pass shouldBe true
    }

    @Test
    fun `slowed motion session fails C2 and results in FLAGGED badge`(@TempDir tempDir: Path) {
        val coachKeys = generateEcKeyPair()
        val sessionDir = tempDir.toFile()
        createFixtureSession(sessionDir, coachKeys, slowMotion = true)

        val outcome = IntegrityEngine.verifySessionFolder(sessionDir)

        outcome.badge shouldBe Badge.FLAGGED
        val c2 = outcome.checks.find { it.id == "C2" }
        c2?.pass shouldBe false
        c2?.reason?.contains("too slow")?.shouldBe(true)
    }

    @Test
    fun `checkCameraSteady ignores start and stop tap spikes when duration exceeds 2_5s`() {
        val samples = listOf(
            // Tap spike at start (t = 0.0s to 0.5s)
            GyroSample(t = 0L, x = 0.8, y = 0.9, z = 0.5),
            GyroSample(t = 500_000_000L, x = 0.6, y = 0.7, z = 0.4),
            // Steady tripod window (t = 1.0s to 3.0s)
            GyroSample(t = 1_500_000_000L, x = 0.01, y = 0.01, z = 0.01),
            GyroSample(t = 2_000_000_000L, x = 0.01, y = 0.02, z = 0.01),
            GyroSample(t = 2_500_000_000L, x = 0.01, y = 0.01, z = 0.02),
            // Tap spike at stop (t = 3.5s to 4.0s)
            GyroSample(t = 3_500_000_000L, x = 0.7, y = 0.6, z = 0.5),
            GyroSample(t = 4_000_000_000L, x = 0.9, y = 0.8, z = 0.6)
        )

        val result = app.pramaan.core.integrity.Checks.checkCameraSteady(samples)
        result.pass shouldBe true
        (result.value.removeSuffix(" rad/s").toDouble() < 0.12) shouldBe true
    }

    @Test
    fun `checkTwoMethodAgreement respects max 8cm or 30 percent tolerance`() {
        // 25 cm flight vs 31 cm hip rise (diff = 6 cm, which is <= 8.0 cm)
        val result1 = app.pramaan.core.integrity.Checks.checkTwoMethodAgreement(25.0, 31.0)
        result1.pass shouldBe true

        // 40 cm flight vs 50 cm hip rise (diff = 10 cm, which is <= 0.30 * 40 = 12 cm)
        val result2 = app.pramaan.core.integrity.Checks.checkTwoMethodAgreement(40.0, 50.0)
        result2.pass shouldBe true

        // 40 cm flight vs 55 cm hip rise (diff = 15 cm > 12 cm)
        val result3 = app.pramaan.core.integrity.Checks.checkTwoMethodAgreement(40.0, 55.0)
        result3.pass shouldBe false
    }
}
