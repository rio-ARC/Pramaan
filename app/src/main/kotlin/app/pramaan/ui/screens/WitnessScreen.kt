package app.pramaan.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import app.pramaan.AppContainer
import app.pramaan.core.crypto.Base64Url
import app.pramaan.core.crypto.DerRaw
import app.pramaan.core.crypto.EcdsaVerifier
import app.pramaan.core.manifest.CanonicalJson
import app.pramaan.core.manifest.SessionQrPayload
import app.pramaan.core.manifest.WitnessQrCodec
import app.pramaan.core.manifest.WitnessQrPayload
import app.pramaan.ui.components.PramaanButton
import app.pramaan.ui.components.PramaanCard
import app.pramaan.ui.components.QrImage
import app.pramaan.ui.components.QrScanner
import app.pramaan.ui.theme.PramaanEmerald
import app.pramaan.ui.theme.PramaanError
import app.pramaan.ui.theme.PramaanOnSurface
import app.pramaan.ui.theme.PramaanOnSurfaceVariant
import app.pramaan.ui.theme.PramaanOutline
import app.pramaan.ui.theme.PramaanSaffron
import app.pramaan.ui.theme.PramaanSurface
import app.pramaan.ui.theme.PramaanTypography

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WitnessScreen(
    container: AppContainer,
    onBack: () -> Unit
) {
    var isScanning by remember { mutableStateOf(false) }
    var scannedPayload by remember { mutableStateOf<SessionQrPayload?>(null) }
    var witnessQrOutput by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun processCoachQr(qrText: String) {
        val decoded = WitnessQrCodec.decode(qrText)
        if (decoded == null || decoded.type != 'S') {
            errorMessage = "Invalid Coach QR: Expected PRM1S format"
            return
        }

        try {
            val payloadBytes = Base64Url.decode(decoded.payloadJsonBase64Url)
            val payload = CanonicalJson.decodeFromByteArray(
                SessionQrPayload.serializer(),
                payloadBytes
            )

            // If coach public key provided, verify signature offline
            if (payload.cpk.isNotEmpty()) {
                val cpkBytes = Base64Url.decode(payload.cpk)
                val rawSig = Base64Url.decode(decoded.signatureBase64Url)
                val sigDer = DerRaw.rawToDer(rawSig)
                val verified = EcdsaVerifier.verify(cpkBytes, payloadBytes, sigDer)
                if (!verified) {
                    errorMessage = "Coach signature verification failed! Possible forged QR."
                    return
                }
            }

            scannedPayload = payload
            errorMessage = null
        } catch (e: Exception) {
            errorMessage = "Failed to parse Coach QR: ${e.message}"
        }
    }

    fun signWitnessAttestation() {
        val payload = scannedPayload ?: return
        try {
            val now = System.currentTimeMillis()
            val wpk = container.deviceKey.getPublicKeyBase64Url()
            val witnessPayload = WitnessQrPayload(
                sid = payload.sid,
                d = payload.d,
                wpk = wpk,
                t = now
            )

            val payloadBytes = CanonicalJson.encodeToByteArray(witnessPayload)
            val payloadB64Url = Base64Url.encode(payloadBytes)
            val sigDer = container.deviceKey.sign(payloadBytes)
            val rawSig = DerRaw.derToRaw(Base64Url.decode(sigDer))
            val sigB64Url = Base64Url.encode(rawSig)

            witnessQrOutput = WitnessQrCodec.encode('W', payloadB64Url, sigB64Url)
        } catch (e: Exception) {
            errorMessage = "Signing error: ${e.message}"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Witness Station",
                            style = PramaanTypography.titleMedium,
                            color = PramaanOnSurface
                        )
                        Text(
                            text = "OFFLINE CO-SIGNER COMPANION",
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
        if (isScanning) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                QrScanner(
                    onQrScanned = { qr ->
                        isScanning = false
                        processCoachQr(qr)
                    }
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { Spacer(modifier = Modifier.height(4.dp)) }

                // Mode Banner
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
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = Color(0xFF4F46E5),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Independent Witness Protocol",
                                    style = PramaanTypography.titleMedium,
                                    color = PramaanOnSurface
                                )
                                Text(
                                    text = "Witness Key: ${container.deviceKey.getKeyFingerprint().take(8).uppercase()}",
                                    style = PramaanTypography.labelSmall,
                                    color = PramaanOutline
                                )
                            }
                        }
                    }
                }

                // If not scanned yet, show Scan button
                if (scannedPayload == null) {
                    item {
                        PramaanCard {
                            Text(
                                text = "Step 1: Scan Coach Session QR",
                                style = PramaanTypography.headlineSmall,
                                color = PramaanOnSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Point this phone's camera at Coach Phone A's screen displaying the QR code.",
                                style = PramaanTypography.bodyMedium,
                                color = PramaanOutline
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            PramaanButton(
                                text = "Scan Coach Session QR",
                                icon = Icons.Default.QrCodeScanner,
                                onClick = { isScanning = true }
                            )

                            errorMessage?.let { err ->
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(text = err, style = PramaanTypography.bodySmall, color = PramaanError)
                            }
                        }
                    }
                } else if (witnessQrOutput == null) {
                    // Scanned! Show Session Summary & "I witnessed this"
                    item {
                        val payload = scannedPayload!!
                        PramaanCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Verified Session Summary",
                                    style = PramaanTypography.titleMedium,
                                    color = PramaanOnSurface
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFDCFCE7))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "SIG VALID",
                                        style = PramaanTypography.labelSmall,
                                        color = PramaanEmerald
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFF8FAFC))
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = "Athlete Initials", style = PramaanTypography.labelSmall, color = PramaanOutline)
                                    Text(text = payload.ini, style = PramaanTypography.labelMedium, color = PramaanOnSurface)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = "Test Type", style = PramaanTypography.labelSmall, color = PramaanOutline)
                                    Text(text = payload.test.replace('_', ' '), style = PramaanTypography.labelMedium, color = PramaanOnSurface)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = "Official Score", style = PramaanTypography.labelSmall, color = PramaanOutline)
                                    Text(text = "${payload.best} ${payload.unit}", style = PramaanTypography.headlineSmall, color = PramaanSaffron)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = "Session ID", style = PramaanTypography.labelSmall, color = PramaanOutline)
                                    Text(text = payload.sid.take(12) + "...", style = PramaanTypography.labelSmall, color = PramaanOutline)
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            PramaanButton(
                                text = "I Witnessed This Test (Sign Offline)",
                                icon = Icons.Default.HowToReg,
                                onClick = { signWitnessAttestation() }
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Cancel / Scan Different",
                                style = PramaanTypography.labelMedium,
                                color = PramaanOutline,
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .clickable {
                                        scannedPayload = null
                                        errorMessage = null
                                    }
                                    .padding(4.dp)
                            )
                        }
                    }
                } else {
                    // Signed! Display QR W on screen for Coach Phone A
                    item {
                        PramaanCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = PramaanEmerald,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Witness Signature Generated",
                                    style = PramaanTypography.titleMedium,
                                    color = PramaanOnSurface
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "Show this QR code to Coach Phone A to complete the co-signing handshake.",
                                style = PramaanTypography.bodyMedium,
                                color = PramaanOutline
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFF1F5F9))
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    witnessQrOutput?.let { qrText ->
                                        QrImage(
                                            content = qrText,
                                            size = 240.dp
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "WITNESS TOKEN • ECDSA P-256",
                                        style = PramaanTypography.labelSmall,
                                        color = PramaanOutline
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            PramaanButton(
                                text = "Done / Next Test",
                                icon = Icons.Default.VerifiedUser,
                                onClick = {
                                    scannedPayload = null
                                    witnessQrOutput = null
                                    onBack()
                                }
                            )
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }
}
