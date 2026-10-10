package app.pramaan.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.SlowMotionVideo
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.pramaan.AppContainer
import app.pramaan.core.manifest.PoseFrameJsonl
import app.pramaan.core.model.Landmark
import app.pramaan.core.model.PoseFrame
import app.pramaan.core.model.TestType
import app.pramaan.data.AthleteEntity
import app.pramaan.ui.components.PramaanButton
import app.pramaan.ui.components.PramaanCard
import app.pramaan.ui.components.PramaanOutlinedButton
import app.pramaan.ui.theme.PramaanEmerald
import app.pramaan.ui.theme.PramaanError
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
import java.io.FileWriter
import java.io.PrintWriter
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    container: AppContainer,
    onNavigateToResult: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isProcessing by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var statusText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun processVideoUri(uri: Uri) {
        isProcessing = true
        progress = 0f
        statusText = "Importing video file..."
        errorMessage = null

        scope.launch {
            try {
                val sessionId = UUID.randomUUID().toString()
                val sessionDir = container.sessionStore.getSessionDir(sessionId)
                sessionDir.mkdirs()

                val localVideoFile = container.sessionStore.getVideoFile(sessionId)
                val framesFile = container.sessionStore.getFramesFile(sessionId)

                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(localVideoFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }

                statusText = "Running MediaPipe pose estimation..."
                val frames = container.videoFilePoseRunner.processVideoFile(
                    videoFile = localVideoFile,
                    outputFramesFile = framesFile,
                    onProgress = { p ->
                        progress = p
                        statusText = "Evaluating frames: ${(p * 100).toInt()}%"
                    }
                )

                statusText = "Evaluating physics & sealing session..."
                val athletes = container.athleteDao.getAll()
                val athlete = athletes.firstOrNull() ?: AthleteEntity(
                    id = "ATH-DEMO-01",
                    name = "Imported Athlete",
                    initials = "IA",
                    age = 15,
                    sex = "F",
                    heightCm = 155.0,
                    weightKg = 50.0,
                    guardianConsent = true
                )

                container.sealer.sealSession(
                    sessionId = sessionId,
                    testType = TestType.VERTICAL_JUMP,
                    athlete = athlete,
                    fps = 30,
                    source = "FILE"
                )

                isProcessing = false
                onNavigateToResult(sessionId)
            } catch (e: Exception) {
                isProcessing = false
                errorMessage = "Error processing video: ${e.message}"
            }
        }
    }

    // Demo Step 8 shortcut: Generate synthetic slowed jump clip for fraud demo
    fun generateSyntheticSlowedJump() {
        isProcessing = true
        progress = 0.5f
        statusText = "Generating 0.5x slowed jump sequence..."
        errorMessage = null

        scope.launch {
            try {
                val sessionId = UUID.randomUUID().toString()
                val sessionDir = container.sessionStore.getSessionDir(sessionId)
                sessionDir.mkdirs()

                val videoFile = container.sessionStore.getVideoFile(sessionId)
                val framesFile = container.sessionStore.getFramesFile(sessionId)

                withContext(Dispatchers.IO) {
                    // Create dummy video file
                    videoFile.writeBytes(ByteArray(1024) { 0x42 })

                    // Generate synthetic 2x slowed jump frames (1.1s flight time, g ~ 2.45 m/s^2)
                    val fps = 30
                    val dtNs = (1_000_000_000L / fps)
                    val frames = mutableListOf<PoseFrame>()
                    val standingY = 0.60f
                    val apexY = 0.23f
                    val flightFrames = 33 // ~1.1s flight duration (slowed)

                    var t = 0L
                    val writer = PrintWriter(FileWriter(framesFile, false))

                    // 15 frames standing
                    for (i in 0 until 15) {
                        val frame = makeSyntheticFrame(t, standingY)
                        frames.add(frame)
                        writer.println(PoseFrameJsonl.writeLine(frame))
                        t += dtNs
                    }

                    // Flight frames (parabola)
                    for (i in 0 until flightFrames) {
                        val frac = i.toFloat() / (flightFrames - 1)
                        val y = standingY - (standingY - apexY) * 4f * frac * (1f - frac)
                        val frame = makeSyntheticFrame(t, y)
                        frames.add(frame)
                        writer.println(PoseFrameJsonl.writeLine(frame))
                        t += dtNs
                    }

                    // 15 frames post-landing
                    for (i in 0 until 15) {
                        val frame = makeSyntheticFrame(t, standingY)
                        frames.add(frame)
                        writer.println(PoseFrameJsonl.writeLine(frame))
                        t += dtNs
                    }

                    writer.flush()
                    writer.close()
                }

                statusText = "Running physics verification..."
                val athletes = container.athleteDao.getAll()
                val athlete = athletes.firstOrNull() ?: AthleteEntity(
                    id = "ATH-DEMO-01",
                    name = "Demo Athlete (Slowed Test)",
                    initials = "DA",
                    age = 15,
                    sex = "F",
                    heightCm = 155.0,
                    weightKg = 50.0,
                    guardianConsent = true
                )

                container.sealer.sealSession(
                    sessionId = sessionId,
                    testType = TestType.VERTICAL_JUMP,
                    athlete = athlete,
                    fps = 30,
                    source = "FILE"
                )

                isProcessing = false
                onNavigateToResult(sessionId)
            } catch (e: Exception) {
                isProcessing = false
                errorMessage = "Error generating synthetic clip: ${e.message}"
            }
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { processVideoUri(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Import & Audit Video",
                            style = PramaanTypography.titleMedium,
                            color = PramaanOnSurface
                        )
                        Text(
                            text = "OFFLINE FRAUD AUDITOR",
                            style = PramaanTypography.labelSmall,
                            color = PramaanOnSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = PramaanOnSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = PramaanSurface
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            item {
                PramaanCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEEF2FF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.VideoLibrary,
                                contentDescription = null,
                                tint = Color(0xFF4F46E5),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Evaluate Pre-Recorded Video",
                                style = PramaanTypography.titleMedium,
                                color = PramaanOnSurface
                            )
                            Text(
                                text = "Tests source integrity & real-world physics consistency",
                                style = PramaanTypography.labelSmall,
                                color = PramaanOutline
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Import any MP4 clip of a jump or sit-up assessment. Pramaan will reconstruct frame-by-frame biomechanics and test if gravity matches 9.8 m/s².",
                        style = PramaanTypography.bodyMedium,
                        color = PramaanOutline
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    PramaanButton(
                        text = "Pick Video from Gallery",
                        icon = Icons.Default.UploadFile,
                        onClick = { launcher.launch("video/mp4") },
                        enabled = !isProcessing
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Live Demo Step 8 Fraud Simulation Button
                    PramaanOutlinedButton(
                        text = "Test 0.5x Slowed Cheat Clip",
                        icon = Icons.Default.SlowMotionVideo,
                        onClick = { generateSyntheticSlowedJump() },
                        enabled = !isProcessing
                    )

                    errorMessage?.let { err ->
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(text = err, style = PramaanTypography.bodySmall, color = PramaanError)
                    }
                }
            }

            if (isProcessing) {
                item {
                    PramaanCard {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                color = PramaanSaffron,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = statusText,
                                style = PramaanTypography.titleMedium,
                                color = PramaanOnSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = PramaanSaffron,
                                trackColor = Color(0xFFE2E8F0)
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

private fun makeSyntheticFrame(tNs: Long, hipY: Float): PoseFrame {
    val landmarks = (0 until 33).map { idx ->
        when (idx) {
            23, 24 -> Landmark(x = 0.5f, y = hipY, visibility = 0.95f)
            27, 28 -> Landmark(x = 0.5f, y = 0.90f, visibility = 0.95f)
            11, 12 -> Landmark(x = 0.5f, y = hipY - 0.25f, visibility = 0.95f)
            0 -> Landmark(x = 0.5f, y = hipY - 0.35f, visibility = 0.95f)
            else -> Landmark(x = 0.5f, y = hipY, visibility = 0.8f)
        }
    }
    return PoseFrame(t = tNs, landmarks = landmarks)
}
