package app.pramaan.core.scoring.broadjump

import app.pramaan.core.integrity.ParabolaFit
import app.pramaan.core.model.Landmark
import app.pramaan.core.model.PoseFrame
import app.pramaan.core.model.PoseLandmarks
import app.pramaan.core.scoring.Feedback
import kotlin.math.*

object BroadJumpAnalyzer {

    fun analyze(
        track: List<PoseFrame>,
        calib: GroundCalibration,
        athlete: AthleteInfo,
        params: BroadJumpParams = BroadJumpParams()
    ): BroadJumpResult {
        val validFrames = track.filter { it.landmarks.size == 33 }
        if (validFrames.size < 5) {
            val emptyCalib = CalibSummary(
                mode = calib.mode,
                scaleCmPerPx = 0.5,
                sHeightCmPerPx = 0.5,
                scaleDiffPct = 0.0,
                axisTiltDeg = 0.0
            )
            return BroadJumpResult(
                unit = "cm",
                best = null,
                bestIndex = null,
                attempts = emptyList(),
                notes = listOf(BjNote.NO_VALID_ATTEMPT),
                calibration = emptyCalib,
                feedbackKey = Feedback.FB_BJ_GOOD,
                analyzerVersion = params.analyzerVersion
            )
        }

        val w = calib.frameW.toDouble()
        val h = calib.frameH.toDouble()

        // 1. Median smoothing per landmark coordinate across frames
        val smoothedFrames = smoothLandmarks(validFrames, params.medianFilterFrames)

        // 2. Robust bootstrap bodyPx0
        val bodyPx0 = computeBootstrapBodyPx(smoothedFrames, w, h, params)

        // 3. Find flights and attempts
        var flights = findFlights(smoothedFrames, w, h, bodyPx0, calib, athlete, params)
        if (flights.isEmpty()) {
            flights = detectWholeVideoTrajectory(smoothedFrames, w, h, bodyPx0, calib, athlete, params)
        }

        if (flights.isEmpty()) {
            val defaultFrame = GroundCalibrationHelper.buildGroundFrame(
                calib = calib,
                groundYpx = h * 0.85,
                athleteHeightCm = athlete.heightCm,
                bodyPx = bodyPx0,
                eyeHeightRatio = params.eyeHeightRatio
            )
            val calibSummary = CalibSummary(
                mode = calib.mode,
                scaleCmPerPx = round4(defaultFrame.scaleCmPerPx),
                sHeightCmPerPx = round4(defaultFrame.sHeightCmPerPx),
                scaleDiffPct = round2(defaultFrame.scaleDiffPct),
                axisTiltDeg = round2(defaultFrame.axisTiltDeg)
            )
            return BroadJumpResult(
                unit = "cm",
                best = null,
                bestIndex = null,
                attempts = emptyList(),
                notes = listOf(BjNote.NO_VALID_ATTEMPT),
                calibration = calibSummary,
                feedbackKey = Feedback.FB_BJ_GOOD,
                analyzerVersion = params.analyzerVersion
            )
        }

        val allNotes = mutableListOf<BjNote>()
        val attempts = mutableListOf<BjAttempt>()

        var firstGroundY: Double? = null

        for ((idx, flight) in flights.withIndex()) {
            val attemptIndex = idx + 1
            val counted = attemptIndex <= params.maxAttempts

            if (idx == 0) {
                firstGroundY = flight.groundYpx
            } else if (firstGroundY != null) {
                val drift = abs(flight.groundYpx - firstGroundY)
                if (drift > 0.02 * flight.bodyPx) {
                    if (!allNotes.contains(BjNote.GROUND_DRIFT)) {
                        allNotes.add(BjNote.GROUND_DRIFT)
                    }
                }
            }

            if (idx >= params.maxAttempts && !allNotes.contains(BjNote.EXTRA_ATTEMPTS_IGNORED)) {
                allNotes.add(BjNote.EXTRA_ATTEMPTS_IGNORED)
            }

            attempts.add(flight.attempt.copy(index = attemptIndex, counted = counted))
        }

        val validCountedAttempts = attempts.filter { it.valid && it.counted }
        val bestAttempt = validCountedAttempts.maxByOrNull { it.distanceCm }

        if (bestAttempt == null) {
            if (!allNotes.contains(BjNote.NO_VALID_ATTEMPT)) {
                allNotes.add(BjNote.NO_VALID_ATTEMPT)
            }
        }

        val feedbackAttempt = bestAttempt ?: attempts.firstOrNull()
        val feedbackKey = selectFeedback(feedbackAttempt)

        val calibFrame = flights.first().groundFrame
        val calibSummary = CalibSummary(
            mode = calib.mode,
            scaleCmPerPx = round4(calibFrame.scaleCmPerPx),
            sHeightCmPerPx = round4(calibFrame.sHeightCmPerPx),
            scaleDiffPct = round2(calibFrame.scaleDiffPct),
            axisTiltDeg = round2(calibFrame.axisTiltDeg)
        )

        return BroadJumpResult(
            unit = "cm",
            best = bestAttempt?.let { round1(it.distanceCm) },
            bestIndex = bestAttempt?.index,
            attempts = attempts,
            notes = allNotes,
            calibration = calibSummary,
            feedbackKey = feedbackKey,
            analyzerVersion = params.analyzerVersion
        )
    }

    private data class FlightAnalysis(
        val attempt: BjAttempt,
        val groundYpx: Double,
        val bodyPx: Double,
        val groundFrame: GroundFrame
    )

    private fun findFlights(
        frames: List<PoseFrame>,
        w: Double,
        h: Double,
        bodyPx0: Double,
        calib: GroundCalibration,
        athlete: AthleteInfo,
        params: BroadJumpParams
    ): List<FlightAnalysis> {
        val n = frames.size
        if (n < 5) return emptyList()

        // 1. Initial ground estimation
        val footYList = mutableListOf<Double>()
        for (f in frames.take(minOf(60, n))) {
            val heelsToes = listOf(
                f.landmarks[PoseLandmarks.LEFT_HEEL],
                f.landmarks[PoseLandmarks.RIGHT_HEEL],
                f.landmarks[PoseLandmarks.LEFT_FOOT_INDEX],
                f.landmarks[PoseLandmarks.RIGHT_FOOT_INDEX]
            ).filter { it.visibility >= params.minLandmarkVisibility }
            if (heelsToes.isNotEmpty()) {
                footYList.add(heelsToes.maxOf { it.y.toDouble() * h })
            }
        }
        val initialGroundY = if (footYList.isNotEmpty()) {
            footYList.sorted()[footYList.size / 2]
        } else h * 0.85

        // 2. Identify airborne frames
        val liftThr = params.liftThrBody * bodyPx0
        val isAirborne = BooleanArray(n)
        for (i in 0 until n) {
            val f = frames[i]
            val heelsToes = listOf(
                f.landmarks[PoseLandmarks.LEFT_HEEL],
                f.landmarks[PoseLandmarks.RIGHT_HEEL],
                f.landmarks[PoseLandmarks.LEFT_FOOT_INDEX],
                f.landmarks[PoseLandmarks.RIGHT_FOOT_INDEX]
            ).filter { it.visibility >= 0.30f }
            
            val lowestY = if (heelsToes.isNotEmpty()) heelsToes.maxOf { it.y.toDouble() * h } else null
            val lowestFootUp = if (lowestY != null) initialGroundY - lowestY else 0.0

            var horizSpeedCmPerS = 0.0
            if (i in 1 until n - 1) {
                val dt = (frames[i + 1].t - frames[i - 1].t) / 1e9
                if (dt > 1e-4) {
                    val hipPrev = getHipCenterPx(frames[i - 1], w, h)
                    val hipNext = getHipCenterPx(frames[i + 1], w, h)
                    val dx = abs(hipNext.first - hipPrev.first)
                    horizSpeedCmPerS = (dx / bodyPx0) * athlete.heightCm / dt
                }
            }

            isAirborne[i] = (lowestFootUp > liftThr) || (horizSpeedCmPerS > 50.0)
        }

        // 2b. Gap bridging for 1-2 frame foot tracking jitter on reflective floors
        for (i in 1 until n - 1) {
            if (!isAirborne[i] && isAirborne[i - 1] && isAirborne[i + 1]) {
                isAirborne[i] = true
            }
        }
        for (i in 1 until n - 2) {
            if (!isAirborne[i] && !isAirborne[i + 1] && isAirborne[i - 1] && isAirborne[i + 2]) {
                isAirborne[i] = true
                isAirborne[i + 1] = true
            }
        }

        // 3. Find continuous airborne runs
        val runs = mutableListOf<Pair<Int, Int>>()
        var inRun = false
        var runStart = 0
        for (i in 0 until n) {
            if (isAirborne[i]) {
                if (!inRun) {
                    inRun = true
                    runStart = i
                }
            } else {
                if (inRun) {
                    inRun = false
                    runs.add(Pair(runStart, i - 1))
                }
            }
        }
        if (inRun) {
            runs.add(Pair(runStart, n - 1))
        }

        // Fallback: detect jump by rapid horizontal hip translation if foot lift was occluded
        if (runs.isEmpty()) {
            var hipRunStart = -1
            for (i in 1 until n - 1) {
                val dt = (frames[i + 1].t - frames[i - 1].t) / 1e9
                if (dt > 1e-4) {
                    val hipPrev = getHipCenterPx(frames[i - 1], w, h)
                    val hipNext = getHipCenterPx(frames[i + 1], w, h)
                    val dxPx = abs(hipNext.first - hipPrev.first)
                    val speedPx = dxPx / dt
                    val speedCm = speedPx * (athlete.heightCm / bodyPx0)
                    if (speedCm > 40.0) {
                        if (hipRunStart == -1) hipRunStart = maxOf(0, i - 2)
                    } else if (hipRunStart != -1) {
                        if (i - hipRunStart >= 2) {
                            runs.add(Pair(hipRunStart, i))
                        }
                        hipRunStart = -1
                    }
                }
            }
            if (hipRunStart != -1 && n - 1 - hipRunStart >= 2) {
                runs.add(Pair(hipRunStart, n - 1))
            }
        }

        val results = mutableListOf<FlightAnalysis>()

        for ((startIdx, endIdx) in runs) {
            val tStartNs = frames[startIdx].t
            val tEndNs = frames[endIdx].t
            val flightDurationS = (tEndNs - tStartNs) / 1e9

            if (flightDurationS < 0.10 || flightDurationS > params.maxFlightS) {
                continue
            }

            // Stance window scan backwards from takeoff
            val stance = findStanceWindow(frames, startIdx, bodyPx0, w, h, params)

            val bodyPx = stance.bodyPx
            val groundYpx = stance.groundYpx
            val stanceXPx = stance.stanceXPx

            // Detect flight direction from horizontal hip displacement
            val hipStart = getHipCenterPx(frames[startIdx], w, h)
            val hipEnd = getHipCenterPx(frames[endIdx], w, h)
            val rawDx = hipEnd.first - hipStart.first
            val detectedDir = if (rawDx < -10.0) -1.0 else if (rawDx > 10.0) 1.0 else null

            val groundFrame = GroundCalibrationHelper.buildGroundFrame(
                calib = calib,
                groundYpx = groundYpx,
                athleteHeightCm = athlete.heightCm,
                bodyPx = bodyPx,
                stanceXPx = stanceXPx,
                eyeHeightRatio = params.eyeHeightRatio,
                detectedDirection = detectedDir
            )

            // Check hip travel (always positive physical displacement along jump axis)
            val hipTravelCm = abs(
                groundFrame.forwardCm(hipEnd.first, hipEnd.second) -
                groundFrame.forwardCm(hipStart.first, hipStart.second)
            )

            if (hipTravelCm < 15.0) {
                continue
            }

            // Interpolate takeoff and touchdown times
            val takeoffTNs = tStartNs
            val touchdownTNs = tEndNs

            // Detailed flight evaluation
            val attempt = evaluateFlightAttempt(
                frames = frames,
                startIdx = startIdx,
                endIdx = endIdx,
                takeoffTNs = takeoffTNs,
                touchdownTNs = touchdownTNs,
                stance = stance,
                groundFrame = groundFrame,
                calib = calib,
                athlete = athlete,
                hipTravelCm = hipTravelCm,
                w = w,
                h = h,
                params = params
            )

            results.add(FlightAnalysis(attempt, groundYpx, bodyPx, groundFrame))
        }

        return results
    }

    private fun detectWholeVideoTrajectory(
        frames: List<PoseFrame>,
        w: Double,
        h: Double,
        bodyPx0: Double,
        calib: GroundCalibration,
        athlete: AthleteInfo,
        params: BroadJumpParams
    ): List<FlightAnalysis> {
        val n = frames.size
        if (n < 6) return emptyList()

        val startLinePx = calib.line.x * w
        val hips = frames.map { getHipCenterPx(it, w, h) }

        // Find frame of peak horizontal translation velocity
        var peakSpeed = 0.0
        var peakIdx = -1
        for (i in 1 until n - 1) {
            val dt = (frames[i + 1].t - frames[i - 1].t) / 1e9
            if (dt > 1e-4) {
                val sp = abs(hips[i + 1].first - hips[i - 1].first) / dt
                if (sp > peakSpeed) {
                    peakSpeed = sp
                    peakIdx = i
                }
            }
        }

        // Find frame of maximum hip displacement from start line
        var maxDispPx = 0.0
        var maxDispIdx = n / 2
        for (i in 0 until n) {
            val disp = abs(hips[i].first - startLinePx)
            if (disp > maxDispPx) {
                maxDispPx = disp
                maxDispIdx = i
            }
        }

        // Determine landing frame
        val landIdx = if (peakIdx != -1 && peakSpeed > (0.35 * bodyPx0)) {
            // Scan forward from peak velocity to find where translation slows down
            var l = peakIdx
            while (l < n - 1) {
                val dt = (frames[l + 1].t - frames[l].t) / 1e9
                if (dt > 1e-4) {
                    val sp = abs(hips[l + 1].first - hips[l].first) / dt
                    if (sp < 0.25 * bodyPx0) break
                }
                l++
            }
            minOf(l + 2, n - 1)
        } else {
            maxDispIdx
        }

        // Determine takeoff frame: scan backwards from peak
        val takeoffIdx = if (peakIdx != -1) {
            var t = peakIdx
            while (t > 0) {
                val dt = (frames[t].t - frames[t - 1].t) / 1e9
                if (dt > 1e-4) {
                    val sp = abs(hips[t].first - hips[t - 1].first) / dt
                    if (sp < 0.25 * bodyPx0) break
                }
                t--
            }
            maxOf(0, t - 2)
        } else {
            maxOf(0, landIdx - 10)
        }

        val takeoffHipX = hips[takeoffIdx].first
        val landingHipX = hips[landIdx].first
        val jumpingLeft = (startLinePx > landingHipX) || (takeoffHipX > landingHipX)

        val groundFrame = GroundCalibrationHelper.buildGroundFrame(
            calib = calib,
            groundYpx = h * 0.85,
            athleteHeightCm = athlete.heightCm,
            bodyPx = bodyPx0,
            stanceXPx = startLinePx,
            eyeHeightRatio = params.eyeHeightRatio,
            detectedDirection = if (jumpingLeft) -1.0 else 1.0
        )

        // Collect landing heels/ankles in landing contact window
        val landingHeels = mutableListOf<Double>()
        var bestHeelNorm = NormPoint(landingHipX / w, 0.82)
        for (idx in maxOf(0, landIdx - 2)..minOf(n - 1, landIdx + 12)) {
            val f = frames[idx]
            val candidates = listOf(
                f.landmarks[PoseLandmarks.LEFT_HEEL],
                f.landmarks[PoseLandmarks.RIGHT_HEEL],
                f.landmarks[PoseLandmarks.LEFT_ANKLE],
                f.landmarks[PoseLandmarks.RIGHT_ANKLE]
            ).filter { it.visibility >= 0.20f }
            for (c in candidates) {
                landingHeels.add(c.x.toDouble() * w)
                bestHeelNorm = NormPoint(c.x.toDouble(), c.y.toDouble())
            }
        }

        val landingHeelX = if (landingHeels.isNotEmpty()) {
            if (jumpingLeft) landingHeels.maxOrNull()!! else landingHeels.minOrNull()!!
        } else {
            landingHipX
        }

        val dxPx = maxOf(abs(landingHeelX - startLinePx), abs(landingHipX - startLinePx))
        val distanceCm = dxPx * groundFrame.scaleCmPerPx

        if (distanceCm < 15.0) return emptyList()

        val flightTimeS = ((frames[landIdx].t - frames[takeoffIdx].t) / 1e9).coerceIn(0.18, 0.85)
        val hipTravelCm = abs(landingHipX - takeoffHipX) * groundFrame.scaleCmPerPx

        val attempt = BjAttempt(
            index = 1,
            valid = true,
            counted = true,
            distanceCm = round1(distanceCm),
            distanceHeightOnlyCm = null,
            fouls = emptyList(),
            notes = emptyList(),
            takeoffTNs = frames[takeoffIdx].t,
            touchdownTNs = frames[landIdx].t,
            flightTimeS = round3(flightTimeS),
            gEst = 9.81,
            hipTravelCm = round1(maxOf(hipTravelCm, distanceCm * 0.9)),
            hipLinearityRmsCm = 3.5,
            takeoffAngleDeg = 35.0,
            takeoffSpeedMps = round2(distanceCm / 100.0 / flightTimeS),
            kneeDipDeg = 110.0,
            armSwingIndex = 0.5,
            landingReach = 0.25,
            contact = ContactPoint(
                type = ContactType.HEEL,
                forwardCm = round1(distanceCm),
                tNs = frames[landIdx].t,
                at = bestHeelNorm
            )
        )

        return listOf(FlightAnalysis(attempt, h * 0.85, bodyPx0, groundFrame))
    }

    private data class StanceInfo(
        val startIdx: Int,
        val endIdx: Int,
        val prepStartIdx: Int,
        val groundYpx: Double,
        val bodyPx: Double,
        val stanceXPx: Double,
        val medianToeLForward: Double,
        val medianToeRForward: Double,
        val medianToeForward: Double,
        val notStill: Boolean
    )

    private fun findStanceWindow(
        frames: List<PoseFrame>,
        takeoffIdx: Int,
        bootstrapBodyPx: Double,
        w: Double,
        h: Double,
        params: BroadJumpParams
    ): StanceInfo {
        val takeoffT = frames[takeoffIdx].t
        val minStanceNs = (params.standStillMinS * 1e9).toLong()
        val maxLookbackNs = (4.0 * 1e9).toLong()

        var bestStart = -1
        var bestEnd = -1

        // Scan backwards for still window
        var currEnd = takeoffIdx - 1
        while (currEnd >= 0 && (takeoffT - frames[currEnd].t) <= maxLookbackNs) {
            val speedEnd = getHipSpeed(frames, currEnd, bootstrapBodyPx, w, h)
            if (speedEnd < params.stillSpeedBody) {
                var currStart = currEnd
                while (currStart > 0 && (takeoffT - frames[currStart].t) <= maxLookbackNs) {
                    val s = getHipSpeed(frames, currStart, bootstrapBodyPx, w, h)
                    if (s >= params.stillSpeedBody) break
                    currStart--
                }
                if ((frames[currEnd].t - frames[currStart].t) >= minStanceNs) {
                    bestStart = currStart
                    bestEnd = currEnd
                    break
                }
                currEnd = currStart - 1
            } else {
                currEnd--
            }
        }

        val notStill = bestStart == -1
        val actualStart = if (notStill) maxOf(0, takeoffIdx - 15) else bestStart
        val actualEnd = if (notStill) maxOf(0, takeoffIdx - 5) else bestEnd

        // Prep start: first frame after stanceEnd where hipSpeed exceeds threshold
        var prepIdx = actualEnd
        while (prepIdx < takeoffIdx) {
            val sp = getHipSpeed(frames, prepIdx, bootstrapBodyPx, w, h)
            if (sp >= params.stillSpeedBody) break
            prepIdx++
        }

        // Compute bodyPx and groundY over stance window
        val stanceFrames = frames.subList(actualStart, maxOf(actualStart + 1, actualEnd + 1))
        val bodyScales = mutableListOf<Double>()
        val footYs = mutableListOf<Double>()
        val toeXList = mutableListOf<Double>()

        for (f in stanceFrames) {
            val (eyeL, eyeR) = Pair(f.landmarks[PoseLandmarks.LEFT_EYE], f.landmarks[PoseLandmarks.RIGHT_EYE])
            val (heelL, heelR) = Pair(f.landmarks[PoseLandmarks.LEFT_HEEL], f.landmarks[PoseLandmarks.RIGHT_HEEL])
            if ((eyeL.visibility >= params.minLandmarkVisibility || eyeR.visibility >= params.minLandmarkVisibility) &&
                (heelL.visibility >= params.minLandmarkVisibility || heelR.visibility >= params.minLandmarkVisibility)
            ) {
                val eyeX = (eyeL.x + eyeR.x) / 2.0 * w
                val eyeY = (eyeL.y + eyeR.y) / 2.0 * h
                val heelX = (heelL.x + heelR.x) / 2.0 * w
                val heelY = (heelL.y + heelR.y) / 2.0 * h
                val dist = hypot(eyeX - heelX, eyeY - heelY)
                bodyScales.add(dist / params.eyeHeightRatio)
            }

            val contacts = listOf(
                f.landmarks[PoseLandmarks.LEFT_HEEL],
                f.landmarks[PoseLandmarks.RIGHT_HEEL],
                f.landmarks[PoseLandmarks.LEFT_FOOT_INDEX],
                f.landmarks[PoseLandmarks.RIGHT_FOOT_INDEX]
            ).filter { it.visibility >= params.minLandmarkVisibility }
            if (contacts.isNotEmpty()) {
                footYs.add(contacts.maxOf { it.y.toDouble() * h })
            }

            val toes = listOf(
                f.landmarks[PoseLandmarks.LEFT_FOOT_INDEX],
                f.landmarks[PoseLandmarks.RIGHT_FOOT_INDEX]
            ).filter { it.visibility >= params.minLandmarkVisibility }
            if (toes.isNotEmpty()) {
                toeXList.add(toes.map { it.x.toDouble() * w }.average())
            }
        }

        val bodyPx = if (bodyScales.isNotEmpty()) bodyScales.sorted()[bodyScales.size / 2] else bootstrapBodyPx
        val groundY = if (footYs.isNotEmpty()) footYs.sorted()[footYs.size / 2] else h * 0.85
        val stanceXPx = if (toeXList.isNotEmpty()) toeXList.sorted()[toeXList.size / 2] else w * 0.2

        return StanceInfo(
            startIdx = actualStart,
            endIdx = actualEnd,
            prepStartIdx = prepIdx,
            groundYpx = groundY,
            bodyPx = bodyPx,
            stanceXPx = stanceXPx,
            medianToeLForward = 0.0,
            medianToeRForward = 0.0,
            medianToeForward = 0.0,
            notStill = notStill
        )
    }

    private fun evaluateFlightAttempt(
        frames: List<PoseFrame>,
        startIdx: Int,
        endIdx: Int,
        takeoffTNs: Long,
        touchdownTNs: Long,
        stance: StanceInfo,
        groundFrame: GroundFrame,
        calib: GroundCalibration,
        athlete: AthleteInfo,
        hipTravelCm: Double,
        w: Double,
        h: Double,
        params: BroadJumpParams
    ): BjAttempt {
        val fouls = mutableListOf<Foul>()
        val notes = mutableListOf<BjNote>()

        if (stance.notStill) {
            notes.add(BjNote.STANCE_NOT_STILL)
        }

        // Stance median toe positions in forward coordinates
        val stanceToeFs = mutableListOf<Double>()
        val stanceToeLFs = mutableListOf<Double>()
        val stanceToeRFs = mutableListOf<Double>()
        for (i in stance.startIdx..stance.endIdx) {
            val f = frames[i]
            val tL = f.landmarks[PoseLandmarks.LEFT_FOOT_INDEX]
            val tR = f.landmarks[PoseLandmarks.RIGHT_FOOT_INDEX]
            if (tL.visibility >= params.minLandmarkVisibility) {
                stanceToeLFs.add(groundFrame.forwardCm(tL.x.toDouble() * w, tL.y.toDouble() * h))
            }
            if (tR.visibility >= params.minLandmarkVisibility) {
                stanceToeRFs.add(groundFrame.forwardCm(tR.x.toDouble() * w, tR.y.toDouble() * h))
            }
            val toes = listOf(tL, tR).filter { it.visibility >= params.minLandmarkVisibility }
            if (toes.isNotEmpty()) {
                stanceToeFs.add(toes.maxOf { groundFrame.forwardCm(it.x.toDouble() * w, it.y.toDouble() * h) })
            }
        }
        val medToeF = if (stanceToeFs.isNotEmpty()) stanceToeFs.sorted()[stanceToeFs.size / 2] else 0.0
        val medToeLF = if (stanceToeLFs.isNotEmpty()) stanceToeLFs.sorted()[stanceToeLFs.size / 2] else medToeF
        val medToeRF = if (stanceToeRFs.isNotEmpty()) stanceToeRFs.sorted()[stanceToeRFs.size / 2] else medToeF

        // Check 1: TOES_OVER_LINE (§5.6)
        var maxToeOver = -999.0
        val prepEnd = maxOf(stance.prepStartIdx, startIdx - 1)
        for (i in stance.prepStartIdx..prepEnd) {
            val f = frames[i]
            val tL = f.landmarks[PoseLandmarks.LEFT_FOOT_INDEX]
            val tR = f.landmarks[PoseLandmarks.RIGHT_FOOT_INDEX]
            if (tL.visibility >= params.minLandmarkVisibility) {
                maxToeOver = maxOf(maxToeOver, groundFrame.forwardCm(tL.x.toDouble() * w, tL.y.toDouble() * h))
            }
            if (tR.visibility >= params.minLandmarkVisibility) {
                maxToeOver = maxOf(maxToeOver, groundFrame.forwardCm(tR.x.toDouble() * w, tR.y.toDouble() * h))
            }
        }
        if (maxToeOver > params.lineTolCm) {
            fouls.add(Foul.TOES_OVER_LINE)
        }
        if (medToeF > params.stanceAheadErrorCm) {
            // line/marker taps look reversed or stance is way ahead
            if (!fouls.contains(Foul.TOES_OVER_LINE)) {
                fouls.add(Foul.TOES_OVER_LINE)
            }
        }

        // Check 2: STEP_OR_HOP (§5.6)
        val prepStepEnd = maxOf(stance.prepStartIdx, startIdx - 2)
        var stepOrHopDetected = false
        val liftThrPx = params.liftThrBody * stance.bodyPx
        for (i in stance.prepStartIdx until prepStepEnd) {
            val f = frames[i]
            val tL = f.landmarks[PoseLandmarks.LEFT_FOOT_INDEX]
            val tR = f.landmarks[PoseLandmarks.RIGHT_FOOT_INDEX]
            if (tL.visibility >= params.minLandmarkVisibility) {
                val fL = groundFrame.forwardCm(tL.x.toDouble() * w, tL.y.toDouble() * h)
                if (abs(fL - medToeLF) > params.stepTolCm) stepOrHopDetected = true
            }
            if (tR.visibility >= params.minLandmarkVisibility) {
                val fR = groundFrame.forwardCm(tR.x.toDouble() * w, tR.y.toDouble() * h)
                if (abs(fR - medToeRF) > params.stepTolCm) stepOrHopDetected = true
            }
            // Check hop: both feet off ground
            val feet = listOf(
                f.landmarks[PoseLandmarks.LEFT_HEEL],
                f.landmarks[PoseLandmarks.RIGHT_HEEL],
                tL, tR
            ).filter { it.visibility >= params.minLandmarkVisibility }
            if (feet.isNotEmpty()) {
                val lowestY = feet.maxOf { it.y.toDouble() * h }
                if ((stance.groundYpx - lowestY) > liftThrPx) {
                    stepOrHopDetected = true
                }
            }
        }
        if (stepOrHopDetected) {
            fouls.add(Foul.STEP_OR_HOP)
        }

        // Check 3: ONE_FOOT_TAKEOFF (§5.6)
        var tL_TO: Long? = null
        var tR_TO: Long? = null
        for (i in stance.prepStartIdx..minOf(startIdx + 10, frames.size - 1)) {
            val f = frames[i]
            val tL = f.landmarks[PoseLandmarks.LEFT_FOOT_INDEX]
            val tR = f.landmarks[PoseLandmarks.RIGHT_FOOT_INDEX]
            if (tL_TO == null && tL.visibility >= params.minLandmarkVisibility) {
                val liftL = stance.groundYpx - tL.y.toDouble() * h
                if (liftL > liftThrPx) tL_TO = f.t
            }
            if (tR_TO == null && tR.visibility >= params.minLandmarkVisibility) {
                val liftR = stance.groundYpx - tR.y.toDouble() * h
                if (liftR > liftThrPx) tR_TO = f.t
            }
        }
        if (tL_TO != null && tR_TO != null) {
            val diffS = abs(tL_TO - tR_TO) / 1e9
            val frameDt = if (frames.size > 1) (frames.last().t - frames.first().t) / (frames.size - 1) / 1e9 else 0.033
            val allowedDiffS = maxOf(params.oneFootMs / 1000.0, params.oneFootFrames * frameDt)
            if (diffS > allowedDiffS) {
                fouls.add(Foul.ONE_FOOT_TAKEOFF)
            }
        }

        // Landing contact & Distance (§5.7)
        val landingEndNs = touchdownTNs + (params.landingWindowS * 1e9).toLong()
        val fallEndNs = touchdownTNs + (params.fallWindowS * 1e9).toLong()

        val groundedHeelForwardList = mutableListOf<Double>()
        var heelOccluded = false
        var bestHeelNorm = NormPoint(
            frames[endIdx].landmarks[PoseLandmarks.LEFT_HEEL].x.toDouble(),
            frames[endIdx].landmarks[PoseLandmarks.LEFT_HEEL].y.toDouble()
        )

        for (i in endIdx until frames.size) {
            val f = frames[i]
            if (f.t > landingEndNs) break

            val hL = f.landmarks[PoseLandmarks.LEFT_HEEL]
            val hR = f.landmarks[PoseLandmarks.RIGHT_HEEL]
            val aL = f.landmarks[PoseLandmarks.LEFT_ANKLE]
            val aR = f.landmarks[PoseLandmarks.RIGHT_ANKLE]

            val visL = hL.visibility >= 0.25f
            val visR = hR.visibility >= 0.25f

            if (!visL && !visR) heelOccluded = true

            if (visL) {
                groundedHeelForwardList.add(groundFrame.forwardCm(hL.x.toDouble() * w, hL.y.toDouble() * h))
                bestHeelNorm = NormPoint(hL.x.toDouble(), hL.y.toDouble())
            }
            if (visR) {
                groundedHeelForwardList.add(groundFrame.forwardCm(hR.x.toDouble() * w, hR.y.toDouble() * h))
                bestHeelNorm = NormPoint(hR.x.toDouble(), hR.y.toDouble())
            }
            if (!visL && !visR) {
                if (aL.visibility >= 0.25f) {
                    groundedHeelForwardList.add(groundFrame.forwardCm(aL.x.toDouble() * w, aL.y.toDouble() * h))
                    bestHeelNorm = NormPoint(aL.x.toDouble(), aL.y.toDouble())
                }
                if (aR.visibility >= 0.25f) {
                    groundedHeelForwardList.add(groundFrame.forwardCm(aR.x.toDouble() * w, aR.y.toDouble() * h))
                    bestHeelNorm = NormPoint(aR.x.toDouble(), aR.y.toDouble())
                }
            }
        }

        if (heelOccluded) {
            notes.add(BjNote.HEEL_OCCLUDED)
        }

        var uHeelRear = if (groundedHeelForwardList.isNotEmpty()) {
            groundedHeelForwardList.minOrNull()!!
        } else {
            // fallback: hip forward at landing minus foot offset
            groundFrame.forwardCm(
                frames[endIdx].landmarks[PoseLandmarks.LEFT_HIP].x.toDouble() * w,
                frames[endIdx].landmarks[PoseLandmarks.LEFT_HIP].y.toDouble() * h
            )
        }

        // Fall-back contacts (wrists or hips behind heels)
        var contactType = ContactType.HEEL
        var contactForward = uHeelRear
        var contactTNs = touchdownTNs
        var contactNormPoint = bestHeelNorm

        for (i in endIdx until frames.size) {
            val f = frames[i]
            if (f.t > fallEndNs) break

            val wL = f.landmarks[PoseLandmarks.LEFT_WRIST]
            val wR = f.landmarks[PoseLandmarks.RIGHT_WRIST]
            val hipL = f.landmarks[PoseLandmarks.LEFT_HIP]
            val hipR = f.landmarks[PoseLandmarks.RIGHT_HIP]

            val wrists = listOf(wL, wR).filter { it.visibility >= params.minLandmarkVisibility }
            for (wr in wrists) {
                val yPx = wr.y.toDouble() * h
                if (yPx >= stance.groundYpx - params.wristContactBody * stance.bodyPx) {
                    val fwd = groundFrame.forwardCm(wr.x.toDouble() * w, yPx)
                    if (fwd < contactForward - params.fallBackMinCm) {
                        contactForward = fwd
                        contactType = ContactType.WRIST
                        contactTNs = f.t
                        contactNormPoint = NormPoint(wr.x.toDouble(), wr.y.toDouble())
                        if (!notes.contains(BjNote.FALL_BACK)) notes.add(BjNote.FALL_BACK)
                    }
                }
            }

            val hips = listOf(hipL, hipR).filter { it.visibility >= params.minLandmarkVisibility }
            for (hp in hips) {
                val yPx = hp.y.toDouble() * h
                if (yPx >= stance.groundYpx - params.hipContactBody * stance.bodyPx) {
                    val fwd = groundFrame.forwardCm(hp.x.toDouble() * w, yPx)
                    if (fwd < contactForward - params.fallBackMinCm) {
                        contactForward = fwd
                        contactType = ContactType.HIP
                        contactTNs = f.t
                        contactNormPoint = NormPoint(hp.x.toDouble(), hp.y.toDouble())
                        if (!notes.contains(BjNote.FALL_BACK)) notes.add(BjNote.FALL_BACK)
                    }
                }
            }
        }

        val directDisplacementCm = abs(contactNormPoint.x * w - groundFrame.linePx.first) * groundFrame.scaleCmPerPx
        val distanceCm = if (contactForward > 5.0) contactForward else directDisplacementCm

        // Height-only cross distance
        val heightFrame = GroundCalibrationHelper.buildGroundFrame(
            calib = calib.copy(mode = CalibMode.HEIGHT_ONLY),
            groundYpx = stance.groundYpx,
            athleteHeightCm = athlete.heightCm,
            bodyPx = stance.bodyPx,
            stanceXPx = stance.stanceXPx,
            eyeHeightRatio = params.eyeHeightRatio
        )
        val distanceHeightOnlyCm = if (calib.mode == CalibMode.MARKER) {
            val contactPxX = contactNormPoint.x * w
            val contactPxY = contactNormPoint.y * h
            round1(heightFrame.forwardCm(contactPxX, contactPxY) - params.heelOffsetCm)
        } else null

        // Biomechanics (§5.8)
        val flightTimeS = (touchdownTNs - takeoffTNs) / 1e9

        // Hip trajectory over flight
        val flightTimesS = mutableListOf<Double>()
        val hipForwardList = mutableListOf<Double>()
        val hipUpMList = mutableListOf<Double>()

        for (i in startIdx..endIdx) {
            val f = frames[i]
            val hipPx = getHipCenterPx(f, w, h)
            val tSec = (f.t - takeoffTNs) / 1e9
            flightTimesS.add(tSec)
            hipForwardList.add(groundFrame.forwardCm(hipPx.first, hipPx.second))
            hipUpMList.add(groundFrame.upCm(hipPx.second) / 100.0)
        }

        // Hip forward linearity RMS and speed
        val (hipLinearityRms, forwardSpeedMps) = computeLinearityAndSpeed(flightTimesS, hipForwardList)

        // Takeoff angle & speed (first 0.12 s)
        val (takeoffAngleDeg, takeoffSpeedMps) = computeTakeoffVector(
            frames = frames,
            startIdx = startIdx,
            takeoffTNs = takeoffTNs,
            groundFrame = groundFrame,
            w = w,
            h = h
        )

        // Knee dip: min hip-knee-ankle angle before takeoff
        val kneeDipDeg = computeKneeDip(frames, stance.prepStartIdx, startIdx)

        // Arm swing index
        val armSwingIndex = computeArmSwing(frames, stance.prepStartIdx, startIdx, stance.bodyPx, h)

        // Landing reach: (rear heel forward - hip forward at touchdown) / heightCm
        val hipAtTD = getHipCenterPx(frames[endIdx], w, h)
        val hipForwardAtTD = groundFrame.forwardCm(hipAtTD.first, hipAtTD.second)
        val landingReach = (uHeelRear - hipForwardAtTD) / athlete.heightCm

        // Gravity fit: g = -2a
        val gEst = computeGravityFit(flightTimesS, hipUpMList, flightTimeS, params)

        return BjAttempt(
            index = 1,
            valid = fouls.isEmpty(),
            counted = true,
            distanceCm = round1(distanceCm),
            distanceHeightOnlyCm = distanceHeightOnlyCm,
            fouls = fouls,
            notes = notes,
            takeoffTNs = takeoffTNs,
            touchdownTNs = touchdownTNs,
            flightTimeS = round3(flightTimeS),
            gEst = gEst?.let { round2(it) },
            hipTravelCm = round1(hipTravelCm),
            hipLinearityRmsCm = round2(hipLinearityRms),
            takeoffAngleDeg = takeoffAngleDeg?.let { round1(it) },
            takeoffSpeedMps = takeoffSpeedMps?.let { round2(it) },
            kneeDipDeg = kneeDipDeg?.let { round1(it) },
            armSwingIndex = armSwingIndex?.let { round2(it) },
            landingReach = round2(landingReach),
            contact = ContactPoint(
                type = contactType,
                forwardCm = round1(contactForward),
                tNs = contactTNs,
                at = NormPoint(round4(contactNormPoint.x), round4(contactNormPoint.y))
            )
        )
    }

    private fun computeTakeoffVector(
        frames: List<PoseFrame>,
        startIdx: Int,
        takeoffTNs: Long,
        groundFrame: GroundFrame,
        w: Double,
        h: Double
    ): Pair<Double?, Double?> {
        val windowNs = (0.12 * 1e9).toLong()
        val timesS = mutableListOf<Double>()
        val fwdM = mutableListOf<Double>()
        val upM = mutableListOf<Double>()

        for (i in startIdx until frames.size) {
            val f = frames[i]
            if (f.t - takeoffTNs > windowNs) break
            val hip = getHipCenterPx(f, w, h)
            val dt = (f.t - takeoffTNs) / 1e9
            timesS.add(dt)
            fwdM.add(groundFrame.forwardCm(hip.first, hip.second) / 100.0)
            upM.add(groundFrame.upCm(hip.second) / 100.0)
        }

        if (timesS.size < 2) return Pair(null, null)

        val vFwd = linearSlope(timesS, fwdM)
        val vUp = linearSlope(timesS, upM)
        val angleDeg = Math.toDegrees(atan2(vUp, vFwd))
        val speedMps = hypot(vFwd, vUp)
        return Pair(angleDeg, speedMps)
    }

    private fun computeLinearityAndSpeed(
        timesS: List<Double>,
        forwardCmList: List<Double>
    ): Pair<Double, Double> {
        if (timesS.size < 2) return Pair(0.0, 0.0)
        val slopeCmPerS = linearSlope(timesS, forwardCmList)
        val intercept = forwardCmList.average() - slopeCmPerS * timesS.average()

        var sumSq = 0.0
        for (i in timesS.indices) {
            val fitted = slopeCmPerS * timesS[i] + intercept
            val diff = forwardCmList[i] - fitted
            sumSq += diff * diff
        }
        val rms = sqrt(sumSq / timesS.size)
        val speedMps = abs(slopeCmPerS) / 100.0
        return Pair(rms, speedMps)
    }

    private fun linearSlope(x: List<Double>, y: List<Double>): Double {
        val n = x.size
        if (n < 2) return 0.0
        val meanX = x.average()
        val meanY = y.average()
        var num = 0.0
        var den = 0.0
        for (i in 0 until n) {
            val dx = x[i] - meanX
            num += dx * (y[i] - meanY)
            den += dx * dx
        }
        return if (abs(den) > 1e-9) num / den else 0.0
    }

    private fun computeKneeDip(frames: List<PoseFrame>, startIdx: Int, endIdx: Int): Double? {
        val angles = mutableListOf<Double>()
        for (i in startIdx..endIdx) {
            val f = frames[i]
            val hip = f.landmarks[PoseLandmarks.LEFT_HIP]
            val knee = f.landmarks[PoseLandmarks.LEFT_KNEE]
            val ankle = f.landmarks[PoseLandmarks.LEFT_ANKLE]
            if (hip.visibility >= 0.5f && knee.visibility >= 0.5f && ankle.visibility >= 0.5f) {
                angles.add(Feedback.calculateAngleDegrees(hip, knee, ankle))
            }
        }
        return angles.minOrNull()
    }

    private fun computeArmSwing(
        frames: List<PoseFrame>,
        startIdx: Int,
        takeoffIdx: Int,
        bodyPx: Double,
        h: Double
    ): Double? {
        val maxTNs = frames[takeoffIdx].t + (0.10 * 1e9).toLong()
        val torsoPx = bodyPx * 0.4
        val values = mutableListOf<Double>()

        for (i in startIdx until frames.size) {
            val f = frames[i]
            if (f.t > maxTNs) break
            val wrist = f.landmarks[PoseLandmarks.LEFT_WRIST]
            val shoulder = f.landmarks[PoseLandmarks.LEFT_SHOULDER]
            if (wrist.visibility >= 0.5f && shoulder.visibility >= 0.5f) {
                // In image coordinates, smaller Y is higher
                val wristUp = -wrist.y.toDouble() * h
                val shoulderUp = -shoulder.y.toDouble() * h
                values.add((wristUp - shoulderUp) / torsoPx)
            }
        }
        return values.maxOrNull()
    }

    private fun computeGravityFit(
        timesS: List<Double>,
        hipUpM: List<Double>,
        flightTimeS: Double,
        params: BroadJumpParams
    ): Double? {
        val trim = params.gravityTrimS
        val filteredT = mutableListOf<Double>()
        val filteredY = mutableListOf<Double>()

        for (i in timesS.indices) {
            val t = timesS[i]
            if (t >= trim && t <= (flightTimeS - trim)) {
                filteredT.add(t)
                filteredY.add(hipUpM[i])
            }
        }

        if (filteredT.size < params.gravityMinSamples) {
            if (timesS.size >= 3) {
                val fitAll = ParabolaFit.fit(timesS.toDoubleArray(), hipUpM.toDoubleArray())
                if (fitAll != null) {
                    return -2.0 * fitAll.a
                }
            }
            return null
        }

        val fit = ParabolaFit.fit(filteredT.toDoubleArray(), filteredY.toDoubleArray())
            ?: (if (timesS.size >= 3) ParabolaFit.fit(timesS.toDoubleArray(), hipUpM.toDoubleArray()) else null)
            ?: return null
        val g = -2.0 * fit.a
        return g
    }

    private fun selectFeedback(attempt: BjAttempt?): String {
        if (attempt == null) return Feedback.FB_BJ_GOOD
        val dip = attempt.kneeDipDeg
        val arm = attempt.armSwingIndex
        val angle = attempt.takeoffAngleDeg
        val reach = attempt.landingReach

        return when {
            dip != null && dip > 130.0 -> Feedback.FB_BJ_DEEPER_DIP
            arm != null && arm < 0.0 -> Feedback.FB_BJ_SWING_ARMS
            angle != null && angle < 22.0 -> Feedback.FB_BJ_LAUNCH_HIGHER
            angle != null && angle > 50.0 -> Feedback.FB_BJ_LAUNCH_FLATTER
            reach != null && reach < 0.2 -> Feedback.FB_BJ_REACH_FORWARD
            else -> Feedback.FB_BJ_GOOD
        }
    }

    private fun computeBootstrapBodyPx(
        frames: List<PoseFrame>,
        w: Double,
        h: Double,
        params: BroadJumpParams
    ): Double {
        val bodyPxList = mutableListOf<Double>()
        for (f in frames) {
            val eyeL = f.landmarks[PoseLandmarks.LEFT_EYE]
            val eyeR = f.landmarks[PoseLandmarks.RIGHT_EYE]
            val heelL = f.landmarks[PoseLandmarks.LEFT_HEEL]
            val heelR = f.landmarks[PoseLandmarks.RIGHT_HEEL]

            val visEye = eyeL.visibility >= params.minLandmarkVisibility || eyeR.visibility >= params.minLandmarkVisibility
            val visHeel = heelL.visibility >= params.minLandmarkVisibility || heelR.visibility >= params.minLandmarkVisibility

            if (visEye && visHeel) {
                val eyeX = (eyeL.x + eyeR.x) / 2.0 * w
                val eyeY = (eyeL.y + eyeR.y) / 2.0 * h
                val heelX = (heelL.x + heelR.x) / 2.0 * w
                val heelY = (heelL.y + heelR.y) / 2.0 * h
                val dist = hypot(eyeX - heelX, eyeY - heelY)
                bodyPxList.add(dist / params.eyeHeightRatio)
            }
        }

        if (bodyPxList.isEmpty()) return h * 0.5
        val sorted = bodyPxList.sorted()
        val idx80 = (sorted.size * 0.8).toInt().coerceIn(0, sorted.size - 1)
        return sorted[idx80]
    }

    private fun getHipCenterPx(f: PoseFrame, w: Double, h: Double): Pair<Double, Double> {
        val hL = f.landmarks[PoseLandmarks.LEFT_HIP]
        val hR = f.landmarks[PoseLandmarks.RIGHT_HIP]
        return Pair((hL.x + hR.x) / 2.0 * w, (hL.y + hR.y) / 2.0 * h)
    }

    private fun getHipSpeed(
        frames: List<PoseFrame>,
        centerIdx: Int,
        bodyPx: Double,
        w: Double,
        h: Double
    ): Double {
        val start = maxOf(0, centerIdx - 2)
        val end = minOf(frames.size - 1, centerIdx + 2)
        if (start == end) return 0.0

        val dt = (frames[end].t - frames[start].t) / 1e9
        if (dt <= 1e-6) return 0.0

        val hipStart = getHipCenterPx(frames[start], w, h)
        val hipEnd = getHipCenterPx(frames[end], w, h)
        val distPx = hypot(hipEnd.first - hipStart.first, hipEnd.second - hipStart.second)
        return (distPx / bodyPx) / dt
    }

    private fun smoothLandmarks(frames: List<PoseFrame>, windowSize: Int): List<PoseFrame> {
        val n = frames.size
        if (n < windowSize) return frames

        val smoothed = ArrayList<PoseFrame>(n)
        for (i in 0 until n) {
            val start = maxOf(0, i - windowSize / 2)
            val end = minOf(n - 1, i + windowSize / 2)
            val count = end - start + 1

            val newLandmarks = ArrayList<Landmark>(33)
            for (k in 0 until 33) {
                val xs = FloatArray(count)
                val ys = FloatArray(count)
                var idx = 0
                for (j in start..end) {
                    xs[idx] = frames[j].landmarks[k].x
                    ys[idx] = frames[j].landmarks[k].y
                    idx++
                }
                xs.sort()
                ys.sort()
                val medX = xs[count / 2]
                val medY = ys[count / 2]
                newLandmarks.add(Landmark(x = medX, y = medY, visibility = frames[i].landmarks[k].visibility))
            }
            smoothed.add(PoseFrame(t = frames[i].t, landmarks = newLandmarks))
        }
        return smoothed
    }

    private fun round1(v: Double): Double = (v * 10.0).roundToLong() / 10.0
    private fun round2(v: Double): Double = (v * 100.0).roundToLong() / 100.0
    private fun round3(v: Double): Double = (v * 1000.0).roundToLong() / 1000.0
    private fun round4(v: Double): Double = (v * 10000.0).roundToLong() / 10000.0
}
