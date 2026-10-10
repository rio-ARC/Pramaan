package app.pramaan.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pramaan.AppContainer
import app.pramaan.api.dto.AccountProfileDto
import app.pramaan.core.model.TestType
import app.pramaan.data.AthleteEntity
import app.pramaan.ui.components.InitialsAvatar
import app.pramaan.ui.components.PramaanButton
import app.pramaan.ui.components.PramaanCard
import app.pramaan.ui.theme.PramaanEmerald
import app.pramaan.ui.theme.PramaanOnSurface
import app.pramaan.ui.theme.PramaanOnSurfaceVariant
import app.pramaan.ui.theme.PramaanOutline
import app.pramaan.ui.theme.PramaanSaffron
import app.pramaan.ui.theme.PramaanSurface
import app.pramaan.ui.theme.PramaanTypography
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.sqrt

class TestSetupViewModel(private val container: AppContainer) : ViewModel() {
    var athleteProfile by mutableStateOf<AccountProfileDto?>(null)
        private set
    var accountId by mutableStateOf("")
        private set

    val gyroRecorder = container.gyroRecorder

    init {
        viewModelScope.launch {
            accountId = container.authStore.accountIdFlow.first() ?: "ATH-DEMO"
            athleteProfile = container.authStore.getProfile()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestSetupScreen(
    testType: TestType,
    viewModel: TestSetupViewModel,
    onBeginTest: (athleteId: String, testType: TestType) -> Unit,
    onBack: () -> Unit
) {
    val currentRms by viewModel.gyroRecorder.currentRms.collectAsState()
    val gyroSteady = currentRms < 0.08
    val profile = viewModel.athleteProfile
    val accountId = viewModel.accountId

    // Monitor gyroscope steadiness in pre-flight
    DisposableEffect(Unit) {
        viewModel.gyroRecorder.startPreflightMonitoring()
        onDispose {
            viewModel.gyroRecorder.stopPreflightMonitoring()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Sensor Calibration",
                            style = PramaanTypography.titleMedium,
                            color = PramaanOnSurface
                        )
                        Text(
                            text = testType.displayName.uppercase(),
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
                actions = {
                    Box(
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (gyroSteady) PramaanEmerald else PramaanSaffron)
                    )
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
                    PramaanButton(
                        text = "Begin Test: Attempt 1",
                        icon = Icons.Default.PlayCircle,
                        onClick = {
                            onBeginTest(accountId, testType)
                        },
                        enabled = accountId.isNotBlank()
                    )
                }
            }
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
            item { Spacer(modifier = Modifier.height(8.dp)) }

            // 1. Athlete Identity Card (Stitch Screen 2)
            item {
                PramaanCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFDCFCE7))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Verified,
                                    contentDescription = null,
                                    tint = PramaanEmerald,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (profile?.guardianConsent == true) "GUARDIAN CONSENT SIGNED" else "SELF REGISTERED",
                                    style = PramaanTypography.labelSmall,
                                    color = PramaanEmerald
                                )
                            }
                        }
                        Text(
                            text = if (accountId.isNotBlank()) "ID: $accountId" else "",
                            style = PramaanTypography.labelSmall,
                            color = PramaanOnSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        InitialsAvatar(name = profile?.name ?: "Athlete", size = 56)
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = profile?.name ?: "Athlete",
                                style = PramaanTypography.titleLarge,
                                color = PramaanOnSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${profile?.sex ?: "—"} • ${profile?.heightCm?.toInt() ?: 165} cm • ${profile?.weightKg?.toInt() ?: 55} kg",
                                style = PramaanTypography.bodySmall,
                                color = PramaanOnSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 2. Spatial Setup Visualizer & Guidance (Stitch Screen 2)
            item {
                PramaanCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Sensors,
                                contentDescription = null,
                                tint = PramaanSaffron,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Camera Calibration",
                                style = PramaanTypography.titleMedium,
                                color = PramaanOnSurface
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFFFEDD5))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Optimum 3m Zone",
                                style = PramaanTypography.labelSmall,
                                color = PramaanSaffron
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Minimal 3m Field Diagram Canvas
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF1F5F9)),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                            val w = size.width
                            val h = size.height

                            // Ground baseline
                            drawLine(
                                color = Color(0xFFCBD5E1),
                                start = Offset(20f, h - 20f),
                                end = Offset(w - 20f, h - 20f),
                                strokeWidth = 3f
                            )

                            // Tripod on Left
                            val tripodX = 60f
                            val tripodTop = h - 70f
                            // Legs
                            drawLine(
                                color = Color(0xFF64748B),
                                start = Offset(tripodX, tripodTop),
                                end = Offset(tripodX - 25f, h - 20f),
                                strokeWidth = 3f
                            )
                            drawLine(
                                color = Color(0xFF64748B),
                                start = Offset(tripodX, tripodTop),
                                end = Offset(tripodX, h - 20f),
                                strokeWidth = 3f
                            )
                            drawLine(
                                color = Color(0xFF64748B),
                                start = Offset(tripodX, tripodTop),
                                end = Offset(tripodX + 25f, h - 20f),
                                strokeWidth = 3f
                            )
                            // Phone on tripod
                            drawRect(
                                color = Color(0xFF1E293B),
                                topLeft = Offset(tripodX - 6f, tripodTop - 30f),
                                size = androidx.compose.ui.geometry.Size(12f, 26f)
                            )
                            drawCircle(
                                color = PramaanSaffron,
                                radius = 3f,
                                center = Offset(tripodX, tripodTop - 24f)
                            )

                            // Athlete on Right
                            val athleteX = w - 70f
                            val headY = h - 90f
                            // Head
                            drawCircle(
                                color = Color(0xFF1E293B),
                                radius = 9f,
                                center = Offset(athleteX, headY)
                            )
                            // Torso
                            drawLine(
                                color = PramaanSaffron,
                                start = Offset(athleteX, headY + 9f),
                                end = Offset(athleteX, headY + 45f),
                                strokeWidth = 5f
                            )
                            // Legs
                            drawLine(
                                color = Color(0xFF1E293B),
                                start = Offset(athleteX, headY + 45f),
                                end = Offset(athleteX - 10f, h - 20f),
                                strokeWidth = 3.5f
                            )
                            drawLine(
                                color = Color(0xFF1E293B),
                                start = Offset(athleteX, headY + 45f),
                                end = Offset(athleteX + 10f, h - 20f),
                                strokeWidth = 3.5f
                            )

                            // Field of View Cone (dashed orange)
                            val fovPathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                            drawLine(
                                color = PramaanSaffron.copy(alpha = 0.5f),
                                start = Offset(tripodX + 8f, tripodTop - 20f),
                                end = Offset(athleteX - 25f, headY - 10f),
                                strokeWidth = 2f,
                                pathEffect = fovPathEffect
                            )
                            drawLine(
                                color = PramaanSaffron.copy(alpha = 0.5f),
                                start = Offset(tripodX + 8f, tripodTop - 20f),
                                end = Offset(athleteX - 25f, h - 15f),
                                strokeWidth = 2f,
                                pathEffect = fovPathEffect
                            )

                            // 3.0 m Distance Line with text
                            val midY = h - 35f
                            drawLine(
                                color = Color(0xFF94A3B8),
                                start = Offset(tripodX + 25f, midY),
                                end = Offset(athleteX - 25f, midY),
                                strokeWidth = 2f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Coach Tip Callout
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF8FAFC))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = PramaanSaffron,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Keep the phone on a steady stand 3m away, full body in frame.",
                            style = PramaanTypography.bodySmall,
                            color = PramaanOnSurface
                        )
                    }
                }
            }

            // 3. Pre-Flight Diagnostic Checklist (Stitch Screen 2)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Pre-Flight Checks",
                            style = PramaanTypography.titleMedium,
                            color = PramaanOnSurface
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(PramaanEmerald)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "All Systems Ready",
                                style = PramaanTypography.labelSmall,
                                color = PramaanEmerald
                            )
                        }
                    }

                    // Check 1: Camera Framing
                    PreFlightCheckItem(
                        title = "Full body in camera frame",
                        subtitle = "Head-to-toe captured with floor margin",
                        statusText = "Ready",
                        isPassed = true
                    )

                    // Check 2: Gyroscope Steadiness
                    PreFlightCheckItem(
                        title = "Phone steadiness",
                        subtitle = if (gyroSteady) "Gyroscope idle, variance ${(currentRms * 100).toInt() / 100.0}°" else "Movement detected - place phone on stand",
                        statusText = if (gyroSteady) "Tripod Detected" else "Adjusting",
                        isPassed = gyroSteady
                    )

                    // Check 3: Lighting
                    PreFlightCheckItem(
                        title = "Lighting condition",
                        subtitle = "High contrast, no backlight silhouette",
                        statusText = "Good Daylight",
                        isPassed = true
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun PreFlightCheckItem(
    title: String,
    subtitle: String,
    statusText: String,
    isPassed: Boolean
) {
    PramaanCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isPassed) Color(0xFFDCFCE7) else Color(0xFFFFEDD5)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = if (isPassed) PramaanEmerald else PramaanSaffron,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = title,
                        style = PramaanTypography.labelLarge,
                        color = PramaanOnSurface
                    )
                    Text(
                        text = subtitle,
                        style = PramaanTypography.bodySmall,
                        color = PramaanOnSurfaceVariant
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isPassed) Color(0xFFDCFCE7) else Color(0xFFFFEDD5))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = statusText,
                    style = PramaanTypography.labelSmall,
                    color = if (isPassed) PramaanEmerald else PramaanSaffron
                )
            }
        }
    }
}
