package app.pramaan.ui.coach

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sports
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pramaan.AppContainer
import app.pramaan.api.dto.AccountProfileDto
import app.pramaan.api.dto.CoachQueueItemDto
import app.pramaan.ui.components.InitialsAvatar
import app.pramaan.ui.components.IntegrityChip
import app.pramaan.ui.components.PramaanButton
import app.pramaan.ui.components.PramaanCard
import app.pramaan.ui.theme.PramaanNavy
import app.pramaan.ui.theme.PramaanOnSurface
import app.pramaan.ui.theme.PramaanOnSurfaceVariant
import app.pramaan.ui.theme.PramaanOutline
import app.pramaan.ui.theme.PramaanSaffron
import app.pramaan.ui.theme.PramaanSurface
import app.pramaan.ui.theme.PramaanTypography
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoachHomeScreen(
    container: AppContainer,
    onScanQr: () -> Unit,
    onSelectSession: (sessionId: String, isScanned: Boolean) -> Unit,
    onViewHistory: () -> Unit,
    onLogout: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val coachProfile by container.authStore.profileFlow.collectAsState(initial = null)
    val coachId by container.authStore.accountIdFlow.collectAsState(initial = "COA-01")

    var queueItems by remember { mutableStateOf<List<CoachQueueItemDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    suspend fun fetchQueue() {
        val result = container.reviewRepository.getQueue()
        if (result.isSuccess) {
            queueItems = result.getOrNull() ?: emptyList()
            errorMessage = null
        } else {
            errorMessage = result.exceptionOrNull()?.message ?: "Failed to load queue"
        }
        isLoading = false
    }

    // 5s periodic polling while on this screen
    LaunchedEffect(Unit) {
        fetchQueue()
        while (isActive) {
            delay(5000)
            fetchQueue()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Coach • ${coachId ?: "COA-01"}",
                            style = PramaanTypography.titleMedium,
                            color = PramaanSaffron,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = coachProfile?.name ?: "Official SAI Coach",
                            style = PramaanTypography.labelSmall,
                            color = PramaanOnSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onViewHistory) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "Review History",
                            tint = PramaanOnSurface
                        )
                    }
                    IconButton(onClick = {
                        scope.launch {
                            container.authRepository.logout()
                            onLogout()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.ExitToApp,
                            contentDescription = "Logout",
                            tint = PramaanOnSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onScanQr,
                containerColor = PramaanSaffron,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.QrCodeScanner, contentDescription = "Scan")
                    Text("Scan Athlete QR", fontWeight = FontWeight.Bold)
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
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // 1. Coach Profile Card
            item {
                PramaanCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        InitialsAvatar(
                            name = coachProfile?.name ?: "Coach",
                            size = 52
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = coachProfile?.name ?: "Coach",
                                style = PramaanTypography.titleMedium,
                                color = PramaanOnSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Coach ID: ${coachId ?: "COA-01"}  •  ${coachProfile?.district ?: "National Trial"}",
                                style = PramaanTypography.bodySmall,
                                color = PramaanOnSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFFFFEDD5))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "OFFICIAL SAI VERIFIER",
                                    style = PramaanTypography.labelSmall.copy(fontSize = 10.sp),
                                    color = PramaanSaffron,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // 2. Queue Header & Count
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Pending Queue",
                            style = PramaanTypography.titleMedium,
                            color = PramaanOnSurface
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(PramaanSaffron)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = queueItems.size.toString(),
                                style = PramaanTypography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    IconButton(onClick = { scope.launch { fetchQueue() } }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = PramaanOnSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // 3. Queue List
            if (isLoading && queueItems.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = PramaanSaffron)
                    }
                }
            } else if (queueItems.isEmpty()) {
                item {
                    PramaanCard {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.HourglassTop,
                                contentDescription = null,
                                tint = PramaanOutline,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Queue is Empty",
                                style = PramaanTypography.titleSmall,
                                color = PramaanOnSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Athlete submissions will appear here in real time. Or tap 'Scan Athlete QR' to verify in person.",
                                style = PramaanTypography.bodySmall,
                                color = PramaanOnSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(queueItems) { item ->
                    PramaanCard(
                        modifier = Modifier.clickable {
                            onSelectSession(item.sessionId, false)
                        }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            InitialsAvatar(name = item.athleteName, size = 44)
                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.athleteName,
                                    style = PramaanTypography.titleSmall,
                                    color = PramaanOnSurface
                                )
                                Text(
                                    text = "${item.athleteId} • ${item.test.replace("_", " ")}",
                                    style = PramaanTypography.bodySmall,
                                    color = PramaanOnSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${item.best} ${item.unit}",
                                        style = PramaanTypography.titleMedium,
                                        color = PramaanNavy,
                                        fontWeight = FontWeight.Bold
                                    )
                                    IntegrityChip(integrity = item.integrity)
                                }
                            }

                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "Review",
                                tint = PramaanOutline
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(72.dp)) }
        }
    }
}
