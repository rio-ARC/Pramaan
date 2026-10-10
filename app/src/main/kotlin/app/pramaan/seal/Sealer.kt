package app.pramaan.seal

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
import app.pramaan.core.model.TestType
import app.pramaan.core.scoring.JumpResult
import app.pramaan.core.scoring.SitUpAnalyzer
import app.pramaan.core.scoring.SitUpResult
import app.pramaan.core.scoring.VerticalJumpAnalyzer
import app.pramaan.data.AthleteEntity
import app.pramaan.data.SessionDao
import app.pramaan.data.SessionEntity
import app.pramaan.data.SessionStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class Sealer(
    private val sessionStore: SessionStore,
    private val deviceKey: DeviceKey,
    private val sessionDao: SessionDao
) {

    /**
     * Seals a recorded session immediately upon recording completion (PRD 10.5).
     */
    suspend fun sealSession(
        sessionId: String,
        testType: TestType,
        athlete: AthleteEntity,
        fps: Int = 30,
        source: String = "CAMERA"
    ): SessionManifest {
        val sessionDir = sessionStore.getSessionDir(sessionId)
        val videoFile = sessionStore.getVideoFile(sessionId)
        val framesFile = sessionStore.getFramesFile(sessionId)
        val gyroFile = sessionStore.getGyroFile(sessionId)

        // Read frames and gyro from disk for deterministic sealing
        val frames = if (framesFile.exists()) {
            framesFile.reader().use { PoseFrameJsonl.readAll(it) }
        } else emptyList()

        val gyroSamples = if (gyroFile.exists()) {
            gyroFile.reader().use { GyroJsonl.readAll(it) }
        } else emptyList()

        // Batch re-score from saved disk frames (Gap Fix 3)
        var jumpResult: JumpResult? = null
        var sitUpResult: SitUpResult? = null

        val sessionResult: SessionResult = when (testType) {
            TestType.VERTICAL_JUMP -> {
                val res = VerticalJumpAnalyzer.analyze(frames, athlete.heightCm)
                jumpResult = res
                val attempts = res.attempts.map { att ->
                    JumpAttempt(
                        flightCm = round1(att.flightCm),
                        hipRiseCm = round1(att.hipRiseCm),
                        gravity = round2(att.gravity),
                        valid = att.valid
                    )
                }
                SessionResult(
                    unit = "cm",
                    best = round1(res.bestFlightCm),
                    attempts = attempts
                )
            }
            TestType.SIT_UPS -> {
                val res = SitUpAnalyzer.analyze(frames)
                sitUpResult = res
                SessionResult(
                    unit = "reps",
                    best = res.totalReps.toDouble(),
                    rejected = res.rejectedReps
                )
            }
        }

        // Evaluate physics checks & compute badge
        val (checks, badge) = IntegrityEngine.evaluateAtSeal(
            testType = testType,
            source = source,
            jumpResult = jumpResult,
            gyroSamples = gyroSamples
        )

        // Hash video, frames, gyro files
        val videoHash = if (videoFile.exists()) videoFile.inputStream().use { Sha256.hash(it) } else ""
        val framesHash = if (framesFile.exists()) framesFile.inputStream().use { Sha256.hash(it) } else ""
        val gyroHash = if (gyroFile.exists()) gyroFile.inputStream().use { Sha256.hash(it) } else ""
        val hashes = FileHashes(video = videoHash, frames = framesHash, gyro = gyroHash)

        val isoDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val capturedAt = isoDateFormat.format(Date())

        val manifest = SessionManifest(
            v = 1,
            sessionId = sessionId,
            test = testType,
            source = source,
            athlete = AthleteManifestInfo(
                id = athlete.id,
                initials = athlete.initials,
                age = athlete.age,
                sex = athlete.sex,
                heightCm = athlete.heightCm
            ),
            devicePublicKey = deviceKey.getPublicKeyBase64Url(),
            capturedAt = capturedAt,
            fps = fps,
            hashes = hashes,
            result = sessionResult,
            checks = checks,
            badge = badge
        )

        // Encode canonically and sign with Android Keystore hardware key
        val manifestBytes = CanonicalJson.encodeToByteArray(manifest)
        sessionStore.getManifestFile(sessionId).writeBytes(manifestBytes)

        val sigB64Url = deviceKey.sign(manifestBytes)
        sessionStore.getSigFile(sessionId).writeText(sigB64Url)

        // Save index to Room DB
        val entity = SessionEntity(
            id = sessionId,
            athleteId = athlete.id,
            test = testType.name,
            source = source,
            folder = sessionDir.absolutePath,
            best = sessionResult.best,
            unit = sessionResult.unit,
            badge = badge.name,
            synced = false
        )
        sessionDao.insert(entity)

        return manifest
    }

    fun createSessionQr(sessionId: String): String? {
        val manifestFile = sessionStore.getManifestFile(sessionId)
        if (!manifestFile.exists()) return null

        val manifest = CanonicalJson.decodeFromByteArray(
            SessionManifest.serializer(),
            manifestFile.readBytes()
        )
        val manifestDigest = Sha256.hash(manifestFile.readBytes())

        val payload = app.pramaan.core.manifest.SessionQrPayload(
            sid = manifest.sessionId,
            d = manifestDigest,
            test = manifest.test.name,
            best = manifest.result.best,
            unit = manifest.result.unit,
            ini = manifest.athlete.initials,
            cpk = deviceKey.getPublicKeyBase64Url()
        )

        val payloadBytes = CanonicalJson.encodeToByteArray(payload)
        val payloadB64Url = app.pramaan.core.crypto.Base64Url.encode(payloadBytes)
        val sigDer = deviceKey.sign(payloadBytes)
        // Convert to raw P1363 for compact QR if needed, or keep DER
        val rawSig = app.pramaan.core.crypto.DerRaw.derToRaw(app.pramaan.core.crypto.Base64Url.decode(sigDer))
        val sigB64Url = app.pramaan.core.crypto.Base64Url.encode(rawSig)

        return app.pramaan.core.manifest.WitnessQrCodec.encode('S', payloadB64Url, sigB64Url)
    }

    private fun round1(v: Double): Double = Math.round(v * 10.0) / 10.0
    private fun round2(v: Double): Double = Math.round(v * 100.0) / 100.0
}
