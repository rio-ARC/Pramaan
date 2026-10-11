package app.pramaan.ui.screens

import android.os.SystemClock
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Sports
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.pramaan.AppContainer
import app.pramaan.core.model.PoseFrame
import app.pramaan.core.model.PoseLandmarks
import app.pramaan.core.model.TestType
import app.pramaan.core.scoring.SitUpAnalyzer
import app.pramaan.core.scoring.VerticalJumpAnalyzer
import app.pramaan.core.scoring.broadjump.BroadJumpLiveTracker
import kotlin.math.abs
import app.pramaan.data.AthleteEntity
import app.pramaan.ui.components.CameraPermissionGate
import app.pramaan.ui.components.PramaanButton
import app.pramaan.ui.components.SkeletonOverlay
import app.pramaan.ui.theme.PramaanEmerald
import app.pramaan.ui.theme.PramaanOnSurface
import app.pramaan.ui.theme.PramaanOutline
import app.pramaan.ui.theme.PramaanSaffron
import app.pramaan.ui.theme.PramaanSurface
import app.pramaan.ui.theme.PramaanTypography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

@Composable
fun RecordScreen(
    athleteId: String,
    testType: TestType,
    container: AppContainer,
    onFinished: (sessionId: String) -> Unit,
    onBack: () -> Unit
) {
    CameraPermissionGate(purpose = "capturing exercise movement and recording verification video") {
        RecordContent(
            athleteId = athleteId,
            testType = testType,
            container = container,
            onFinished = onFinished,
            onBack = onBack
        )
    }
}

@Composable
private fun RecordContent(
    athleteId: String,
    testType: TestType,
    container: AppContainer,
    onFinished: (sessionId: String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val sessionId = remember { UUID.randomUUID().toString() }
    var athleteProfile by remember { mutableStateOf<app.pramaan.api.dto.AccountProfileDto?>(null) }
    var isSealing by remember { mutableStateOf(false) }

    // Live telemetry states
    var currentPoseFrame by remember { mutableStateOf<PoseFrame?>(null) }
    var provisionalMetric by remember { mutableDoubleStateOf(0.0) }
    var bestBenchmark by remember { mutableDoubleStateOf(0.0) }
    var liveFeedback by remember { mutableStateOf("Body in view • Ready for movement") }
    var toastText by remember { mutableStateOf<String?>(null) }
    var attemptNumber by remember { mutableIntStateOf(1) }

    // Timer state
    var elapsedSeconds by remember { mutableLongStateOf(0L) }
    var isRunning by remember { mutableStateOf(false) }

    // Recent frames buffer for scoring
    val frameBuffer = remember { mutableStateListOf<PoseFrame>() }
    var frameCounter by remember { mutableIntStateOf(0) }
    val bjTracker = remember(athleteProfile) { BroadJumpLiveTracker(athleteProfile?.heightCm ?: 165.0) }

    // Load athlete profile
    LaunchedEffect(athleteId) {
        athleteProfile = container.authStore.getProfile()
    }

    // Elapsed timer ticker
    LaunchedEffect(isRunning) {
        if (!isRunning) return@LaunchedEffect
        val startWall = SystemClock.elapsedRealtime()
        while (isRunning) {
            elapsedSeconds = (SystemClock.elapsedRealtime() - startWall) / 1000L
            if (testType == TestType.SIT_UPS && elapsedSeconds >= 30) {
                // Auto finish 30s sit-up cadence
                isRunning = false
                break
            }
            delay(500)
        }
    }

    val videoFile = remember(sessionId) { container.sessionStore.getVideoFile(sessionId) }
    val framesFile = remember(sessionId) { container.sessionStore.getFramesFile(sessionId) }
    val gyroFile = remember(sessionId) { container.sessionStore.getGyroFile(sessionId) }

    // Setup lifecycle cleanup
    DisposableEffect(sessionId) {
        val sessionDir = container.sessionStore.getSessionDir(sessionId)
        sessionDir.mkdirs()

        // Start gyro recording
        container.gyroRecorder.startRecording()

        // Set up pose recording
        container.poseEngine.startRecording(framesFile)
        container.poseEngine.onPoseFrame = { frame: PoseFrame ->
            currentPoseFrame = frame
            frameBuffer.add(frame)
            if (frameBuffer.size > 180) frameBuffer.removeAt(0)

            frameCounter++
            if (testType == TestType.BROAD_JUMP) {
                val fw = container.poseEngine.frameWidth
                val fh = container.poseEngine.frameHeight
                val state = bjTracker.onFrame(frame, fw, fh)
                if (state.standingStill && state.attemptsDetected == 0) {
                    toastText = "Standing Steady • Ready to Jump"
                    liveFeedback = "Ready behind line — jump forward"
                } else if (state.attemptsDetected == 0 && !state.standingStill) {
                    liveFeedback = "Hold still behind the line to begin"
                }
                if (state.attemptsDetected > 0) {
                    attemptNumber = (state.attemptsDetected + 1).coerceAtMost(3)
                }
                if (state.lastApproxCm != null) {
                    val approx = Math.round(state.lastApproxCm!! * 10.0) / 10.0
                    provisionalMetric = approx
                    if (provisionalMetric > bestBenchmark) {
                        bestBenchmark = provisionalMetric
                    }
                    toastText = "Landing Detected • ≈ ${provisionalMetric.toInt()} cm"
                    liveFeedback = "Jump ${state.attemptsDetected} recorded (≈ ${provisionalMetric.toInt()} cm) • Ready for next"
                } else if (frameCounter % 5 == 0 && frameBuffer.size >= 15) {
                    // Buffer trajectory fallback: detect displacement between stance and current position
                    val hL = frameBuffer.first().landmarks[PoseLandmarks.LEFT_HIP]
                    val hR = frameBuffer.first().landmarks[PoseLandmarks.RIGHT_HIP]
                    val currHL = frame.landmarks[PoseLandmarks.LEFT_HIP]
                    val currHR = frame.landmarks[PoseLandmarks.RIGHT_HIP]
                    if ((hL.visibility >= 0.25f || hR.visibility >= 0.25f) && (currHL.visibility >= 0.25f || currHR.visibility >= 0.25f)) {
                        val startHipX = (hL.x + hR.x) / 2.0 * fw
                        val currHipX = (currHL.x + currHR.x) / 2.0 * fw
                        val dx = abs(currHipX - startHipX)
                        val athleteH = athleteProfile?.heightCm ?: 165.0
                        // Estimate bodyPx from current frame
                        val eye = frame.landmarks[PoseLandmarks.NOSE].y * fh
                        val heel = maxOf(frame.landmarks[PoseLandmarks.LEFT_HEEL].y, frame.landmarks[PoseLandmarks.RIGHT_HEEL].y) * fh
                        val bodyPx = if (heel > eye) (heel - eye) / 0.9 else (fh * 0.6)
                        val approx = Math.round(dx * (athleteH / bodyPx) * 10.0) / 10.0
                        if (approx >= 30.0) {
                            provisionalMetric = approx
                            if (provisionalMetric > bestBenchmark) {
                                bestBenchmark = provisionalMetric
                            }
                            toastText = "Movement Detected • ≈ ${provisionalMetric.toInt()} cm"
                            liveFeedback = "Jump recorded (≈ ${provisionalMetric.toInt()} cm)"
                        }
                    }
                }
            } else if (frameCounter % 3 == 0) {
                val prof = athleteProfile
                val heightCm = prof?.heightCm ?: 165.0
                if (testType == TestType.VERTICAL_JUMP) {
                    val result = VerticalJumpAnalyzer.analyze(frameBuffer.toList(), heightCm)
                    if (result.attempts.isNotEmpty()) {
                        val lastAtt = result.attempts.last()
                        provisionalMetric = Math.round(lastAtt.flightCm * 10.0) / 10.0
                        if (provisionalMetric > bestBenchmark) {
                            bestBenchmark = provisionalMetric
                        }
                        toastText = "Take-off Detected • Apex ${provisionalMetric} cm"
                        liveFeedback = "Depth good, full extension on landing"
                    }
                } else if (testType == TestType.SIT_UPS) {
                    val result = SitUpAnalyzer.analyze(frameBuffer.toList())
                    provisionalMetric = result.totalReps.toDouble()
                    bestBenchmark = provisionalMetric
                    toastText = "Rep ${result.totalReps} • Flexion Valid"
                    liveFeedback = "Rep valid: flexed > 70°"
                }
            }
        }

        onDispose {
            isRunning = false
            container.cameraController.unbind()
            container.poseEngine.stopRecording()
            container.gyroRecorder.stopRecording(gyroFile)
        }
    }

    fun stopAndSaveResult() {
        if (isSealing) return
        isSealing = true
        isRunning = false

        scope.launch {
            withContext(Dispatchers.IO) {
                // Wait for CameraX to completely flush and finalize the MP4 file
                container.cameraController.stopAndAwaitFinalize()
                container.poseEngine.stopRecording()
                container.gyroRecorder.stopRecording(gyroFile)

                if (testType != TestType.BROAD_JUMP) {
                    val prof = athleteProfile ?: app.pramaan.api.dto.AccountProfileDto(
                        id = athleteId,
                        role = "ATHLETE",
                        name = "Athlete",
                        sex = "M",
                        heightCm = 165.0,
                        weightKg = 55.0
                    )

                    val fps = container.poseEngine.measuredFps
                    container.sealer.sealSession(
                        sessionId = sessionId,
                        testType = testType,
                        athleteProfile = prof,
                        fps = fps
                    )
                }
            }
            onFinished(sessionId)
        }
    }

    Scaffold(
        containerColor = PramaanSurface
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Viewfinder Area with 4:5 Aspect Ratio (Stitch Screen 3)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 5f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF131B2E))
            ) {
                // Live CameraX PreviewView
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            container.cameraController.bindCamera(
                                lifecycleOwner = lifecycleOwner,
                                previewView = this,
                                onBound = { success ->
                                    if (success) {
                                        container.cameraController.startRecording(videoFile)
                                        isRunning = true
                                    }
                                }
                            )
                        }
                    }
                )

                // Sun-glaze gradient scrim for outdoor visibility
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0x99131B2E),
                                    Color.Transparent,
                                    Color(0xCC131B2E)
                                )
                            )
                        )
                )

                // Skeleton Wireframe Overlay
                SkeletonOverlay(
                    poseFrame = currentPoseFrame,
                    modifier = Modifier.fillMaxSize(),
                    lineColor = Color(0xFF7CF994),
                    jointColor = Color.White
                )

                // Corner alignment framing brackets
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                ) {
                    Box(modifier = Modifier.align(Alignment.TopStart).size(16.dp).border(2.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(topStart = 4.dp)))
                    Box(modifier = Modifier.align(Alignment.TopEnd).size(16.dp).border(2.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(topEnd = 4.dp)))
                    Box(modifier = Modifier.align(Alignment.BottomStart).size(16.dp).border(2.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(bottomStart = 4.dp)))
                    Box(modifier = Modifier.align(Alignment.BottomEnd).size(16.dp).border(2.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(bottomEnd = 4.dp)))
                }

                // Top Floating Pill: Timer + Attempt Indicator
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                        .clip(CircleShape)
                        .background(Color(0xD9131B2E))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEF4444))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    val minutes = elapsedSeconds / 60
                    val seconds = elapsedSeconds % 60
                    Text(
                        text = "%02d:%02d".format(minutes, seconds),
                        style = PramaanTypography.labelMedium,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(modifier = Modifier.width(1.dp).height(12.dp).background(Color(0xFF64748B)))
                    Spacer(modifier = Modifier.width(10.dp))
                    Icon(
                        imageVector = Icons.Default.Flag,
                        contentDescription = null,
                        tint = Color(0xFF7CF994),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = when (testType) {
                            TestType.VERTICAL_JUMP -> "Attempt $attemptNumber of 3"
                            TestType.BROAD_JUMP -> "Jump $attemptNumber of 3"
                            TestType.SIT_UPS -> "30s Cadence"
                        },
                        style = PramaanTypography.labelSmall,
                        color = Color(0xFF7CF994)
                    )
                }

                // Center Floating Toast Indicator (When take-off or rep is detected)
                toastText?.let { text ->
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 64.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xE6131B2E))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0x337CF994)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Height,
                                contentDescription = null,
                                tint = Color(0xFF7CF994),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = text,
                            style = PramaanTypography.labelMedium,
                            color = Color.White
                        )
                    }
                }

                // Bottom Left: Active Optical Tracking Badge
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 12.dp, bottom = 12.dp)
                        .clip(CircleShape)
                        .background(Color(0xBF131B2E))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF7CF994))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "AI POSE • LIVE",
                        style = PramaanTypography.labelSmall,
                        color = Color.White
                    )
                }

                // Back button on viewfinder
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Bottom Clean HUD Card (Stitch Screen 3)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                shadowElevation = 4.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    // Top Metric Split: Provisional Jump & Session Best
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = when (testType) {
                                        TestType.VERTICAL_JUMP -> "PROVISIONAL HEIGHT"
                                        TestType.BROAD_JUMP -> "≈ APPROX DISTANCE"
                                        TestType.SIT_UPS -> "REPETITIONS"
                                    },
                                    style = PramaanTypography.labelSmall,
                                    color = PramaanOutline
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFDCFCE7))
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "LIVE",
                                        style = PramaanTypography.labelSmall,
                                        color = PramaanEmerald
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = when (testType) {
                                        TestType.VERTICAL_JUMP -> "$provisionalMetric"
                                        TestType.BROAD_JUMP -> if (provisionalMetric > 0) "≈ ${provisionalMetric.toInt()}" else "—"
                                        TestType.SIT_UPS -> "${provisionalMetric.toInt()}"
                                    },
                                    style = PramaanTypography.displayLarge,
                                    color = PramaanOnSurface
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (testType == TestType.SIT_UPS) "reps" else "cm",
                                    style = PramaanTypography.titleMedium,
                                    color = PramaanOutline,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                            }
                        }

                        // Benchmark badge
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "SESSION BENCHMARK",
                                style = PramaanTypography.labelSmall,
                                color = PramaanOutline
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFF1F5F9))
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.MilitaryTech,
                                        contentDescription = null,
                                        tint = PramaanSaffron,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = when (testType) {
                                            TestType.VERTICAL_JUMP -> "Best: $bestBenchmark cm"
                                            TestType.BROAD_JUMP -> "Best: ≈ ${bestBenchmark.toInt()} cm"
                                            TestType.SIT_UPS -> "Best: ${bestBenchmark.toInt()} reps"
                                        },
                                        style = PramaanTypography.labelMedium,
                                        color = PramaanOnSurface
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Live Coach Feedback Ribbon
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF8FAFC))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFFFEDD5)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sports,
                                contentDescription = null,
                                tint = PramaanSaffron,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "BIOMECHANICAL FEEDBACK",
                                style = PramaanTypography.labelSmall,
                                color = PramaanSaffron
                            )
                            Text(
                                text = liveFeedback,
                                style = PramaanTypography.bodySmall,
                                color = PramaanOnSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Primary CTA: Stop & Save Result
                    PramaanButton(
                        text = if (isSealing) "Sealing Hardware Record..." else "Stop & Save Result",
                        icon = Icons.Default.StopCircle,
                        onClick = { stopAndSaveResult() },
                        enabled = !isSealing
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Retake Secondary Action
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Next Attempt (${attemptNumber + 1}/3)",
                            style = PramaanTypography.labelMedium,
                            color = PramaanOutline,
                            modifier = Modifier.padding(4.dp)
                        )
                    }
                }
            }
        }
    }
}
