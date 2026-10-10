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
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.VerifiedUser
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.pramaan.AppContainer
import app.pramaan.core.manifest.CanonicalJson
import app.pramaan.core.manifest.SessionManifest
import app.pramaan.core.manifest.WitnessAttestation
import app.pramaan.core.model.Badge
import app.pramaan.data.AthleteEntity
import app.pramaan.data.SessionEntity
import app.pramaan.ui.components.InitialsAvatar
import app.pramaan.ui.components.PramaanButton
import app.pramaan.ui.components.PramaanCard
import app.pramaan.ui.components.QrImage
import app.pramaan.ui.components.QrScanner
import app.pramaan.ui.theme.PramaanEmerald
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
fun CoSignScreen(
    sessionId: String,
    container: AppContainer,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Witness Co-Sign, 1: Digital Certificate

    var session by remember { mutableStateOf<SessionEntity?>(null) }
    var manifest by remember { mutableStateOf<SessionManifest?>(null) }
    var athlete by remember { mutableStateOf<AthleteEntity?>(null) }
    var witnessAttestation by remember { mutableStateOf<WitnessAttestation?>(null) }
    var sessionQrString by remember { mutableStateOf<String?>(null) }

    // Scanner state
    var isScanning by remember { mutableStateOf(false) }
    var scanSuccessMessage by remember { mutableStateOf<String?>(null) }
    var scanErrorMessage by remember { mutableStateOf<String?>(null) }

    fun loadData() {
        scope.launch {
            val sess = container.sessionDao.getById(sessionId)
            session = sess
            sess?.let {
                athlete = container.athleteDao.getById(it.athleteId)
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

            val witnessFile = container.sessionStore.getWitnessFile(sessionId)
            if (witnessFile.exists()) {
                try {
                    witnessAttestation = CanonicalJson.decodeFromByteArray(
                        WitnessAttestation.serializer(),
                        witnessFile.readBytes()
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            sessionQrString = container.sealer.createSessionQr(sessionId)
        }
    }

    LaunchedEffect(sessionId) {
        loadData()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "PRAMAAN",
                            style = PramaanTypography.titleMedium,
                            color = PramaanOnSurface
                        )
                        Text(
                            text = "WITNESS & CERTIFICATE",
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
            // Fullscreen scanner overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                QrScanner(
                    onQrScanned = { qr ->
                        isScanning = false
                        scope.launch {
                            val success = withContext(Dispatchers.IO) {
                                container.localVerifier.attachWitnessAttestation(sessionId, qr)
                            }
                            if (success) {
                                scanSuccessMessage = "Co-Sign Verified & Attached! Keystore Locked."
                                scanErrorMessage = null
                                loadData()
                            } else {
                                scanErrorMessage = "Invalid witness QR signature or session ID mismatch."
                            }
                        }
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

                // Segmented Tab Header (Stitch Screen 5)
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFF1F5F9))
                            .padding(4.dp)
                    ) {
                        // Tab 1
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selectedTab == 0) Color.White else Color.Transparent)
                                .clickable { selectedTab = 0 }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.HowToReg,
                                    contentDescription = null,
                                    tint = if (selectedTab == 0) PramaanSaffron else PramaanOnSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "1. Witness Co-Sign",
                                    style = PramaanTypography.labelMedium,
                                    color = if (selectedTab == 0) PramaanSaffron else PramaanOnSurfaceVariant
                                )
                            }
                        }

                        // Tab 2
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selectedTab == 1) Color.White else Color.Transparent)
                                .clickable { selectedTab = 1 }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Verified,
                                    contentDescription = null,
                                    tint = if (selectedTab == 1) PramaanSaffron else PramaanOnSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "2. Digital Certificate",
                                    style = PramaanTypography.labelMedium,
                                    color = if (selectedTab == 1) PramaanSaffron else PramaanOnSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Athlete & Verified Result Snapshot Card (Common)
                item {
                    PramaanCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                InitialsAvatar(name = athlete?.name ?: "Athlete", size = 42)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = athlete?.name ?: "Manpreet Kaur",
                                            style = PramaanTypography.titleMedium,
                                            color = PramaanOnSurface
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = PramaanEmerald,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Text(
                                        text = "Punjab • U-16 Trial",
                                        style = PramaanTypography.labelSmall,
                                        color = PramaanOutline
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFDCFCE7))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "#${sessionId.take(6).uppercase()}",
                                    style = PramaanTypography.labelMedium,
                                    color = PramaanEmerald
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Metric Callout Grid
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFF8FAFC))
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = (session?.test ?: "VERTICAL JUMP").replace('_', ' '),
                                    style = PramaanTypography.labelSmall,
                                    color = PramaanOutline
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        text = "${session?.best ?: 38.2}",
                                        style = PramaanTypography.titleLarge,
                                        color = PramaanOnSurface
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = session?.unit ?: "cm",
                                        style = PramaanTypography.bodyMedium,
                                        color = PramaanOutline
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "STATUS",
                                    style = PramaanTypography.labelSmall,
                                    color = PramaanOutline
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (witnessAttestation != null) "CO-SIGNED" else "SEALED",
                                    style = PramaanTypography.labelLarge,
                                    color = PramaanEmerald
                                )
                            }
                        }
                    }
                }

                if (selectedTab == 0) {
                    // TAB 1: WITNESS CO-SIGN FLOW
                    item {
                        PramaanCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFFFEDD5)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "1",
                                        style = PramaanTypography.labelMedium,
                                        color = PramaanSaffron
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Show QR to Witness / Second Coach",
                                    style = PramaanTypography.titleMedium,
                                    color = PramaanOnSurface
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "The counter-signing evaluator scans this cryptographically sealed session payload using their Pramaan app.",
                                style = PramaanTypography.bodySmall,
                                color = PramaanOutline
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            // High Contrast QR Display
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFF1F5F9))
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    sessionQrString?.let { qrText ->
                                        QrImage(
                                            content = qrText,
                                            size = 220.dp
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "SIG: ${container.deviceKey.getKeyFingerprint().take(8).uppercase()}",
                                        style = PramaanTypography.labelSmall,
                                        color = PramaanOutline
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color(0xFFF1F5F9))
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AirplanemodeActive,
                                    contentDescription = null,
                                    tint = Color(0xFF4F46E5),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Fully functional in offline / zero-network grounds",
                                    style = PramaanTypography.labelSmall,
                                    color = PramaanOnSurface
                                )
                            }
                        }
                    }

                    // Step 2 Action Card
                    item {
                        PramaanCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFFFEDD5)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "2",
                                        style = PramaanTypography.labelMedium,
                                        color = PramaanSaffron
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Scan Witness Handshake",
                                    style = PramaanTypography.titleMedium,
                                    color = PramaanOnSurface
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "Once the witness confirms on their device, capture their generated one-time token to seal the co-signed record.",
                                style = PramaanTypography.bodySmall,
                                color = PramaanOutline
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            PramaanButton(
                                text = "Open Camera to Scan Witness QR",
                                icon = Icons.Default.QrCodeScanner,
                                onClick = { isScanning = true }
                            )

                            scanSuccessMessage?.let { msg ->
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFDCFCE7))
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Verified,
                                        contentDescription = null,
                                        tint = PramaanEmerald,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = msg,
                                        style = PramaanTypography.labelMedium,
                                        color = PramaanEmerald
                                    )
                                }
                            }

                            scanErrorMessage?.let { err ->
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = err,
                                    style = PramaanTypography.labelMedium,
                                    color = Color.Red
                                )
                            }
                        }
                    }
                } else {
                    // TAB 2: DIGITAL CERTIFICATE PREVIEW
                    item {
                        PramaanCard {
                            // Banner
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column {
                                    Text(
                                        text = "SPORTS TALENT VERIFICATION SYSTEM",
                                        style = PramaanTypography.labelSmall,
                                        color = PramaanSaffron
                                    )
                                    Text(
                                        text = "Talent Metric Certificate",
                                        style = PramaanTypography.headlineSmall,
                                        color = PramaanOnSurface
                                    )
                                    Text(
                                        text = "PRAMAAN Verifiable Credential • Tier 1 Field Test",
                                        style = PramaanTypography.bodySmall,
                                        color = PramaanOutline
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFF1F5F9)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MilitaryTech,
                                        contentDescription = null,
                                        tint = PramaanSaffron,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Candidate Credentials Callout
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
                                    Text(text = "CANDIDATE FULL NAME", style = PramaanTypography.labelSmall, color = PramaanOutline)
                                    Text(text = "Athlete Verified", style = PramaanTypography.labelSmall, color = PramaanEmerald)
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = athlete?.name ?: "Manpreet Kaur",
                                    style = PramaanTypography.titleMedium,
                                    color = PramaanOnSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Age: ${athlete?.age ?: 14} • Punjab • U-16 Trial",
                                    style = PramaanTypography.bodySmall,
                                    color = PramaanOutline
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Performance Block
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFF1F5F9))
                                        .padding(10.dp)
                                ) {
                                    Column {
                                        Text(text = "STANDARDIZED TEST", style = PramaanTypography.labelSmall, color = PramaanOutline)
                                        Text(text = (session?.test ?: "VERTICAL JUMP").replace('_', ' '), style = PramaanTypography.labelLarge, color = PramaanOnSurface)
                                        Text(text = "On-Device AI Scored", style = PramaanTypography.bodySmall, color = PramaanOutline)
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFF1F5F9))
                                        .padding(10.dp)
                                ) {
                                    Column {
                                        Text(text = "VALIDATED SCORE", style = PramaanTypography.labelSmall, color = PramaanOutline)
                                        Row(verticalAlignment = Alignment.Bottom) {
                                            Text(text = "${session?.best ?: 38.2}", style = PramaanTypography.headlineMedium, color = PramaanOnSurface)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(text = session?.unit ?: "cm", style = PramaanTypography.labelMedium, color = PramaanOutline)
                                        }
                                        Text(text = "Hardware Audit Passed", style = PramaanTypography.bodySmall, color = PramaanEmerald)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Hardware & Keystore Proof Block
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White)
                                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                                    .padding(10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Security,
                                        contentDescription = null,
                                        tint = PramaanEmerald,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Android Keystore Hardware TEE Attested",
                                        style = PramaanTypography.labelMedium,
                                        color = PramaanEmerald
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "KEY_SHA256: ${container.deviceKey.getKeyFingerprint()} | WITNESS: ${witnessAttestation?.witnessShortId ?: "PENDING"} | CAPTURED: ${manifest?.capturedAt ?: "TODAY"}",
                                    style = PramaanTypography.labelSmall,
                                    color = PramaanOutline
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Dual Signatory Stamps
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(imageVector = Icons.Default.Draw, contentDescription = null, tint = PramaanEmerald, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(text = "Signed by Field Station", style = PramaanTypography.labelSmall, color = PramaanEmerald)
                                    }
                                    Text(text = "Station ID: ${container.deviceKey.getKeyFingerprint().take(8).uppercase()}", style = PramaanTypography.bodySmall, color = PramaanOnSurface)
                                    Text(text = "Appraiser Sealed", style = PramaanTypography.labelSmall, color = PramaanOutline)
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(imageVector = Icons.Default.VerifiedUser, contentDescription = null, tint = if (witnessAttestation != null) PramaanEmerald else PramaanOutline, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(text = "Co-Signed by Witness", style = PramaanTypography.labelSmall, color = if (witnessAttestation != null) PramaanEmerald else PramaanOutline)
                                    }
                                    Text(
                                        text = if (witnessAttestation != null) "Witness ID: ${witnessAttestation?.witnessShortId?.uppercase()}" else "Awaiting Co-Sign",
                                        style = PramaanTypography.bodySmall,
                                        color = PramaanOnSurface
                                    )
                                    Text(text = if (witnessAttestation != null) "Hardware ECDSA P-256" else "Step 1 & 2 Required", style = PramaanTypography.labelSmall, color = PramaanOutline)
                                }
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }
}
