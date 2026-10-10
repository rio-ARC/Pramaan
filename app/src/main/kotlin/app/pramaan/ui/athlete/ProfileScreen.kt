package app.pramaan.ui.athlete

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.pramaan.AppContainer
import app.pramaan.api.dto.AccountProfileDto
import app.pramaan.ui.components.InitialsAvatar
import app.pramaan.ui.components.PramaanButton
import app.pramaan.ui.components.PramaanCard
import app.pramaan.ui.components.PramaanOutlinedButton
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    container: AppContainer,
    onNavigateToImport: () -> Unit,
    onSignOut: () -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var profile by remember { mutableStateOf<AccountProfileDto?>(null) }
    var accountId by remember { mutableStateOf("") }
    var serverUrl by remember { mutableStateOf("") }
    var deviceKeyFingerprint by remember { mutableStateOf("") }

    var isTampering by remember { mutableStateOf(false) }
    var tamperAlertMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        profile = container.authStore.getProfile()
        accountId = container.authStore.accountIdFlow.first() ?: ""
        serverUrl = container.authStore.serverUrlFlow.first() ?: "http://10.0.2.2:8080"
        deviceKeyFingerprint = container.deviceKey.getKeyFingerprint()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Athlete Profile",
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Profile Card
            PramaanCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    InitialsAvatar(
                        initials = profile?.name?.take(2)?.uppercase() ?: "AT",
                        size = 56.dp
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = profile?.name ?: "Athlete",
                            style = PramaanTypography.titleMedium,
                            color = PramaanOnSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Account: $accountId",
                            style = PramaanTypography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = PramaanNavy
                        )
                        Text(
                            text = "Sex: ${profile?.sex ?: "—"}  •  Height: ${profile?.heightCm?.toInt() ?: "—"} cm  •  Weight: ${profile?.weightKg?.toInt() ?: "—"} kg",
                            style = PramaanTypography.bodySmall,
                            color = PramaanOnSurfaceVariant
                        )
                    }
                }
            }

            // Security & Hardware Binding Card
            PramaanCard {
                Text(
                    text = "Hardware Security Binding",
                    style = PramaanTypography.titleSmall,
                    color = PramaanOnSurface
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = null,
                            tint = PramaanEmerald,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Android Keystore Key",
                            style = PramaanTypography.bodyMedium,
                            color = PramaanOnSurface
                        )
                    }
                    Text(
                        text = deviceKeyFingerprint,
                        style = PramaanTypography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = PramaanOutline
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Divider(color = Color(0xFFF1F5F9))
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Dns,
                            contentDescription = null,
                            tint = PramaanNavy,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Registered Server",
                            style = PramaanTypography.bodyMedium,
                            color = PramaanOnSurface
                        )
                    }
                    Text(
                        text = serverUrl,
                        style = PramaanTypography.labelSmall,
                        color = PramaanOnSurfaceVariant
                    )
                }
            }

            // Demo & Judge Tools
            PramaanCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.BugReport,
                        contentDescription = null,
                        tint = PramaanSaffron,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Demo Tools (Live Evaluation)",
                        style = PramaanTypography.titleSmall,
                        color = PramaanOnSurface
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Tools for demonstration to simulate external video ingestion and demonstrate cryptographic tamper detection.",
                    style = PramaanTypography.bodySmall,
                    color = PramaanOnSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))

                PramaanOutlinedButton(
                    text = "Import Reference Video",
                    icon = Icons.Default.UploadFile,
                    onClick = onNavigateToImport
                )

                Spacer(modifier = Modifier.height(10.dp))

                PramaanOutlinedButton(
                    text = if (isTampering) "Injecting byte tamper..." else "Simulate Tampering on Latest Session",
                    icon = Icons.Default.GppBad,
                    onClick = {
                        if (isTampering) return@PramaanOutlinedButton
                        isTampering = true
                        scope.launch {
                            val list = container.sessionDao.getAll()
                            val latest = list.firstOrNull()
                            if (latest == null) {
                                tamperAlertMessage = "No sessions found to tamper. Record a test first!"
                                isTampering = false
                                return@launch
                            }
                            val (_, outcome) = withContext(Dispatchers.IO) {
                                container.tamperSimulator.simulateTampering(latest.id)
                            }
                            tamperAlertMessage = "Altered byte in video file for session ${latest.id.take(8)}...\n\nIntegrity Engine detected mismatch!\nBadge downgraded to: ${outcome.badge.name}\nServer integrity set to TAMPERED."
                            isTampering = false
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Sign Out
            PramaanButton(
                text = "Sign Out",
                icon = Icons.Default.ExitToApp,
                onClick = {
                    scope.launch {
                        container.authRepository.logout()
                        onSignOut()
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (tamperAlertMessage != null) {
        AlertDialog(
            onDismissRequest = { tamperAlertMessage = null },
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
                    text = "Tamper Simulation Result",
                    style = PramaanTypography.titleMedium,
                    color = PramaanOnSurface
                )
            },
            text = {
                Text(
                    text = tamperAlertMessage ?: "",
                    style = PramaanTypography.bodyMedium,
                    color = PramaanOnSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = { tamperAlertMessage = null }) {
                    Text("OK", color = PramaanSaffron)
                }
            }
        )
    }
}
