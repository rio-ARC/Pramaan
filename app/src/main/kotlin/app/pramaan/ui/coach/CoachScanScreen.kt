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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import app.pramaan.core.manifest.SessionQr
import app.pramaan.ui.components.QrScanner
import app.pramaan.ui.theme.PramaanSaffron
import app.pramaan.ui.theme.PramaanTypography

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoachScanScreen(
    onSessionScanned: (sessionId: String, rawQr: String) -> Unit,
    onBack: () -> Unit
) {
    var hasScanned by remember { mutableStateOf(false) }
    var scanFeedback by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Scan Athlete QR",
                        style = PramaanTypography.titleMedium,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = Color.Black
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Live Camera QR Scanner
            QrScanner(
                onQrScanned = { rawQr ->
                    if (hasScanned) return@QrScanner
                    val decoded = SessionQr.decode(rawQr)
                    if (decoded != null) {
                        hasScanned = true
                        onSessionScanned(decoded.sessionId, rawQr)
                    } else if (rawQr.startsWith("PRAMAAN:S2")) {
                        scanFeedback = "Invalid or expired S2 QR code format"
                    } else {
                        scanFeedback = "Not a Pramaan S2 QR code"
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Scanning Target Reticle & Instructions
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        text = "Point camera at the Athlete's S2 Verification QR screen",
                        style = PramaanTypography.bodyMedium,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        textAlign = TextAlign.Center
                    )
                }

                // Center Reticle
                Box(
                    modifier = Modifier
                        .size(260.dp)
                        .border(3.dp, PramaanSaffron, RoundedCornerShape(24.dp))
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (scanFeedback != null) {
                        Surface(
                            color = Color(0xFFDC2626).copy(alpha = 0.85f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = scanFeedback ?: "",
                                color = Color.White,
                                style = PramaanTypography.labelMedium,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Text(
                        text = "Hardware Attestation • S2 In-Person Protocol",
                        style = PramaanTypography.labelSmall.copy(letterSpacing = 1.sp),
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}
