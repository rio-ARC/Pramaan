package app.pramaan.server

import app.pramaan.core.integrity.BroadJumpChecks
import app.pramaan.core.integrity.Checks
import app.pramaan.core.integrity.IntegrityEngine
import app.pramaan.core.integrity.VerificationOutcome
import app.pramaan.core.manifest.GyroJsonl
import app.pramaan.core.manifest.PoseFrameJsonl
import app.pramaan.core.manifest.SessionManifest
import app.pramaan.core.model.Badge
import app.pramaan.core.model.CheckResult
import app.pramaan.core.scoring.broadjump.AthleteInfo
import app.pramaan.core.scoring.broadjump.BroadJumpAnalyzer
import app.pramaan.core.scoring.broadjump.BroadJumpParams
import app.pramaan.core.scoring.broadjump.BroadJumpResult
import app.pramaan.core.scoring.broadjump.CalibMode
import app.pramaan.core.scoring.broadjump.GroundCalibration
import app.pramaan.core.scoring.broadjump.NormPoint
import java.io.File
import kotlin.math.abs

object BroadJumpRescorer {

    data class RescoreResult(
        val outcome: VerificationOutcome,
        val serverResult: BroadJumpResult?
    )

    fun rescore(
        sessionFolder: File,
        manifest: SessionManifest,
        account: AccountRecord? = null
    ): RescoreResult {
        // 1. Initial disk integrity check (C1)
        val diskOutcome = IntegrityEngine.verifySessionFolder(sessionFolder)
        val c1Check = diskOutcome.checks.find { it.id == "C1" }
        if (c1Check != null && !c1Check.pass) {
            return RescoreResult(diskOutcome, null)
        }

        val allChecks = mutableListOf<CheckResult>()
        if (c1Check != null) {
            allChecks.add(c1Check)
        }

        // Account-registered height vs manifest athlete height (Rule 1: Height Integrity)
        val accountHeight = account?.heightCm ?: manifest.athlete.heightCm
        val heightDiff = abs(manifest.athlete.heightCm - accountHeight)
        if (heightDiff > 0.5) {
            allChecks.add(
                CheckResult(
                    id = "C5",
                    pass = false,
                    value = "${manifest.athlete.heightCm} cm",
                    limit = "$accountHeight cm ± 0.5",
                    reason = "HEIGHT_MISMATCH"
                )
            )
        }

        // Rule 2: Mode Integrity (HEIGHT_ONLY is FLAGGED CALIB_APPROX)
        val calibInManifest = manifest.calibration
        if (calibInManifest == null || calibInManifest.mode == CalibMode.HEIGHT_ONLY) {
            allChecks.add(
                CheckResult(
                    id = "C5",
                    pass = false,
                    value = calibInManifest?.mode?.name ?: "MISSING",
                    limit = "MARKER mode required",
                    reason = "CALIB_APPROX"
                )
            )
        }

        // Check analyzer version (§10.3)
        val knownVersions = setOf("bj-1.0.0", "1.0")
        if (manifest.analyzerVersion == null || manifest.analyzerVersion !in knownVersions) {
            allChecks.add(
                CheckResult(
                    id = "C3",
                    pass = false,
                    value = manifest.analyzerVersion ?: "null",
                    limit = "bj-1.0.0",
                    reason = "ANALYZER_VERSION_UNKNOWN"
                )
            )
        }

        // 2. Load pose frames from frames.jsonl
        val framesFile = File(sessionFolder, "frames.jsonl")
        val frames = if (framesFile.exists()) {
            framesFile.reader().use { PoseFrameJsonl.readAll(it) }
        } else emptyList()

        val calib = manifest.calibration ?: GroundCalibration(
            mode = CalibMode.HEIGHT_ONLY,
            frameW = 1280,
            frameH = 720,
            calibFrameTNs = 0L,
            line = NormPoint(0.25, 0.8)
        )

        val athleteInfo = AthleteInfo(
            heightCm = accountHeight,
            ageYears = manifest.athlete.age,
            sex = account?.sex ?: manifest.athlete.sex
        )

        // 3. Re-score session using server execution
        val serverResult = BroadJumpAnalyzer.analyze(
            track = frames,
            calib = calib,
            athlete = athleteInfo,
            params = BroadJumpParams(analyzerVersion = manifest.analyzerVersion ?: "1.0")
        )

        // Rule 3: Test Gating
        if (serverResult.attempts.isEmpty() || serverResult.best == null) {
            allChecks.add(
                CheckResult(
                    id = "C3",
                    pass = false,
                    value = "0 valid jumps",
                    limit = "≥ 1 valid attempt",
                    reason = "NO_VALID_ATTEMPT"
                )
            )
        }

        // Rescore match check (§10.4)
        var rescoreMatch = true
        var mismatchDetail = ""

        val serverBest = serverResult.best ?: 0.0
        val manifestBest = manifest.result.best
        if (abs(serverBest - manifestBest) > 0.5) {
            rescoreMatch = false
            mismatchDetail = "Best dist diff ${abs(serverBest - manifestBest)} > 0.5cm"
        }

        val manifestAttempts = manifest.result.broadJumpAttempts ?: emptyList()
        if (manifestAttempts.size != serverResult.attempts.size) {
            rescoreMatch = false
            mismatchDetail = "Attempt count mismatch: manifest=${manifestAttempts.size}, server=${serverResult.attempts.size}"
        } else {
            for (i in manifestAttempts.indices) {
                val mAtt = manifestAttempts[i]
                val sAtt = serverResult.attempts[i]
                if (abs(mAtt.distanceCm - sAtt.distanceCm) > 0.5) {
                    rescoreMatch = false
                    mismatchDetail = "Att $i dist diff: manifest=${mAtt.distanceCm}, server=${sAtt.distanceCm}"
                    break
                }
                if (mAtt.valid != sAtt.valid || mAtt.fouls.toSet() != sAtt.fouls.toSet()) {
                    rescoreMatch = false
                    mismatchDetail = "Att $i foul mismatch: manifest=${mAtt.fouls}, server=${sAtt.fouls}"
                    break
                }
            }
        }

        if (!rescoreMatch) {
            allChecks.add(
                CheckResult(
                    id = "C3",
                    pass = false,
                    value = mismatchDetail,
                    limit = "Rescore delta ≤ 0.5 cm, fouls match",
                    reason = "SERVER_RESCORE_MISMATCH"
                )
            )
        }

        // 4. Server physics checks on serverResult
        allChecks.add(BroadJumpChecks.checkGravity(serverResult))
        allChecks.add(BroadJumpChecks.checkHorizontalConsistency(serverResult, accountHeight))
        allChecks.add(BroadJumpChecks.checkCalibrationAgreement(serverResult))

        // Gyro camera steadiness
        val gyroFile = File(sessionFolder, "gyro.jsonl")
        val gyroSamples = if (gyroFile.exists()) {
            gyroFile.reader().use { GyroJsonl.readAll(it) }
        } else emptyList()
        val isImported = manifest.source.equals("IMPORT", ignoreCase = true)
        allChecks.add(Checks.checkCameraSteady(gyroSamples, isImported = isImported))

        val badge = IntegrityEngine.decideBadge(
            source = manifest.source,
            checks = allChecks,
            witnessValid = false
        )

        return RescoreResult(
            outcome = VerificationOutcome(manifest, allChecks, badge),
            serverResult = serverResult
        )
    }
}
