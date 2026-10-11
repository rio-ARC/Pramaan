package app.pramaan.ui.coach

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import app.pramaan.AppContainer
import app.pramaan.api.dto.CoachSessionDetailDto
import app.pramaan.core.model.CheckResult
import app.pramaan.ui.components.InitialsAvatar
import app.pramaan.ui.components.IntegrityChip
import app.pramaan.ui.components.PramaanButton
import app.pramaan.ui.components.PramaanCard
import app.pramaan.ui.components.PramaanOutlinedButton
import app.pramaan.ui.components.StatusChip
import app.pramaan.ui.theme.PramaanEmerald
import app.pramaan.ui.theme.PramaanError
import app.pramaan.ui.theme.PramaanNavy
import app.pramaan.ui.theme.PramaanOnSurface
import app.pramaan.ui.theme.PramaanOnSurfaceVariant
import app.pramaan.ui.theme.PramaanOutline
import app.pramaan.ui.theme.PramaanSaffron
import app.pramaan.ui.theme.PramaanSurface
import app.pramaan.ui.theme.PramaanTypography
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    container: AppContainer,
    sessionId: String,
    isScanned: Boolean,
    scannedQr: String? = null,
    onDecisionDone: (sessionId: String, isApproved: Boolean, certificateJws: String?) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var detail by remember { mutableStateOf<CoachSessionDetailDto?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    // Dialog states
    var showApproveConfirm by remember { mutableStateOf(false) }
    var showRejectDialog by remember { mutableStateOf(false) }
    var rejectReason by remember { mutableStateOf("Retest required: movement execution flawed") }
    var customReason by remember { mutableStateOf("") }

    val loadDetail: () -> Unit = {
        scope.launch {
            isLoading = true
            errorMessage = null
            val result = container.reviewRepository.getSessionDetail(sessionId)
            if (result.isSuccess) {
                detail = result.getOrNull()
            } else {
                errorMessage = result.exceptionOrNull()?.message ?: "Failed to load session details"
            }
            isLoading = false
        }
    }

    LaunchedEffect(sessionId) {
        loadDetail()
    }

    // ExoPlayer lifecycle
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build()
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    LaunchedEffect(detail?.videoUrl) {
        val url = detail?.videoUrl
        if (!url.isNullOrBlank()) {
            val mediaItem = MediaItem.fromUri(url)
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Review Submission",
                            style = PramaanTypography.titleMedium,
                            color = PramaanOnSurface
                        )
                        Text(
                            text = sessionId.take(16) + "...",
                            style = PramaanTypography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = PramaanOnSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
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
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = PramaanSaffron)
            }
            return@Scaffold
        }

        val sessionDetail = detail
        if (sessionDetail == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                PramaanCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFFEF3C7),
                            modifier = Modifier.size(56.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = Color(0xFFD97706),
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        Text(
                            text = "Submission Pending Sync",
                            style = PramaanTypography.titleMedium,
                            color = PramaanOnSurface,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = "The athlete has generated the verification QR code, but the session data is still uploading to the server.",
                            style = PramaanTypography.bodyMedium,
                            color = PramaanOnSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        PramaanButton(
                            text = "Refresh / Check Again",
                            onClick = { loadDetail() },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            return@Scaffold
        }

        val isVerified = sessionDetail.integrity.equals("VERIFIED", ignoreCase = true)
        val canApprove = isVerified && sessionDetail.status.equals("AWAITING_REVIEW", ignoreCase = true)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // 1. Verification Mode Banner
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = if (isScanned) Color(0xFFDCFCE7) else Color(0xFFEFF6FF),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isScanned) PramaanEmerald else Color(0xFF93C5FD)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isScanned) Icons.Default.QrCode else Icons.Default.Security,
                            contentDescription = null,
                            tint = if (isScanned) PramaanEmerald else Color(0xFF2563EB),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (isScanned) "Scanned In-Person ✓" else "Remote Review Mode",
                                style = PramaanTypography.titleSmall,
                                color = if (isScanned) Color(0xFF14532D) else Color(0xFF1E40AF),
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isScanned)
                                    "Physical presence confirmed via athlete's hardware S2 QR code."
                                else
                                    "Reviewing submission remotely from the central organiser queue.",
                                style = PramaanTypography.bodySmall,
                                color = if (isScanned) Color(0xFF166534) else Color(0xFF1E3A8A)
                            )
                        }
                    }
                }
            }

            // 2. Athlete Card
            item {
                PramaanCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        InitialsAvatar(name = sessionDetail.athleteName, size = 52)
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = sessionDetail.athleteName,
                                style = PramaanTypography.titleMedium,
                                color = PramaanOnSurface
                            )
                            Text(
                                text = "Athlete ID: ${sessionDetail.athleteId}",
                                style = PramaanTypography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = PramaanNavy
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${sessionDetail.sex ?: "—"} • ${sessionDetail.age ?: "—"} Yrs • ${sessionDetail.heightCm?.toInt() ?: "—"} cm • ${sessionDetail.weightKg?.toInt() ?: "—"} kg",
                                style = PramaanTypography.bodySmall,
                                color = PramaanOnSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 3. Video Player
            item {
                PramaanCard {
                    Text(
                        text = "Session Video Evidence",
                        style = PramaanTypography.titleSmall,
                        color = PramaanOnSurface
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(4f / 5f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    player = exoPlayer
                                    useController = true
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            // 4. Score & AI Audit Checks
            item {
                PramaanCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = sessionDetail.test.replace("_", " "),
                                style = PramaanTypography.labelMedium,
                                color = PramaanOnSurfaceVariant
                            )
                            Text(
                                text = "${sessionDetail.best} ${sessionDetail.unit}",
                                style = PramaanTypography.headlineMedium,
                                color = PramaanNavy,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        IntegrityChip(integrity = sessionDetail.integrity)
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "4-Layer Automated Integrity Audit",
                        style = PramaanTypography.labelSmall,
                        color = PramaanOnSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (sessionDetail.checks.isEmpty()) {
                        // Fallback check rows
                        AuditCheckItem("C1: Cryptographic Seal", "HMAC + SHA-256 frame seal", isVerified)
                        AuditCheckItem("C2: Gravity Ballistics", "g = 9.81 m/s² parabolic fit", isVerified)
                        AuditCheckItem("C3: Multi-Sensor Agreement", "Pose vs Sensor sync ≤ 10cm", isVerified)
                        AuditCheckItem("C4: Sensor Steadiness", "Gyroscope drift < 0.15 rad/s", isVerified)
                    } else {
                        sessionDetail.checks.forEach { check ->
                            AuditCheckItem(
                                title = "${check.id}: ${checkName(check.id)}",
                                subtitle = "${check.value} (Limit: ${check.limit})",
                                passed = check.pass
                            )
                        }
                    }
                }
            }

            // 5. Rule Warning Banner if not approvable
            if (!isVerified) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFFEF2F2),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFCA5A5))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = PramaanError,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Rule Violation: Integrity is ${sessionDetail.integrity}. Per SAI protocol, sessions with failed AI checks cannot be approved.",
                                style = PramaanTypography.bodySmall,
                                color = Color(0xFF991B1B)
                            )
                        }
                    }
                }
            }

            // 6. Action Buttons
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PramaanButton(
                        text = if (isSubmitting) "Signing with Keystore..." else "Approve & Sign Record",
                        icon = Icons.Default.Lock,
                        onClick = { showApproveConfirm = true },
                        enabled = canApprove && !isSubmitting
                    )

                    PramaanOutlinedButton(
                        text = "Reject Submission",
                        icon = Icons.Default.Close,
                        onClick = { showRejectDialog = true },
                        enabled = !isSubmitting
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    // Approve Confirmation Dialog
    if (showApproveConfirm) {
        AlertDialog(
            onDismissRequest = { showApproveConfirm = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = PramaanEmerald,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Confirm Official Approval",
                    style = PramaanTypography.titleMedium,
                    color = PramaanOnSurface
                )
            },
            text = {
                Text(
                    text = "You are about to sign this athletic performance record with your hardware-backed Android Keystore key. An immutable certificate will be issued.\n\nAthlete: ${detail?.athleteName}\nMetric: ${detail?.best} ${detail?.unit}\nMethod: ${if (isScanned) "IN_PERSON_QR" else "REMOTE"}",
                    style = PramaanTypography.bodyMedium,
                    color = PramaanOnSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showApproveConfirm = false
                        isSubmitting = true
                        scope.launch {
                            val method = if (isScanned) "IN_PERSON_QR" else "REMOTE"
                            val res = container.reviewRepository.submitReview(
                                sessionId = sessionId,
                                manifestSha256 = detail?.manifestSha256 ?: "",
                                decision = "APPROVED",
                                method = method,
                                reason = null,
                                scannedQr = scannedQr
                            )
                            isSubmitting = false
                            if (res.isSuccess) {
                                val outcome = res.getOrNull()
                                onDecisionDone(sessionId, true, outcome?.certificateJws)
                            } else {
                                errorMessage = res.exceptionOrNull()?.message ?: "Review failed"
                            }
                        }
                    }
                ) {
                    Text("Approve & Sign", color = PramaanEmerald, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showApproveConfirm = false }) {
                    Text("Cancel", color = PramaanOutline)
                }
            }
        )
    }

    // Reject Dialog with Reason Selection
    if (showRejectDialog) {
        val reasons = listOf(
            "Retest required: movement execution flawed",
            "Disqualified: illegal starting position or assistance",
            "Occluded: camera angle did not capture apex/flexion",
            "Other reason"
        )

        AlertDialog(
            onDismissRequest = { showRejectDialog = false },
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
                    text = "Reject Submission",
                    style = PramaanTypography.titleMedium,
                    color = PramaanOnSurface
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Select official rejection reason to notify the athlete:",
                        style = PramaanTypography.bodySmall,
                        color = PramaanOnSurfaceVariant
                    )
                    reasons.forEach { r ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { rejectReason = r }
                        ) {
                            RadioButton(
                                selected = (rejectReason == r),
                                onClick = { rejectReason = r },
                                colors = RadioButtonDefaults.colors(selectedColor = PramaanSaffron)
                            )
                            Text(
                                text = r,
                                style = PramaanTypography.bodySmall,
                                color = PramaanOnSurface
                            )
                        }
                    }

                    if (rejectReason == "Other reason") {
                        OutlinedTextField(
                            value = customReason,
                            onValueChange = { customReason = it },
                            placeholder = { Text("Enter detailed reason...") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val finalReason = if (rejectReason == "Other reason") customReason.ifBlank { "Rejected by coach" } else rejectReason
                        showRejectDialog = false
                        isSubmitting = true
                        scope.launch {
                            val method = if (isScanned) "IN_PERSON_QR" else "REMOTE"
                            val res = container.reviewRepository.submitReview(
                                sessionId = sessionId,
                                manifestSha256 = detail?.manifestSha256 ?: "",
                                decision = "REJECTED",
                                method = method,
                                reason = finalReason,
                                scannedQr = scannedQr
                            )
                            isSubmitting = false
                            if (res.isSuccess) {
                                onDecisionDone(sessionId, false, null)
                            } else {
                                errorMessage = res.exceptionOrNull()?.message ?: "Rejection failed"
                            }
                        }
                    }
                ) {
                    Text("Confirm Rejection", color = PramaanError, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRejectDialog = false }) {
                    Text("Cancel", color = PramaanOutline)
                }
            }
        )
    }
}

@Composable
private fun AuditCheckItem(title: String, subtitle: String, passed: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFF8FAFC))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (passed) Icons.Default.CheckCircle else Icons.Default.GppBad,
            contentDescription = null,
            tint = if (passed) PramaanEmerald else PramaanError,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(text = title, style = PramaanTypography.labelMedium, color = PramaanOnSurface)
            Text(text = subtitle, style = PramaanTypography.bodySmall, color = PramaanOutline)
        }
    }
}

private fun checkName(id: String): String = when (id) {
    "C1" -> "Seal Integrity"
    "C2" -> "Gravity Acceleration"
    "C3" -> "Sensor Agreement"
    "C4" -> "Camera Steadiness"
    else -> "Integrity Check"
}
