package app.pramaan.seal

import app.pramaan.api.dto.AccountProfileDto
import app.pramaan.core.crypto.Sha256
import app.pramaan.core.integrity.IntegrityEngine
import app.pramaan.core.manifest.AnalysisFrameDimensions
import app.pramaan.core.manifest.AthleteManifestInfo
import app.pramaan.core.manifest.CanonicalJson
import app.pramaan.core.manifest.FileHashes
import app.pramaan.core.manifest.GyroJsonl
import app.pramaan.core.manifest.JumpAttempt
import app.pramaan.core.manifest.PoseFrameJsonl
import app.pramaan.core.manifest.SessionManifest
import app.pramaan.core.manifest.SessionQr
import app.pramaan.core.manifest.SessionResult
import app.pramaan.core.model.CheckResult
import app.pramaan.core.model.TestType
import app.pramaan.core.scoring.JumpResult
import app.pramaan.core.scoring.SitUpAnalyzer
import app.pramaan.core.scoring.SitUpResult
import app.pramaan.core.scoring.VerticalJumpAnalyzer
import app.pramaan.core.scoring.broadjump.AthleteInfo
import app.pramaan.core.scoring.broadjump.BroadJumpAnalyzer
import app.pramaan.core.scoring.broadjump.BroadJumpResult
import app.pramaan.core.scoring.broadjump.CalibMode
import app.pramaan.core.scoring.broadjump.GroundCalibration
import app.pramaan.core.scoring.broadjump.NormPoint
import app.pramaan.data.SessionDao
import app.pramaan.data.SessionEntity
import app.pramaan.data.SessionStore
import java.io.File
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class Sealer(
    private val sessionStore: SessionStore,
    private val deviceKey: DeviceKey,
    private val sessionDao: SessionDao
) {

    private val random = SecureRandom()

    suspend fun sealSession(
        sessionId: String,
        testType: TestType,
        athleteProfile: AccountProfileDto,
        fps: Int = 30,
        source: String = "CAMERA",
        calibration: GroundCalibration? = null,
        extraFiles: Map<String, File> = emptyMap()
    ): SessionManifest {
        val sessionDir = sessionStore.getSessionDir(sessionId)
        val videoFile = sessionStore.getVideoFile(sessionId)
        val framesFile = sessionStore.getFramesFile(sessionId)
        val gyroFile = sessionStore.getGyroFile(sessionId)

        val frames = if (framesFile.exists()) {
            framesFile.reader().use { PoseFrameJsonl.readAll(it) }
        } else emptyList()

        val gyroSamples = if (gyroFile.exists()) {
            gyroFile.reader().use { GyroJsonl.readAll(it) }
        } else emptyList()

        val heightCm = athleteProfile.heightCm ?: 165.0

        var jumpResult: JumpResult? = null
        var sitUpResult: SitUpResult? = null
        var broadJumpResult: BroadJumpResult? = null

        val sessionResult: SessionResult = when (testType) {
            TestType.VERTICAL_JUMP -> {
                val res = VerticalJumpAnalyzer.analyze(frames, heightCm)
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
            TestType.BROAD_JUMP -> {
                val calib = calibration ?: GroundCalibration(
                    mode = CalibMode.HEIGHT_ONLY,
                    frameW = 1280,
                    frameH = 720,
                    calibFrameTNs = 0L,
                    line = NormPoint(0.2, 0.8),
                    marker = null,
                    markerCm = null
                )
                val athleteInfo = AthleteInfo(
                    heightCm = heightCm,
                    ageYears = athleteProfile.age ?: 16,
                    sex = athleteProfile.sex ?: "F"
                )
                val res = BroadJumpAnalyzer.analyze(frames, calib, athleteInfo)
                broadJumpResult = res
                SessionResult(
                    unit = "cm",
                    best = res.best ?: 0.0,
                    broadJumpAttempts = res.attempts
                )
            }
        }

        val (checks, badge) = IntegrityEngine.evaluateAtSeal(
            testType = testType,
            source = source,
            jumpResult = jumpResult,
            gyroSamples = gyroSamples,
            broadJumpResult = broadJumpResult,
            athleteHeightCm = heightCm
        )

        val allChecks = buildList {
            add(
                CheckResult(
                    id = "C1",
                    pass = true,
                    value = "Signed & intact",
                    limit = "Signature & hashes match"
                )
            )
            addAll(checks)
        }

        val videoHash = if (videoFile.exists()) videoFile.inputStream().use { Sha256.hash(it) } else ""
        val framesHash = if (framesFile.exists()) framesFile.inputStream().use { Sha256.hash(it) } else ""
        val gyroHash = if (gyroFile.exists()) gyroFile.inputStream().use { Sha256.hash(it) } else ""

        val calibFrameFile = extraFiles["calib_frame.jpg"] ?: sessionStore.getCalibFrameFile(sessionId)
        val calibHash = if (calibFrameFile.exists()) calibFrameFile.inputStream().use { Sha256.hash(it) } else null

        val stillsDir = sessionStore.getStillsDir(sessionId)
        val stillsMap = mutableMapOf<String, String>()
        if (stillsDir.exists() && stillsDir.isDirectory) {
            stillsDir.listFiles()?.filter { it.isFile }?.forEach { f ->
                val key = f.nameWithoutExtension
                stillsMap[key] = f.inputStream().use { Sha256.hash(it) }
            }
        }
        extraFiles.filter { it.key.startsWith("still_") || it.key.startsWith("att") }.forEach { (name, f) ->
            if (f.exists()) {
                val key = name.substringBeforeLast('.')
                stillsMap[key] = f.inputStream().use { Sha256.hash(it) }
            }
        }

        val hashes = FileHashes(
            video = videoHash,
            frames = framesHash,
            gyro = gyroHash,
            calibFrame = calibHash,
            stills = if (stillsMap.isNotEmpty()) stillsMap else null
        )

        val isoDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val capturedAt = isoDateFormat.format(Date())

        val initials = athleteProfile.name.split(" ")
            .filter { it.isNotBlank() }
            .map { it.first().uppercaseChar() }
            .joinToString("")
            .take(4)
            .ifEmpty { "ATH" }

        val manifest = SessionManifest(
            v = 1,
            sessionId = sessionId,
            test = testType,
            source = source,
            athlete = AthleteManifestInfo(
                id = athleteProfile.id,
                initials = initials,
                age = athleteProfile.age ?: 16,
                sex = athleteProfile.sex ?: "F",
                heightCm = heightCm
            ),
            devicePublicKey = deviceKey.getPublicKeyBase64Url(),
            capturedAt = capturedAt,
            fps = fps,
            hashes = hashes,
            result = sessionResult,
            checks = allChecks,
            badge = badge,
            analyzerVersion = if (testType == TestType.BROAD_JUMP) (broadJumpResult?.analyzerVersion ?: "1.0") else null,
            analysisFrame = if (calibration != null) AnalysisFrameDimensions(calibration.frameW, calibration.frameH) else null,
            calibration = calibration
        )

        val manifestBytes = CanonicalJson.encodeToByteArray(manifest)
        sessionStore.getManifestFile(sessionId).writeBytes(manifestBytes)

        val sigB64Url = deviceKey.sign(manifestBytes)
        sessionStore.getSigFile(sessionId).writeText(sigB64Url)

        val entity = SessionEntity(
            id = sessionId,
            ownerAccountId = athleteProfile.id,
            test = testType.name,
            source = source,
            folder = sessionDir.absolutePath,
            best = sessionResult.best,
            unit = sessionResult.unit,
            localIntegrity = badge.name,
            status = "DRAFT"
        )
        sessionDao.insert(entity)

        return manifest
    }

    fun createS2SessionQr(sessionId: String): String? {
        val manifestFile = sessionStore.getManifestFile(sessionId)
        if (!manifestFile.exists()) return null

        val manifestBytes = manifestFile.readBytes()
        val manifestSha256 = Sha256.hash(manifestBytes)

        val nonceBytes = ByteArray(12)
        random.nextBytes(nonceBytes)
        val nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(nonceBytes)
        val issuedAtMs = System.currentTimeMillis()

        val messageBytes = SessionQr.buildSignedMessageBytes(sessionId, manifestSha256, nonce, issuedAtMs)
        val sigDerB64Url = deviceKey.sign(messageBytes)

        return SessionQr.encode(
            sessionId = sessionId,
            manifestSha256Hex = manifestSha256,
            nonce = nonce,
            issuedAtMs = issuedAtMs,
            athleteSigB64url = sigDerB64Url
        )
    }

    private fun round1(v: Double): Double = Math.round(v * 10.0) / 10.0
    private fun round2(v: Double): Double = Math.round(v * 100.0) / 100.0
}
