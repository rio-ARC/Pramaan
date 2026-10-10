package app.pramaan.server

import app.pramaan.core.crypto.Base64Url
import app.pramaan.core.crypto.Sha256
import app.pramaan.core.manifest.AthleteManifestInfo
import app.pramaan.core.manifest.CanonicalJson
import app.pramaan.core.manifest.FileHashes
import app.pramaan.core.manifest.GyroJsonl
import app.pramaan.core.manifest.JumpAttempt
import app.pramaan.core.manifest.PoseFrameJsonl
import app.pramaan.core.manifest.SessionManifest
import app.pramaan.core.manifest.SessionResult
import app.pramaan.core.model.Badge
import app.pramaan.core.model.CheckResult
import app.pramaan.core.model.GyroSample
import app.pramaan.core.model.Landmark
import app.pramaan.core.model.PoseFrame
import app.pramaan.core.model.TestType
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec

class SessionUploadTest {

    @Test
    fun `test genuine session verification and certificate issuance`(@TempDir tempDir: File) {
        val sessionDir = File(tempDir, "session_test_1").apply { mkdirs() }

        // Generate ECDSA P-256 key pair
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        val keyPair = kpg.generateKeyPair()
        val pubKeyB64 = Base64Url.encode(keyPair.public.encoded)

        // Write synthetic video, frames, gyro files
        val videoFile = File(sessionDir, "video.mp4")
        videoFile.writeBytes(ByteArray(2048) { 0x01 })

        val heightCm = 175.0
        val track = SyntheticTracks.countermovementJump(
            flightTimeSeconds = 0.500,
            fps = 30,
            heightCm = heightCm,
            noiseStdDev = 0.0
        )
        val framesFile = File(sessionDir, "frames.jsonl")
        framesFile.writer().use { PoseFrameJsonl.writeAll(it, track) }

        val gyroFile = File(sessionDir, "gyro.jsonl")
        val gyroSamples = listOf(
            GyroSample(t = 100L, x = 0.001, y = 0.002, z = 0.001),
            GyroSample(t = 200L, x = 0.002, y = 0.001, z = 0.002)
        )
        gyroFile.writer().use { GyroJsonl.writeAll(it, gyroSamples) }

        val videoHash = Sha256.hash(videoFile.readBytes())
        val framesHash = Sha256.hash(framesFile.readBytes())
        val gyroHash = Sha256.hash(gyroFile.readBytes())

        val manifest = SessionManifest(
            v = 1,
            sessionId = "session_test_1",
            test = TestType.VERTICAL_JUMP,
            source = "CAMERA",
            athlete = AthleteManifestInfo(
                id = "ATH-01",
                initials = "MK",
                age = 14,
                sex = "F",
                heightCm = heightCm
            ),
            devicePublicKey = pubKeyB64,
            capturedAt = "2026-05-18T09:42:11Z",
            fps = 30,
            hashes = FileHashes(video = videoHash, frames = framesHash, gyro = gyroHash),
            result = SessionResult(
                unit = "cm",
                best = 30.7,
                attempts = listOf(
                    JumpAttempt(flightCm = 30.7, hipRiseCm = 30.0, gravity = 9.81, valid = true)
                )
            ),
            checks = listOf(
                CheckResult(id = "C1", pass = true, value = "0.01", limit = "0.04"),
                CheckResult(id = "C2", pass = true, value = "9.81", limit = "8.0..12.0"),
                CheckResult(id = "C3", pass = true, value = "30.7", limit = "delta < 5.0"),
                CheckResult(id = "C4", pass = true, value = "valid", limit = "valid")
            ),
            badge = Badge.VERIFIED
        )

        val manifestBytes = CanonicalJson.encodeToByteArray(manifest)
        File(sessionDir, "manifest.json").writeBytes(manifestBytes)

        // Sign manifest
        val sig = Signature.getInstance("SHA256withECDSA")
        sig.initSign(keyPair.private)
        sig.update(manifestBytes)
        val sigBytes = sig.sign()
        File(sessionDir, "manifest.sig").writeText(Base64Url.encode(sigBytes))

        // Verify session folder
        val result = SessionVerifier.verifyUploadedSession(sessionDir)
        result.outcome.checks.all { it.pass } shouldBe true
        result.outcome.badge shouldBe Badge.VERIFIED

        // Issue certificate
        val keyFile = File(tempDir, "server_key.json")
        val issuer = ServerCertificateIssuer(keyFile)
        val jws = issuer.issueCertificate(manifest, result.outcome.badge)
        jws.isNotEmpty() shouldBe true

        // Verify certificate
        val claims = issuer.verifyCertificate(jws)
        claims shouldNotBe null
        claims!!.subject shouldBe "ATH-01"
        claims.getClaim("val") shouldBe 30.7
    }

    @Test
    fun `test tampered video session produces TAMPERED badge`(@TempDir tempDir: File) {
        val sessionDir = File(tempDir, "session_test_2").apply { mkdirs() }

        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        val keyPair = kpg.generateKeyPair()
        val pubKeyB64 = Base64Url.encode(keyPair.public.encoded)

        val videoFile = File(sessionDir, "video.mp4")
        videoFile.writeBytes(ByteArray(2048) { 0x01 })

        val framesFile = File(sessionDir, "frames.jsonl")
        framesFile.writeText(PoseFrameJsonl.writeLine(PoseFrame(1L, (0 until 33).map { Landmark(0.5f, 0.5f, 0.9f) })) + "\n")

        val gyroFile = File(sessionDir, "gyro.jsonl")
        gyroFile.writeText(GyroJsonl.writeLine(GyroSample(1L, 0.0, 0.0, 0.0)) + "\n")

        val manifest = SessionManifest(
            v = 1,
            sessionId = "session_test_2",
            test = TestType.VERTICAL_JUMP,
            source = "CAMERA",
            athlete = AthleteManifestInfo("ATH-02", "JD", 16, "M", 170.0),
            devicePublicKey = pubKeyB64,
            capturedAt = "2026-05-18T09:42:11Z",
            fps = 30,
            hashes = FileHashes(
                video = Sha256.hash(videoFile.readBytes()),
                frames = Sha256.hash(framesFile.readBytes()),
                gyro = Sha256.hash(gyroFile.readBytes())
            ),
            result = SessionResult("cm", 35.0),
            checks = emptyList(),
            badge = Badge.VERIFIED
        )

        val manifestBytes = CanonicalJson.encodeToByteArray(manifest)
        File(sessionDir, "manifest.json").writeBytes(manifestBytes)

        val sig = Signature.getInstance("SHA256withECDSA")
        sig.initSign(keyPair.private)
        sig.update(manifestBytes)
        File(sessionDir, "manifest.sig").writeText(Base64Url.encode(sig.sign()))

        // Tamper video file: alter bytes
        videoFile.writeBytes(ByteArray(2048) { 0xFF.toByte() })

        // Verify session folder
        val result = SessionVerifier.verifyUploadedSession(sessionDir)
        result.outcome.badge shouldBe Badge.TAMPERED
    }

    @Test
    fun `test dashboard pairing qr generation`() {
        val qrBase64 = DashboardPages.generateQrBase64("http://192.168.1.50:8080", 160)
        qrBase64.isNotEmpty() shouldBe true
    }
}

