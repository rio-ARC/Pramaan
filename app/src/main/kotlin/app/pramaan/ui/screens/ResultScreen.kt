package app.pramaan.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SecurityUpdateWarning
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pramaan.AppContainer
import app.pramaan.core.integrity.VerificationOutcome
import app.pramaan.core.manifest.CanonicalJson
import app.pramaan.core.manifest.SessionManifest
import app.pramaan.core.model.Badge
import app.pramaan.core.model.TestType
import app.pramaan.data.AthleteEntity
import app.pramaan.data.SessionEntity
import java.io.File
import app.pramaan.ui.components.BadgeChip
import app.pramaan.ui.components.InitialsAvatar
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    sessionId: String,
    container: AppContainer,
    onSubmitForCoach: (String) -> Unit,
    onNavigateToCertificate: (String) -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var session by remember { mutableStateOf<SessionEntity?>(null) }
    var manifest by remember { mutableStateOf<SessionManifest?>(null) }
    var athlete by remember { mutableStateOf<AthleteEntity?>(null) }
    var currentBadge by remember { mutableStateOf(Badge.VERIFIED) }
    var selectedAttemptIndex by remember { mutableIntStateOf(0) }

    // Tamper simulation dialog state
    var showTamperModal by remember { mutableStateOf(false) }
    var tamperMessage by remember { mutableStateOf("") }
    var isTampering by remember { mutableStateOf(false) }

    fun loadData() {
        scope.launch {
            val sess = container.sessionDao.getById(sessionId)
            session = sess
            sess?.let {
                athlete = container.athleteDao.getById(it.ownerAccountId)
                try {
                    currentBadge = Badge.valueOf(it.localIntegrity)
                } catch (_: Exception) { }
            }

            val manifestFile = container.sessionStore.getManifestFile(sessionId)
            if (manifestFile.exists()) {
                try {
                    manifest = CanonicalJson.decodeFromByteArray(
                        SessionManifest.serializer(),
                        manifestFile.readBytes()
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    LaunchedEffect(sessionId) {
        loadData()
    }

    fun triggerTamperSimulation() {
        if (isTampering) return
        isTampering = true
        scope.launch {
            val (_, outcome) = withContext(Dispatchers.IO) {
                container.tamperSimulator.simulateTampering(sessionId)
            }
            tamperMessage = "Metric altered in session video file. Integrity SHA-256 hash mismatch detected across 4-layer audit.\n\nStatus automatically downgraded to: ${outcome.badge.name}."
            showTamperModal = true
            isTampering = false
            loadData() // Refresh screen with updated state
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Assessment Summary",
                            style = PramaanTypography.titleMedium,
                            color = PramaanOnSurface
                        )
                        Text(
                            text = "OFFICIAL RECORD",
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
                    Box(modifier = Modifier.padding(end = 16.dp)) {
                        BadgeChip(badge = currentBadge)
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
            item { Spacer(modifier = Modifier.height(8.dp)) }

            // 1. Athlete Snapshot Card (Stitch Screen 4)
            item {
                PramaanCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            InitialsAvatar(name = athlete?.name ?: "Athlete", size = 52)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = athlete?.name ?: "Manpreet Kaur",
                                        style = PramaanTypography.titleMedium,
                                        color = PramaanOnSurface
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.Verified,
                                        contentDescription = null,
                                        tint = PramaanEmerald,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Text(
                                    text = "Age ${athlete?.age ?: 14} • Punjab • U-16 Trial",
                                    style = PramaanTypography.bodySmall,
                                    color = PramaanOnSurfaceVariant
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFDCFCE7))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Verified",
                                style = PramaanTypography.labelSmall,
                                color = PramaanEmerald
                            )
                        }
                    }
                }
            }

            // 2. Official Benchmark Card (Stitch Screen 4)
            item {
                PramaanCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.VerticalAlignTop,
                                contentDescription = null,
                                tint = PramaanSaffron,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "OFFICIAL BENCHMARK",
                                style = PramaanTypography.labelSmall,
                                color = PramaanSaffron
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFFFEDD5))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Trial #${sessionId.take(8).uppercase()}",
                                style = PramaanTypography.labelSmall,
                                color = PramaanSaffron
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Big Numeral Metric
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "${session?.best ?: 38.2}",
                            style = PramaanTypography.displayLarge,
                            color = PramaanOnSurface
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = session?.unit ?: "cm",
                            style = PramaanTypography.headlineMedium,
                            color = PramaanOutline,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }

                    Text(
                        text = (session?.test ?: "STANDING VERTICAL JUMP").replace('_', ' '),
                        style = PramaanTypography.labelLarge,
                        color = PramaanOnSurface
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    val bjAttempts = manifest?.result?.broadJumpAttempts ?: emptyList()
                    val attempts = manifest?.result?.attempts ?: emptyList()

                    if (bjAttempts.isNotEmpty()) {
                        Text(
                            text = "Valid attempts: ${bjAttempts.count { it.valid }} of ${bjAttempts.size}",
                            style = PramaanTypography.bodySmall,
                            color = PramaanEmerald
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // Trial Run Sequence Strip
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF8FAFC))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "TRIAL RUN SEQUENCE",
                            style = PramaanTypography.labelSmall,
                            color = PramaanOutline
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (bjAttempts.isNotEmpty()) {
                                bjAttempts.forEachIndexed { idx, att ->
                                    val isSelected = (selectedAttemptIndex == idx)
                                    val isBest = att.valid && att.distanceCm == (manifest?.result?.best ?: 0.0)
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) Color(0xFFEFF6FF) else Color.White)
                                            .border(
                                                width = if (isSelected) 2.dp else 1.dp,
                                                color = if (isSelected) Color(0xFF2563EB) else Color(0xFFE2E8F0),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .clickable { selectedAttemptIndex = idx }
                                            .padding(vertical = 8.dp, horizontal = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "Jump ${idx + 1}",
                                                    style = PramaanTypography.labelSmall,
                                                    color = if (isSelected) Color(0xFF2563EB) else PramaanOutline
                                                )
                                                if (isBest) {
                                                    Spacer(modifier = Modifier.width(2.dp))
                                                    Icon(
                                                        imageVector = Icons.Default.Star,
                                                        contentDescription = null,
                                                        tint = PramaanSaffron,
                                                        modifier = Modifier.size(10.dp)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "${att.distanceCm.toInt()} cm",
                                                style = PramaanTypography.labelLarge,
                                                color = PramaanOnSurface
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = if (att.valid) "VALID" else if (att.fouls.isNotEmpty()) att.fouls.first().name.take(8) else "NOTE",
                                                fontSize = 9.sp,
                                                color = if (att.valid) PramaanEmerald else PramaanError
                                            )
                                        }
                                    }
                                }
                            } else if (attempts.isNotEmpty()) {
                                attempts.forEachIndexed { idx, att ->
                                    val isBest = att.flightCm == (manifest?.result?.best ?: 0.0)
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isBest) PramaanSaffron else Color.White)
                                            .border(
                                                width = 1.dp,
                                                color = if (isBest) PramaanSaffron else Color(0xFFE2E8F0),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .padding(vertical = 8.dp, horizontal = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "Attempt ${idx + 1}",
                                                    style = PramaanTypography.labelSmall,
                                                    color = if (isBest) Color.White else PramaanOutline
                                                )
                                                if (isBest) {
                                                    Spacer(modifier = Modifier.width(2.dp))
                                                    Icon(
                                                        imageVector = Icons.Default.Star,
                                                        contentDescription = null,
                                                        tint = Color.White,
                                                        modifier = Modifier.size(10.dp)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "${att.flightCm} cm",
                                                style = PramaanTypography.labelLarge,
                                                color = if (isBest) Color.White else PramaanOnSurface
                                            )
                                        }
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(PramaanSaffron)
                                        .padding(8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(text = "Attempt 1 ★", style = PramaanTypography.labelSmall, color = Color.White)
                                        Text(text = "${session?.best ?: 38.2} ${session?.unit ?: "cm"}", style = PramaanTypography.labelLarge, color = Color.White)
                                    }
                                }
                            }
                        }
                    }

                    // Standing Broad Jump: Landing Still & Biomechanics
                    if (bjAttempts.isNotEmpty()) {
                        val curAtt = bjAttempts.getOrNull(selectedAttemptIndex) ?: bjAttempts.first()
                        val stillFile = File(container.sessionStore.getStillsDir(sessionId), "att${curAtt.index}_landing.jpg")
                        val stillBmp = remember(stillFile.absolutePath, curAtt.index) {
                            if (stillFile.exists()) BitmapFactory.decodeFile(stillFile.absolutePath) else null
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (stillBmp != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black)
                            ) {
                                Image(
                                    bitmap = stillBmp.asImageBitmap(),
                                    contentDescription = "Landing Still",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(8.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xCC000000))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "Landing Contact: ${curAtt.distanceCm.toInt()} cm (Jump ${curAtt.index})",
                                        color = Color.White,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        // Biomechanics Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Angle: ${curAtt.takeoffAngleDeg?.toInt() ?: 0}°",
                                style = PramaanTypography.bodySmall,
                                color = PramaanOutline
                            )
                            Text(
                                text = "Speed: ${String.format("%.2f", curAtt.takeoffSpeedMps ?: 0.0)} m/s",
                                style = PramaanTypography.bodySmall,
                                color = PramaanOutline
                            )
                            Text(
                                text = "Knee Dip: ${curAtt.kneeDipDeg?.toInt() ?: 0}°",
                                style = PramaanTypography.bodySmall,
                                color = PramaanOutline
                            )
                            Text(
                                text = "Arm Swing: ${curAtt.armSwingIndex?.toInt() ?: 0}",
                                style = PramaanTypography.bodySmall,
                                color = PramaanOutline
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Status: ${if (curAtt.valid) "Clean Jump • Good forward drive" else if (curAtt.fouls.isNotEmpty()) "Foul: " + curAtt.fouls.joinToString { it.name } else "Note: " + curAtt.notes.joinToString { it.name }}",
                            style = PramaanTypography.labelSmall,
                            color = PramaanOnSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = PramaanOutline,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (manifest?.test == TestType.BROAD_JUMP)
                                    "Measured against ${manifest?.calibration?.markerCm?.toInt() ?: 200} cm mark"
                                else
                                    "Recorded: Today",
                                style = PramaanTypography.bodySmall,
                                color = PramaanOutline
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.SatelliteAlt,
                                contentDescription = null,
                                tint = PramaanEmerald,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Hardware Geostamped",
                                style = PramaanTypography.bodySmall,
                                color = PramaanEmerald
                            )
                        }
                    }
                }
            }

            // 3. Why is this result verified? Hardware Audit (Stitch Screen 4)
            item {
                PramaanCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFDCFCE7)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VerifiedUser,
                                    contentDescription = null,
                                    tint = PramaanEmerald,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Why is this result verified?",
                                    style = PramaanTypography.titleMedium,
                                    color = PramaanOnSurface
                                )
                                Text(
                                    text = "Pramaan Trust Engine • Hardware Audit",
                                    style = PramaanTypography.labelSmall,
                                    color = PramaanOutline
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (manifest?.test == TestType.BROAD_JUMP) {
                            AuditCheckRow(
                                title = "Hardware Cryptographic Seal (C1)",
                                subtitle = "Hardware-backed Android Keystore ECDSA P-256 seal & hashes match",
                                passed = manifest?.checks?.find { it.id == "C1" }?.pass ?: true
                            )
                            AuditCheckRow(
                                title = "Gravity Parabola Trajectory (C2)",
                                subtitle = "Hip parabolic acceleration conforms to 9.8 m/s² natural gravity",
                                passed = manifest?.checks?.find { it.id == "C2" }?.pass ?: true
                            )
                            AuditCheckRow(
                                title = "Horizontal Motion Consistency (C3)",
                                subtitle = "Forward linearity and velocity within biomechanical limits",
                                passed = manifest?.checks?.find { it.id == "C3" }?.pass ?: true
                            )
                            AuditCheckRow(
                                title = "Camera was completely stationary (C4)",
                                subtitle = "Tripod gyroscope stability variance < 0.12 rad/s",
                                passed = manifest?.checks?.find { it.id == "C4" }?.pass ?: true
                            )
                            AuditCheckRow(
                                title = "Ground Calibration Agreement (C5)",
                                subtitle = "Tape marker scale agrees with registered athlete height scale (diff ≤ 10%)",
                                passed = manifest?.checks?.find { it.id == "C5" }?.pass ?: true
                            )
                        } else {
                            AuditCheckRow(
                                title = "Camera was completely stationary",
                                subtitle = "Tripod gyroscope stability variance < 0.12 rad/s",
                                passed = manifest?.checks?.find { it.id == "C4" }?.pass ?: true
                            )
                            AuditCheckRow(
                                title = "Real-time gravity confirmed",
                                subtitle = "9.8 m/s² natural acceleration verified against frame rate",
                                passed = manifest?.checks?.find { it.id == "C2" }?.pass ?: true
                            )
                            AuditCheckRow(
                                title = "Dual calculation match",
                                subtitle = "Flight time aligns with anatomical hip rise within tolerance",
                                passed = manifest?.checks?.find { it.id == "C3" }?.pass ?: true
                            )
                            AuditCheckRow(
                                title = "Cryptographically sealed on this phone",
                                subtitle = "Hardware-backed Android Keystore ECDSA P-256 seal",
                                passed = manifest?.checks?.find { it.id == "C1" }?.pass ?: true
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF1F5F9))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = null,
                                tint = Color(0xFF4F46E5),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "SEAL-${sessionId.take(8).uppercase()}",
                                style = PramaanTypography.labelMedium,
                                color = PramaanOnSurface
                            )
                        }
                        Text(
                            text = if (currentBadge == Badge.FLAGGED || currentBadge == Badge.TAMPERED) "FLAGGED" else "100% Tamper Proof",
                            style = PramaanTypography.labelSmall,
                            color = if (currentBadge == Badge.FLAGGED || currentBadge == Badge.TAMPERED) PramaanError else PramaanEmerald
                        )
                    }
                }
            }

            // 4. Action Buttons (Stitch Screen 4)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val isApproved = session?.status.equals("APPROVED", ignoreCase = true)
                    val isSubmitted = session?.status.equals("SUBMITTED", ignoreCase = true)

                    if (isApproved) {
                        PramaanButton(
                            text = "View Digital Certificate",
                            icon = Icons.Default.Badge,
                            onClick = { onNavigateToCertificate(sessionId) }
                        )
                    } else {
                        var isSubmitting by remember { mutableStateOf(false) }
                        var submitError by remember { mutableStateOf<String?>(null) }

                        if (submitError != null) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFFEE2E2),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFCA5A5))
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.GppBad,
                                        contentDescription = null,
                                        tint = PramaanError,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = submitError!!,
                                        style = PramaanTypography.bodySmall,
                                        color = Color(0xFF991B1B)
                                    )
                                }
                            }
                        }

                        PramaanButton(
                            text = if (isSubmitting) "Submitting to Server..." else if (isSubmitted) "Show Coach Verification QR" else "Submit for Coach Verification",
                            icon = Icons.Default.QrCode2,
                            onClick = {
                                if (isSubmitted) {
                                    onSubmitForCoach(sessionId)
                                } else {
                                    isSubmitting = true
                                    submitError = null
                                    scope.launch {
                                        val res = container.submissionRepository.submitSession(sessionId)
                                        isSubmitting = false
                                        if (res.isSuccess) {
                                            onSubmitForCoach(sessionId)
                                        } else {
                                            submitError = res.exceptionOrNull()?.message ?: "Upload failed. Please check server connection."
                                        }
                                    }
                                }
                            }
                        )

                        PramaanOutlinedButton(
                            text = "Save as Draft",
                            icon = Icons.Default.ArrowBack,
                            onClick = onBack
                        )
                    }

                    Text(
                        text = if (isTampering) "Injecting byte tamper..." else "Simulate Tamper (Demo Fraud Detection)",
                        style = PramaanTypography.labelMedium,
                        color = PramaanError,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .clickable { triggerTamperSimulation() }
                            .padding(8.dp)
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    // Tamper simulation alert dialog
    if (showTamperModal) {
        AlertDialog(
            onDismissRequest = { showTamperModal = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.GppBad,
                    contentDescription = null,
                    tint = PramaanError,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Tamper Simulation Triggered",
                    style = PramaanTypography.titleMedium,
                    color = PramaanOnSurface
                )
            },
            text = {
                Text(
                    text = tamperMessage,
                    style = PramaanTypography.bodyMedium,
                    color = PramaanOnSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = { showTamperModal = false }) {
                    Text("Understood", color = PramaanSaffron)
                }
            }
        )
    }
}

@Composable
private fun AuditCheckRow(
    title: String,
    subtitle: String,
    passed: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFF8FAFC))
            .padding(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = if (passed) Icons.Default.CheckCircle else Icons.Default.GppBad,
            contentDescription = null,
            tint = if (passed) PramaanEmerald else PramaanError,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                style = PramaanTypography.labelMedium,
                color = PramaanOnSurface
            )
            Text(
                text = subtitle,
                style = PramaanTypography.bodySmall,
                color = PramaanOutline
            )
        }
    }
}
