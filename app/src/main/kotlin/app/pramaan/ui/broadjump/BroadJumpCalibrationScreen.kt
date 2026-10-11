package app.pramaan.ui.broadjump

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.ZoomIn
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
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pramaan.AppContainer
import app.pramaan.core.manifest.PoseFrameJsonl
import app.pramaan.core.model.TestType
import app.pramaan.core.scoring.broadjump.AthleteInfo
import app.pramaan.core.scoring.broadjump.BroadJumpAnalyzer
import app.pramaan.core.scoring.broadjump.CalibMode
import app.pramaan.core.scoring.broadjump.GroundCalibration
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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

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

    // Calibration points
    var activePoint by remember { mutableStateOf(ActivePoint.LINE) }
    var linePoint by remember { mutableStateOf<NormPoint?>(null) }
    var markerPoint by remember { mutableStateOf<NormPoint?>(null) }
    var markerCm by remember { mutableDoubleStateOf(200.0) }
    var isHeightOnly by remember { mutableStateOf(false) }

    // Video frame scrubber state
    var minTimeNs by remember { mutableLongStateOf(0L) }
    var maxTimeNs by remember { mutableLongStateOf(10_000_000_000L) }
    var currentTimeNs by remember { mutableLongStateOf(500_000_000L) }
    var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoadingFrame by remember { mutableStateOf(false) }

    // Zoom & Pan state
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    // Sealing / Analysis progress
    var isAnalyzing by remember { mutableStateOf(false) }
    var analyzeStepText by remember { mutableStateOf("") }

    // 1. Initial frame load (read first frame timestamp from frames.jsonl)
    LaunchedEffect(sessionId) {
        withContext(Dispatchers.IO) {
            val frames = if (framesFile.exists()) {
                framesFile.reader().use { PoseFrameJsonl.readAll(it) }
            } else emptyList()

            if (frames.isNotEmpty()) {
                minTimeNs = frames.first().t
                maxTimeNs = frames.last().t
                // Default to ~0.5s after start (athlete stationary)
                currentTimeNs = (minTimeNs + 500_000_000L).coerceAtMost(maxTimeNs)
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

    // Nudge active point by 1 pixel
    fun nudge(dx: Float, dy: Float) {
        val bmp = currentBitmap ?: return
        val w = bmp.width.toFloat()
        val h = bmp.height.toFloat()
        val dxNorm = dx / (w * zoomScale)
        val dyNorm = dy / (h * zoomScale)

        if (activePoint == ActivePoint.LINE) {
            val p = linePoint ?: NormPoint(0.3, 0.8)
            linePoint = NormPoint(
                (p.x + dxNorm).coerceIn(0.0, 1.0),
                (p.y + dyNorm).coerceIn(0.0, 1.0)
            )
        } else {
            val p = markerPoint ?: NormPoint(0.7, 0.8)
            markerPoint = NormPoint(
                (p.x + dxNorm).coerceIn(0.0, 1.0),
                (p.y + dyNorm).coerceIn(0.0, 1.0)
            )
        }
    }

    // Live validation checks
    val bmpW = currentBitmap?.width ?: 1280
    val bmpH = currentBitmap?.height ?: 720
    val linePx = linePoint?.let { Offset((it.x * bmpW).toFloat(), (it.y * bmpH).toFloat()) }
    val markPx = markerPoint?.let { Offset((it.x * bmpW).toFloat(), (it.y * bmpH).toFloat()) }

    val distancePx = if (linePx != null && markPx != null) {
        hypot((markPx.x - linePx.x).toDouble(), (markPx.y - linePx.y).toDouble())
    } else null

    val tiltDeg = if (linePx != null && markPx != null) {
        val dy = (markPx.y - linePx.y).toDouble()
        val dx = (markPx.x - linePx.x).toDouble()
        abs(atan2(dy, dx) * 180.0 / PI)
    } else 0.0

    val isAhead = if (linePx != null && markPx != null) markPx.x > linePx.x else false
    val isMarkerValid = if (isHeightOnly) {
        linePoint != null
    } else {
        linePoint != null && markerPoint != null && isAhead && tiltDeg <= 20.0 && (distancePx ?: 0.0) >= 50.0
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Calibrate Ground Tape",
                            style = PramaanTypography.titleMedium,
                            color = PramaanOnSurface
                        )
                        Text(
                            text = if (isHeightOnly) "Mode: Height Only (Approx)" else "Tap line & ${markerCm.toInt()} cm mark",
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
                            text = "Confirm & Analyze Jumps",
                            icon = Icons.Default.Check,
                            onClick = {
                                isAnalyzing = true
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        analyzeStepText = "Saving calibration frame..."
                                        // 1. Save calibration frame JPG
                                        val calibBmp = currentBitmap
                                        val calibFile = container.sessionStore.getCalibFrameFile(sessionId)
                                        if (calibBmp != null) {
                                            FileOutputStream(calibFile).use { out ->
                                                calibBmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
                                            }
                                        }

                                        analyzeStepText = "Analyzing pose trajectory..."
                                        val calib = GroundCalibration(
                                            mode = if (isHeightOnly) CalibMode.HEIGHT_ONLY else CalibMode.MARKER,
                                            frameW = calibBmp?.width ?: 1280,
                                            frameH = calibBmp?.height ?: 720,
                                            calibFrameTNs = currentTimeNs,
                                            line = linePoint ?: NormPoint(0.25, 0.8),
                                            marker = if (isHeightOnly) null else markerPoint,
                                            markerCm = if (isHeightOnly) null else markerCm
                                        )

                                        val frames = if (framesFile.exists()) {
                                            framesFile.reader().use { PoseFrameJsonl.readAll(it) }
                                        } else emptyList()

                                        val prof = container.authStore.getProfile() ?: app.pramaan.api.dto.AccountProfileDto(
                                            id = "ATH-DEMO",
                                            role = "ATHLETE",
                                            name = "Athlete",
                                            sex = "F",
                                            heightCm = 165.0,
                                            weightKg = 55.0
                                        )

                                        val athleteInfo = AthleteInfo(
                                            heightCm = prof.heightCm ?: 165.0,
                                            ageYears = prof.age ?: 16,
                                            sex = prof.sex ?: "F"
                                        )

                                        val result = BroadJumpAnalyzer.analyze(frames, calib, athleteInfo)

                                        analyzeStepText = "Rendering landing stills..."
                                        val stillsDir = container.sessionStore.getStillsDir(sessionId)
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
                            enabled = isMarkerValid
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
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // 1. Interactive Video Frame Canvas with Zoom & Tap
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black)
            ) {
                if (currentBitmap != null) {
                    val bmp = currentBitmap!!
                    val transformState = rememberTransformableState { zoomChange, offsetChange, _ ->
                        zoomScale = (zoomScale * zoomChange).coerceIn(1f, 6f)
                        panOffset += offsetChange
                    }

                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .transformable(state = transformState)
                            .pointerInput(activePoint, zoomScale, panOffset) {
                                detectTapGestures { tapOffset ->
                                    val canvasW = size.width.toFloat()
                                    val canvasH = size.height.toFloat()

                                    // Invert transform to get normalized coordinates [0, 1]
                                    val normX = ((tapOffset.x - panOffset.x) / (canvasW * zoomScale)).coerceIn(0f, 1f)
                                    val normY = ((tapOffset.y - panOffset.y) / (canvasH * zoomScale)).coerceIn(0f, 1f)

                                    if (activePoint == ActivePoint.LINE) {
                                        linePoint = NormPoint(normX.toDouble(), normY.toDouble())
                                        if (!isHeightOnly && markerPoint == null) {
                                            activePoint = ActivePoint.MARKER
                                        }
                                    } else {
                                        markerPoint = NormPoint(normX.toDouble(), normY.toDouble())
                                    }
                                }
                            }
                    ) {
                        val canvasW = size.width
                        val canvasH = size.height

                        // Draw background frame image
                        drawImage(
                            image = bmp.asImageBitmap(),
                            dstOffset = IntOffset(panOffset.x.toInt(), panOffset.y.toInt()),
                            dstSize = IntSize((canvasW * zoomScale).toInt(), (canvasH * zoomScale).toInt())
                        )

                        // Helper to convert normalized coordinate to canvas space
                        fun normToCanvas(p: NormPoint): Offset {
                            return Offset(
                                (p.x * canvasW * zoomScale + panOffset.x).toFloat(),
                                (p.y * canvasH * zoomScale + panOffset.y).toFloat()
                            )
                        }

                        // Draw Takeoff Line (Orange Crosshair)
                        linePoint?.let { lp ->
                            val c = normToCanvas(lp)
                            // Vertical guide line
                            drawLine(
                                color = PramaanSaffron,
                                start = Offset(c.x, 0f),
                                end = Offset(c.x, canvasH),
                                strokeWidth = 2f
                            )
                            drawCircle(color = PramaanSaffron, radius = 10f, center = c)
                            drawCircle(color = Color.White, radius = 4f, center = c)
                        }

                        // Draw Tape Marker (Blue Crosshair)
                        markerPoint?.let { mp ->
                            val c = normToCanvas(mp)
                            drawLine(
                                color = Color(0xFF2563EB),
                                start = Offset(c.x, 0f),
                                end = Offset(c.x, canvasH),
                                strokeWidth = 2f
                            )
                            drawCircle(color = Color(0xFF2563EB), radius = 10f, center = c)
                            drawCircle(color = Color.White, radius = 4f, center = c)
                        }

                        // Draw Connecting Calibration Axis Line
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

                if (isLoadingFrame) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color(0x66000000)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(32.dp))
                    }
                }

                // Zoom Level Badge
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x99000000))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${String.format("%.1f", zoomScale)}x Zoom",
                        color = Color.White,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Point Selection Steps & Nudge Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = activePoint == ActivePoint.LINE,
                        onClick = { activePoint = ActivePoint.LINE },
                        label = { Text("1. Takeoff Line (0 cm)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFFFEDD5),
                            selectedLabelColor = PramaanSaffron
                        )
                    )

                    if (!isHeightOnly) {
                        FilterChip(
                            selected = activePoint == ActivePoint.MARKER,
                            onClick = { activePoint = ActivePoint.MARKER },
                            label = { Text("2. Marker (${markerCm.toInt()} cm)") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFDBEAFE),
                                selectedLabelColor = Color(0xFF2563EB)
                            )
                        )
                    }
                }

                // 1-Pixel Nudge Pad
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFE2E8F0))
                        .padding(2.dp)
                ) {
                    IconButton(onClick = { nudge(0f, -1f) }, modifier = Modifier.size(28.dp)) {
                        Icon(imageVector = Icons.Default.ArrowUpward, contentDescription = "Up", modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = { nudge(0f, 1f) }, modifier = Modifier.size(28.dp)) {
                        Icon(imageVector = Icons.Default.ArrowDownward, contentDescription = "Down", modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = { nudge(-1f, 0f) }, modifier = Modifier.size(28.dp)) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Left", modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = { nudge(1f, 0f) }, modifier = Modifier.size(28.dp)) {
                        Icon(imageVector = Icons.Default.ArrowForward, contentDescription = "Right", modifier = Modifier.size(16.dp))
                    }
                }
            }

            // Marker Length Chips
            if (!isHeightOnly) {
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
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3. Frame Scrubber Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Pick Clear Frame:",
                    style = PramaanTypography.labelSmall,
                    color = PramaanOutline
                )
                Spacer(modifier = Modifier.width(8.dp))
                Slider(
                    value = currentTimeNs.toFloat(),
                    onValueChange = { seekTo(it.toLong()) },
                    valueRange = minTimeNs.toFloat()..maxTimeNs.toFloat(),
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = PramaanSaffron,
                        activeTrackColor = PramaanSaffron
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 4. Live Validity Diagnostics Card
            PramaanCard {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Calibration Validation",
                            style = PramaanTypography.labelLarge,
                            color = PramaanOnSurface
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isMarkerValid) Color(0xFFDCFCE7) else Color(0xFFFFEDD5))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = if (isMarkerValid) "VALID ✓" else "ADJUST POINTS",
                                style = PramaanTypography.labelSmall,
                                color = if (isMarkerValid) PramaanEmerald else PramaanSaffron
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (isHeightOnly) {
                        Text(
                            text = "Height-only fallback enabled. Line position set. Result will be marked APPROX (not certifiable).",
                            style = PramaanTypography.bodySmall,
                            color = PramaanOutline
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Span: ${distancePx?.toInt() ?: 0} px",
                                style = PramaanTypography.bodySmall,
                                color = PramaanOutline
                            )
                            Text(
                                text = "Tilt: ${String.format("%.1f", tiltDeg)}° ${if (tiltDeg <= 20.0) "✓" else "✕ (>20°)"}",
                                style = PramaanTypography.bodySmall,
                                color = if (tiltDeg <= 20.0) PramaanEmerald else PramaanError
                            )
                            Text(
                                text = if (isAhead) "Ahead ✓" else "Behind ✕",
                                style = PramaanTypography.bodySmall,
                                color = if (isAhead) PramaanEmerald else PramaanError
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Secondary fallback switch
                    Text(
                        text = if (isHeightOnly) "Switch back to tape marker calibration" else "No tape? Use height-only fallback (approximate)",
                        style = PramaanTypography.labelSmall,
                        color = Color(0xFF2563EB),
                        modifier = Modifier.clickable {
                            isHeightOnly = !isHeightOnly
                            if (isHeightOnly) activePoint = ActivePoint.LINE
                        }
                    )
                }
            }
        }
    }
}
