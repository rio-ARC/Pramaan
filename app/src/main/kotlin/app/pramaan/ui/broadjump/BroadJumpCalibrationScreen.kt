package app.pramaan.ui.broadjump

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pramaan.AppContainer
import app.pramaan.core.manifest.PoseFrameJsonl
import app.pramaan.core.model.PoseLandmarks
import app.pramaan.core.model.TestType
import app.pramaan.core.scoring.broadjump.AthleteInfo
import app.pramaan.core.scoring.broadjump.BroadJumpAnalyzer
import app.pramaan.core.scoring.broadjump.BroadJumpParams
import app.pramaan.core.scoring.broadjump.CalibMode
import app.pramaan.core.scoring.broadjump.GroundCalibration
import app.pramaan.core.scoring.broadjump.JumpDirection
import app.pramaan.core.scoring.broadjump.NormPoint
import app.pramaan.media.FrameExtractor
import app.pramaan.media.StillRenderer
import app.pramaan.ui.components.PramaanButton
import app.pramaan.ui.components.PramaanCard
import app.pramaan.ui.theme.PramaanEmerald
import app.pramaan.ui.theme.PramaanError
import app.pramaan.ui.theme.PramaanNavy
import app.pramaan.ui.theme.PramaanOnSurface
import app.pramaan.ui.theme.PramaanOnSurfaceVariant
import app.pramaan.ui.theme.PramaanOutline
import app.pramaan.ui.theme.PramaanSaffron
import app.pramaan.ui.theme.PramaanSurface
import app.pramaan.ui.theme.PramaanTypography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs

enum class ActivePoint {
    LINE,
    MARKER
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BroadJumpCalibrationScreen(
    sessionId: String,
    container: AppContainer,
    onCalibrationComplete: (sessionId: String) -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val videoFile = remember(sessionId) { container.sessionStore.getVideoFile(sessionId) }
    val framesFile = remember(sessionId) { container.sessionStore.getFramesFile(sessionId) }

    // Calibration mode: default is Height-Only (No Tape) as requested by user
    var isHeightOnly by remember { mutableStateOf(true) }
    var activePoint by remember { mutableStateOf(ActivePoint.LINE) }
    var jumpDirection by remember { mutableStateOf(JumpDirection.LEFT) }

    // Calibration points
    var linePoint by remember { mutableStateOf<NormPoint?>(NormPoint(0.70, 0.80)) }
    var markerPoint by remember { mutableStateOf<NormPoint?>(null) }
    var markerCm by remember { mutableDoubleStateOf(200.0) }

    // Video frame scrubber state
    var minTimeNs by remember { mutableLongStateOf(0L) }
    var maxTimeNs by remember { mutableLongStateOf(10_000_000_000L) }
    var currentTimeNs by remember { mutableLongStateOf(500_000_000L) }
    var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoadingFrame by remember { mutableStateOf(false) }

    // Sealing / Analysis progress
    var isAnalyzing by remember { mutableStateOf(false) }
    var analyzeStepText by remember { mutableStateOf("") }

    // Athlete profile
    var athleteHeightCm by remember { mutableDoubleStateOf(165.0) }

    // 1. Initial frame load and smart auto-detection of stance & jump direction
    LaunchedEffect(sessionId) {
        withContext(Dispatchers.IO) {
            val prof = container.authStore.getProfile()
            athleteHeightCm = prof?.heightCm ?: 165.0

            val frames = if (framesFile.exists()) {
                framesFile.reader().use { PoseFrameJsonl.readAll(it) }
            } else emptyList()

            if (frames.isNotEmpty()) {
                minTimeNs = frames.first().t
                maxTimeNs = frames.last().t
                currentTimeNs = (minTimeNs + 500_000_000L).coerceAtMost(maxTimeNs)

                // Detect athlete stance position in early frames
                val earlyFrames = frames.take(minOf(45, frames.size))
                val toeXs = mutableListOf<Double>()
                val heelXs = mutableListOf<Double>()
                val hipXs = mutableListOf<Double>()
                for (f in earlyFrames) {
                    val tL = f.landmarks[PoseLandmarks.LEFT_FOOT_INDEX]
                    val tR = f.landmarks[PoseLandmarks.RIGHT_FOOT_INDEX]
                    val hL = f.landmarks[PoseLandmarks.LEFT_HEEL]
                    val hR = f.landmarks[PoseLandmarks.RIGHT_HEEL]
                    val hipL = f.landmarks[PoseLandmarks.LEFT_HIP]
                    val hipR = f.landmarks[PoseLandmarks.RIGHT_HIP]
                    if (tL.visibility >= 0.25f) toeXs.add(tL.x.toDouble())
                    if (tR.visibility >= 0.25f) toeXs.add(tR.x.toDouble())
                    if (hL.visibility >= 0.25f) heelXs.add(hL.x.toDouble())
                    if (hR.visibility >= 0.25f) heelXs.add(hR.x.toDouble())
                    if (hipL.visibility >= 0.25f) hipXs.add(hipL.x.toDouble())
                    if (hipR.visibility >= 0.25f) hipXs.add(hipR.x.toDouble())
                }

                if (toeXs.isNotEmpty()) {
                    val medToeX = toeXs.sorted()[toeXs.size / 2]
                    val medHeelX = if (heelXs.isNotEmpty()) heelXs.sorted()[heelXs.size / 2] else medToeX

                    // If toes are to the left of heels or standing in right half of video, athlete faces/jumps LEFT
                    if (medToeX < medHeelX || medToeX > 0.50) {
                        jumpDirection = JumpDirection.LEFT
                        linePoint = NormPoint(medToeX, 0.82)
                    } else {
                        jumpDirection = JumpDirection.RIGHT
                        linePoint = NormPoint(medToeX, 0.82)
                    }
                } else if (hipXs.isNotEmpty()) {
                    val medHipX = hipXs.sorted()[hipXs.size / 2]
                    if (medHipX > 0.50) {
                        jumpDirection = JumpDirection.LEFT
                        linePoint = NormPoint(medHipX - 0.05, 0.82)
                    } else {
                        jumpDirection = JumpDirection.RIGHT
                        linePoint = NormPoint(medHipX + 0.05, 0.82)
                    }
                } else {
                    linePoint = NormPoint(0.70, 0.82)
                    jumpDirection = JumpDirection.LEFT
                }
            }
        }
        isLoadingFrame = true
        val bmp = FrameExtractor.frameAt(videoFile, currentTimeNs)
        currentBitmap = bmp
        isLoadingFrame = false
    }

    // Function to reload frame on slider change
    fun seekTo(tNs: Long) {
        currentTimeNs = tNs
        scope.launch {
            isLoadingFrame = true
            val bmp = FrameExtractor.frameAt(videoFile, tNs)
            if (bmp != null) {
                currentBitmap = bmp
            }
            isLoadingFrame = false
        }
    }

    // Validation checks
    val isReadyToEvaluate = if (isHeightOnly) {
        linePoint != null
    } else {
        linePoint != null && markerPoint != null && abs((markerPoint!!.x - linePoint!!.x)) > 0.05
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Set Takeoff Line",
                            style = PramaanTypography.titleMedium,
                            color = PramaanOnSurface
                        )
                        Text(
                            text = if (isHeightOnly) "Mode: No Tape (Athlete Height Scaled)" else "Mode: Tape Measure Calibration",
                            style = PramaanTypography.bodySmall,
                            color = PramaanOutline
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    if (isAnalyzing) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = PramaanSaffron,
                                strokeWidth = 2.5.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = analyzeStepText,
                                style = PramaanTypography.bodyMedium,
                                color = PramaanOnSurface
                            )
                        }
                    } else {
                        PramaanButton(
                            text = if (isHeightOnly) "Confirm Start Line & Evaluate Jump" else "Confirm Ground Tape & Evaluate",
                            icon = Icons.Default.Check,
                            onClick = {
                                isAnalyzing = true
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        analyzeStepText = "Saving start line calibration..."
                                        val calibBmp = currentBitmap
                                        val calibFile = container.sessionStore.getCalibFrameFile(sessionId)
                                        if (calibBmp != null) {
                                            FileOutputStream(calibFile).use { out ->
                                                calibBmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
                                            }
                                        }

                                        analyzeStepText = "Analyzing jump trajectory..."
                                        val calib = GroundCalibration(
                                            mode = if (isHeightOnly) CalibMode.HEIGHT_ONLY else CalibMode.MARKER,
                                            frameW = calibBmp?.width ?: 1280,
                                            frameH = calibBmp?.height ?: 720,
                                            calibFrameTNs = currentTimeNs,
                                            line = linePoint ?: NormPoint(0.70, 0.80),
                                            marker = if (isHeightOnly) null else markerPoint,
                                            markerCm = if (isHeightOnly) null else markerCm,
                                            jumpDirection = jumpDirection
                                        )

                                        val frames = if (framesFile.exists()) {
                                            framesFile.reader().use { PoseFrameJsonl.readAll(it) }
                                        } else emptyList()

                                        val prof = container.authStore.getProfile() ?: app.pramaan.api.dto.AccountProfileDto(
                                            id = "ATH-DEMO",
                                            role = "ATHLETE",
                                            name = "Athlete",
                                            sex = "M",
                                            heightCm = athleteHeightCm,
                                            weightKg = 55.0
                                        )

                                        val athleteInfo = AthleteInfo(
                                            heightCm = prof.heightCm ?: athleteHeightCm,
                                            ageYears = prof.age ?: 16,
                                            sex = prof.sex ?: "M"
                                        )

                                        val result = BroadJumpAnalyzer.analyze(
                                            track = frames,
                                            calib = calib,
                                            athlete = athleteInfo,
                                            params = BroadJumpParams(requireTapeMarker = false)
                                        )

                                        analyzeStepText = "Rendering landing stills..."
                                        val stillsDir = container.sessionStore.getStillsDir(sessionId)
                                        stillsDir.mkdirs()
                                        result.attempts.forEach { att ->
                                            val stillBmp = FrameExtractor.frameAt(videoFile, att.contact.tNs)
                                            if (stillBmp != null) {
                                                val outFile = File(stillsDir, "att${att.index}_landing.jpg")
                                                StillRenderer.renderLandingStill(stillBmp, calib, att, outFile)
                                            }
                                        }

                                        analyzeStepText = "Cryptographically sealing session..."
                                        val fps = container.poseEngine.measuredFps
                                        container.sealer.sealSession(
                                            sessionId = sessionId,
                                            testType = TestType.BROAD_JUMP,
                                            athleteProfile = prof,
                                            fps = fps,
                                            calibration = calib
                                        )
                                    }
                                    onCalibrationComplete(sessionId)
                                }
                            },
                            enabled = isReadyToEvaluate
                        )
                    }
                }
            }
        },
        containerColor = PramaanSurface
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // 1. Mode Switcher (Two clear, friendly tabs)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFF1F5F9))
                    .padding(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isHeightOnly) PramaanNavy else Color.Transparent)
                        .clickable {
                            isHeightOnly = true
                            activePoint = ActivePoint.LINE
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "⚡ No Tape (Athlete Height)",
                        style = PramaanTypography.labelMedium,
                        fontWeight = if (isHeightOnly) FontWeight.Bold else FontWeight.Normal,
                        color = if (isHeightOnly) Color.White else PramaanOnSurface
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (!isHeightOnly) Color(0xFF2563EB) else Color.Transparent)
                        .clickable {
                            isHeightOnly = false
                            if (markerPoint == null) {
                                val offset = if (jumpDirection == JumpDirection.LEFT) -0.30 else 0.30
                                markerPoint = NormPoint(
                                    ((linePoint?.x ?: 0.5) + offset).coerceIn(0.05, 0.95),
                                    linePoint?.y ?: 0.8
                                )
                            }
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "📏 With Tape Measure",
                        style = PramaanTypography.labelMedium,
                        fontWeight = if (!isHeightOnly) FontWeight.Bold else FontWeight.Normal,
                        color = if (!isHeightOnly) Color.White else PramaanOnSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Video Frame Canvas
            val bmpAspect = if (currentBitmap != null && currentBitmap!!.height > 0) {
                currentBitmap!!.width.toFloat() / currentBitmap!!.height.toFloat()
            } else (16f / 9f)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(bmpAspect.coerceIn(0.56f, 1.8f))
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black)
            ) {
                if (currentBitmap != null) {
                    val bmp = currentBitmap!!

                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(activePoint, isHeightOnly) {
                                detectTapGestures { tapOffset ->
                                    val canvasW = size.width.toFloat()
                                    val canvasH = size.height.toFloat()
                                    val normX = (tapOffset.x / canvasW).coerceIn(0f, 1f).toDouble()
                                    val normY = (tapOffset.y / canvasH).coerceIn(0f, 1f).toDouble()

                                    if (isHeightOnly || activePoint == ActivePoint.LINE) {
                                        linePoint = NormPoint(normX, normY)
                                        if (!isHeightOnly && markerPoint == null) {
                                            activePoint = ActivePoint.MARKER
                                        }
                                    } else {
                                        markerPoint = NormPoint(normX, normY)
                                    }
                                }
                            }
                            .pointerInput(activePoint, isHeightOnly) {
                                detectDragGestures { change, _ ->
                                    val canvasW = size.width.toFloat()
                                    val canvasH = size.height.toFloat()
                                    val normX = (change.position.x / canvasW).coerceIn(0f, 1f).toDouble()
                                    val normY = (change.position.y / canvasH).coerceIn(0f, 1f).toDouble()

                                    if (isHeightOnly || activePoint == ActivePoint.LINE) {
                                        linePoint = NormPoint(normX, normY)
                                    } else {
                                        markerPoint = NormPoint(normX, normY)
                                    }
                                }
                            }
                    ) {
                        val canvasW = size.width
                        val canvasH = size.height

                        // Draw background frame image
                        drawImage(
                            image = bmp.asImageBitmap(),
                            dstOffset = IntOffset.Zero,
                            dstSize = IntSize(canvasW.toInt(), canvasH.toInt())
                        )

                        fun normToCanvas(p: NormPoint): Offset {
                            return Offset((p.x * canvasW).toFloat(), (p.y * canvasH).toFloat())
                        }

                        // Draw Takeoff Line (Orange)
                        linePoint?.let { lp ->
                            val c = normToCanvas(lp)
                            // Thick guide line
                            drawLine(
                                color = PramaanSaffron,
                                start = Offset(c.x, 0f),
                                end = Offset(c.x, canvasH),
                                strokeWidth = 5f
                            )
                            // Circular grab handle
                            drawCircle(color = PramaanSaffron, radius = 18f, center = c)
                            drawCircle(color = Color.White, radius = 8f, center = c)

                            // Arrow along jump direction
                            val arrowDir = if (jumpDirection == JumpDirection.LEFT) -1f else 1f
                            val arrowStartX = c.x + arrowDir * 24f
                            val arrowEndX = c.x + arrowDir * 70f
                            val arrowY = c.y - 25f
                            drawLine(
                                color = PramaanSaffron,
                                start = Offset(arrowStartX, arrowY),
                                end = Offset(arrowEndX, arrowY),
                                strokeWidth = 4f
                            )
                            drawLine(
                                color = PramaanSaffron,
                                start = Offset(arrowEndX, arrowY),
                                end = Offset(arrowEndX - arrowDir * 14f, arrowY - 10f),
                                strokeWidth = 4f
                            )
                            drawLine(
                                color = PramaanSaffron,
                                start = Offset(arrowEndX, arrowY),
                                end = Offset(arrowEndX - arrowDir * 14f, arrowY + 10f),
                                strokeWidth = 4f
                            )
                        }

                        // Draw Tape Marker (Blue) if in Tape mode
                        if (!isHeightOnly) {
                            markerPoint?.let { mp ->
                                val c = normToCanvas(mp)
                                drawLine(
                                    color = Color(0xFF2563EB),
                                    start = Offset(c.x, 0f),
                                    end = Offset(c.x, canvasH),
                                    strokeWidth = 4f
                                )
                                drawCircle(color = Color(0xFF2563EB), radius = 16f, center = c)
                                drawCircle(color = Color.White, radius = 7f, center = c)
                            }

                            if (linePoint != null && markerPoint != null) {
                                val cLine = normToCanvas(linePoint!!)
                                val cMark = normToCanvas(markerPoint!!)
                                drawLine(
                                    color = PramaanEmerald,
                                    start = cLine,
                                    end = cMark,
                                    strokeWidth = 3f
                                )
                            }
                        }
                    }
                }

                if (isLoadingFrame) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color(0x66000000)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(32.dp))
                    }
                }

                // Instruction Overlay Badge on Canvas
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isHeightOnly) "Tap or drag line to athlete's toes" else "Tap 1: Line, Tap 2: Tape mark",
                        color = Color.White,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3. Jump Direction Control (Super easy Left vs Right toggle)
            PramaanCard {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.DirectionsRun,
                                contentDescription = null,
                                tint = PramaanSaffron,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Athlete Jump Direction",
                                style = PramaanTypography.labelMedium,
                                color = PramaanOnSurface
                            )
                        }

                        // Segmented Left / Right Buttons
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFF1F5F9))
                                .padding(2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (jumpDirection == JumpDirection.LEFT) PramaanSaffron else Color.Transparent)
                                    .clickable { jumpDirection = JumpDirection.LEFT }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "⇦ Left",
                                    color = if (jumpDirection == JumpDirection.LEFT) Color.White else PramaanOnSurface,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (jumpDirection == JumpDirection.RIGHT) PramaanSaffron else Color.Transparent)
                                    .clickable { jumpDirection = JumpDirection.RIGHT }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "Right ⇨",
                                    color = if (jumpDirection == JumpDirection.RIGHT) Color.White else PramaanOnSurface,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    if (isHeightOnly) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Calibrating distance using athlete's registered height (${athleteHeightCm.toInt()} cm). No tape measure needed.",
                            style = PramaanTypography.bodySmall,
                            color = PramaanOutline
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 4. In Tape Mode: Point Selectors & Marker Length Chips
            if (!isHeightOnly) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = activePoint == ActivePoint.LINE,
                        onClick = { activePoint = ActivePoint.LINE },
                        label = { Text("1. Takeoff Line (0 cm)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFFFEDD5),
                            selectedLabelColor = PramaanSaffron
                        )
                    )

                    FilterChip(
                        selected = activePoint == ActivePoint.MARKER,
                        onClick = { activePoint = ActivePoint.MARKER },
                        label = { Text("2. Tape Mark (${markerCm.toInt()} cm)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFDBEAFE),
                            selectedLabelColor = Color(0xFF2563EB)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(text = "Tape Mark:", style = PramaanTypography.labelSmall, color = PramaanOutline)
                    listOf(100.0, 150.0, 200.0, 250.0, 300.0).forEach { cmVal ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (markerCm == cmVal) Color(0xFF2563EB) else Color(0xFFF1F5F9))
                                .clickable { markerCm = cmVal }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "${cmVal.toInt()} cm",
                                color = if (markerCm == cmVal) Color.White else PramaanOnSurface,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
            }

            // 5. Video Frame Scrubber Slider
            PramaanCard {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Videocam,
                                contentDescription = null,
                                tint = PramaanNavy,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Select Starting Stance Frame",
                                style = PramaanTypography.labelMedium,
                                color = PramaanOnSurface
                            )
                        }

                        val timeSec = String.format("%.2f s", (currentTimeNs - minTimeNs) / 1e9)
                        Text(
                            text = timeSec,
                            style = PramaanTypography.labelSmall,
                            color = PramaanOutline
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Slider(
                        value = currentTimeNs.toFloat(),
                        onValueChange = { seekTo(it.toLong()) },
                        valueRange = minTimeNs.toFloat()..maxTimeNs.toFloat(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = PramaanSaffron,
                            activeTrackColor = PramaanSaffron
                        )
                    )

                    Text(
                        text = "Slide until athlete is standing still at the take-off line before jumping.",
                        style = PramaanTypography.bodySmall,
                        color = PramaanOutline
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
