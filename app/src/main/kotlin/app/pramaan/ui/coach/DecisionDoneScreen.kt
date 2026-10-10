package app.pramaan.ui.coach

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pramaan.AppContainer
import app.pramaan.ui.components.PramaanButton
import app.pramaan.ui.components.PramaanCard
import app.pramaan.ui.components.QrImage
import app.pramaan.ui.theme.PramaanEmerald
import app.pramaan.ui.theme.PramaanError
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
fun DecisionDoneScreen(
    container: AppContainer,
    sessionId: String,
    isApproved: Boolean,
    certificateJws: String?,
    onBackToQueue: () -> Unit
) {
    var serverUrl by remember { mutableStateOf("http://10.0.2.2:8080") }

    LaunchedEffect(Unit) {
        val sUrl = container.authStore.serverUrlFlow.first()
        if (!sUrl.isNullOrBlank()) {
            serverUrl = sUrl.removeSuffix("/")
        }
    }

    val verifyUrl = if (!certificateJws.isNullOrBlank()) {
        "$serverUrl/v/$certificateJws"
    } else {
        "$serverUrl/api/v1/sessions/$sessionId"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Decision Confirmed",
                        style = PramaanTypography.titleMedium,
                        color = PramaanOnSurface
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = PramaanSurface
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(20.dp))

                // Outcome Icon
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(if (isApproved) Color(0xFFDCFCE7) else Color(0xFFFEE2E2)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isApproved) Icons.Default.Check else Icons.Default.Close,
                        contentDescription = null,
                        tint = if (isApproved) PramaanEmerald else PramaanError,
                        modifier = Modifier.size(44.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (isApproved) "Official Record Approved" else "Submission Rejected",
                    style = PramaanTypography.headlineMedium,
                    color = PramaanOnSurface,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = if (isApproved)
                        "Hardware-backed coach attestation applied. Certificate issued and public ledger updated."
                    else
                        "Rejection reason recorded. The athlete's app has been updated.",
                    style = PramaanTypography.bodyMedium,
                    color = PramaanOnSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                if (isApproved && !certificateJws.isNullOrBlank()) {
                    PramaanCard {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.WorkspacePremium,
                                    contentDescription = null,
                                    tint = PramaanSaffron,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "OFFICIAL CERTIFICATE QR",
                                    style = PramaanTypography.labelMedium,
                                    color = PramaanNavy,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(1.dp, PramaanOutline, RoundedCornerShape(12.dp))
                                    .padding(8.dp)
                            ) {
                                QrImage(content = verifyUrl, size = 180.dp)
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "Scan to inspect verified SAI cryptographic certificate",
                                style = PramaanTypography.bodySmall,
                                color = PramaanOutline,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            PramaanButton(
                text = "Back to Queue",
                icon = Icons.Default.ArrowBack,
                onClick = onBackToQueue
            )
        }
    }
}
