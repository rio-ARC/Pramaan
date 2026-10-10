package app.pramaan.ui.athlete

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pramaan.AppContainer
import app.pramaan.data.SessionEntity
import app.pramaan.ui.components.IntegrityChip
import app.pramaan.ui.components.PramaanButton
import app.pramaan.ui.components.PramaanCard
import app.pramaan.ui.components.QrImage
import app.pramaan.ui.components.StatusChip
import app.pramaan.ui.theme.PramaanEmerald
import app.pramaan.ui.theme.PramaanNavy
import app.pramaan.ui.theme.PramaanOnSurface
import app.pramaan.ui.theme.PramaanOnSurfaceVariant
import app.pramaan.ui.theme.PramaanOutline
import app.pramaan.ui.theme.PramaanSaffron
import app.pramaan.ui.theme.PramaanSurface
import app.pramaan.ui.theme.PramaanTypography
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CertificateScreen(
    sessionId: String,
    container: AppContainer,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var session by remember { mutableStateOf<SessionEntity?>(null) }
    var serverUrl by remember { mutableStateOf("http://10.0.2.2:8080") }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(sessionId) {
        val sess = container.sessionDao.getById(sessionId)
        val sUrl = container.authStore.serverUrlFlow.first()
        session = sess
        if (!sUrl.isNullOrBlank()) {
            serverUrl = sUrl.removeSuffix("/")
        }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Official Certificate",
                        style = PramaanTypography.titleMedium,
                        color = PramaanOnSurface
                    )
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
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = PramaanNavy)
            }
            return@Scaffold
        }

        val currentSession = session
        if (currentSession == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Session not found",
                    style = PramaanTypography.bodyMedium,
                    color = PramaanOnSurfaceVariant
                )
            }
            return@Scaffold
        }

        val isApproved = currentSession.status.equals("APPROVED", ignoreCase = true)
        val certificateUrl = if (!currentSession.certificateJws.isNullOrBlank()) {
            "$serverUrl/v/${currentSession.certificateJws}"
        } else {
            "$serverUrl/v/draft-$sessionId"
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!isApproved) {
                // Pending notice
                PramaanCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFEF3C7)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.HourglassTop,
                                contentDescription = null,
                                tint = PramaanSaffron,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Awaiting Coach Sign-Off",
                                style = PramaanTypography.titleSmall,
                                color = PramaanOnSurface
                            )
                            Text(
                                text = "This session has not yet received a verified coach signature. Official certificate seal will activate upon approval.",
                                style = PramaanTypography.bodySmall,
                                color = PramaanOnSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Certificate Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 2.dp,
                        brush = Brush.linearGradient(
                            listOf(
                                if (isApproved) Color(0xFFF59E0B) else Color(0xFFCBD5E1),
                                if (isApproved) PramaanEmerald else Color(0xFF94A3B8)
                            )
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ),
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                shadowElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header Banner
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "SPORTS AUTHORITY OF INDIA",
                                style = PramaanTypography.labelSmall.copy(letterSpacing = 1.2.sp),
                                color = PramaanNavy,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "PRAMAAN ATTESTATION",
                                style = PramaanTypography.titleSmall,
                                color = PramaanSaffron,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.WorkspacePremium,
                            contentDescription = null,
                            tint = if (isApproved) Color(0xFFF59E0B) else Color(0xFF94A3B8),
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Divider(color = Color(0xFFE2E8F0))
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "VERIFIED PERFORMANCE RECORD",
                        style = PramaanTypography.labelMedium,
                        color = PramaanOnSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${currentSession.best} ${currentSession.unit}",
                        style = PramaanTypography.headlineLarge,
                        color = PramaanNavy,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = currentSession.test.replace("_", " "),
                        style = PramaanTypography.titleMedium,
                        color = PramaanOnSurface
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // QR Verification Area
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                            .padding(8.dp)
                    ) {
                        QrImage(
                            content = certificateUrl,
                            size = 180.dp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Scan to verify at $serverUrl",
                        style = PramaanTypography.bodySmall,
                        color = PramaanOutline,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Divider(color = Color(0xFFE2E8F0))
                    Spacer(modifier = Modifier.height(16.dp))

                    // Verification Metadata
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Session ID",
                                style = PramaanTypography.bodySmall,
                                color = PramaanOnSurfaceVariant
                            )
                            Text(
                                text = currentSession.id.take(12) + "...",
                                style = PramaanTypography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = PramaanOnSurface
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Integrity",
                                style = PramaanTypography.bodySmall,
                                color = PramaanOnSurfaceVariant
                            )
                            IntegrityChip(integrity = currentSession.serverIntegrity ?: currentSession.localIntegrity)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Review Status",
                                style = PramaanTypography.bodySmall,
                                color = PramaanOnSurfaceVariant
                            )
                            StatusChip(status = currentSession.status)
                        }

                        if (!currentSession.reviewedByName.isNullOrBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Coach Signer",
                                    style = PramaanTypography.bodySmall,
                                    color = PramaanOnSurfaceVariant
                                )
                                Text(
                                    text = currentSession.reviewedByName,
                                    style = PramaanTypography.labelSmall,
                                    color = PramaanNavy,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Share Action
            PramaanButton(
                text = "Share Certificate Link",
                icon = Icons.Default.Share,
                onClick = {
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_SUBJECT, "Pramaan Verified Performance Certificate")
                        putExtra(
                            Intent.EXTRA_TEXT,
                            "Official Pramaan Verified Athletic Performance Record: ${currentSession.best} ${currentSession.unit} in ${currentSession.test}.\n\nVerify certificate online: $certificateUrl"
                        )
                        type = "text/plain"
                    }
                    context.startActivity(Intent.createChooser(sendIntent, "Share Certificate"))
                }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
