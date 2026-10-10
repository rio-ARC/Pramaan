package app.pramaan.ui.screens

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
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pramaan.AppContainer
import app.pramaan.core.model.Badge
import app.pramaan.core.model.TestType
import app.pramaan.data.SessionEntity
import app.pramaan.ui.components.BadgeChip
import app.pramaan.ui.components.InitialsAvatar
import app.pramaan.ui.components.PramaanCard
import app.pramaan.ui.theme.PramaanEmerald
import app.pramaan.ui.theme.PramaanOnSurface
import app.pramaan.ui.theme.PramaanOnSurfaceVariant
import app.pramaan.ui.theme.PramaanSaffron
import app.pramaan.ui.theme.PramaanSurface
import app.pramaan.ui.theme.PramaanTypography
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(private val container: AppContainer) : ViewModel() {
    val pendingSyncCount: StateFlow<Int> = container.sessionDao.getPendingSyncCountFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val recentSessions: StateFlow<List<SessionEntity>> = container.sessionDao.getAllFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val stationId: String = container.deviceKey.getKeyFingerprint()
}

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToSetup: (TestType) -> Unit,
    onNavigateToAthletes: () -> Unit,
    onNavigateToWitness: () -> Unit,
    onNavigateToImport: () -> Unit,
    onNavigateToSync: () -> Unit,
    onNavigateToResult: (String) -> Unit
) {
    val pendingSyncs by viewModel.pendingSyncCount.collectAsState()
    val sessions by viewModel.recentSessions.collectAsState()

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                NavigationBarItem(
                    selected = true,
                    onClick = { },
                    icon = { Icon(Icons.Default.FitnessCenter, contentDescription = "Tests") },
                    label = { Text("Tests", style = PramaanTypography.labelSmall) }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onNavigateToAthletes,
                    icon = { Icon(Icons.Default.Person, contentDescription = "Athletes") },
                    label = { Text("Athletes", style = PramaanTypography.labelSmall) }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onNavigateToWitness,
                    icon = { Icon(Icons.Default.Shield, contentDescription = "Witness") },
                    label = { Text("Witness", style = PramaanTypography.labelSmall) }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onNavigateToSync,
                    icon = { Icon(Icons.Default.CloudUpload, contentDescription = "Sync") },
                    label = { Text("Sync", style = PramaanTypography.labelSmall) }
                )
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
            item {
                Spacer(modifier = Modifier.height(8.dp))
                // App Bar Header (Stitch Screen 1)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(PramaanSaffron),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "PRAMAAN",
                                    style = PramaanTypography.titleLarge,
                                    color = PramaanOnSurface
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFFDCFCE7))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "READY",
                                        style = PramaanTypography.labelSmall,
                                        color = PramaanEmerald
                                    )
                                }
                            }
                            Text(
                                text = "SPORTS TALENT VERIFICATION",
                                style = PramaanTypography.labelSmall,
                                color = PramaanOnSurfaceVariant
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE2E8F0)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = viewModel.stationId.take(2).uppercase(),
                            style = PramaanTypography.labelMedium,
                            color = PramaanOnSurface
                        )
                    }
                }
            }

            // Field Station Card
            item {
                PramaanCard {
                    Text(
                        text = "FIELD STATION • ID ${viewModel.stationId.uppercase()}",
                        style = PramaanTypography.labelSmall,
                        color = PramaanOnSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Pramaan Verification Station",
                        style = PramaanTypography.headlineMedium,
                        color = PramaanOnSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(PramaanEmerald)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Station Ready",
                                style = PramaanTypography.bodySmall,
                                color = PramaanOnSurface
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFEEF2FF))
                                .clickable { onNavigateToSync() }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "$pendingSyncs pending syncs",
                                style = PramaanTypography.labelSmall,
                                color = Color(0xFF4F46E5)
                            )
                        }
                    }
                }
            }

            // Active Assessments Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Active Assessments",
                        style = PramaanTypography.titleLarge,
                        color = PramaanOnSurface
                    )
                    Text(
                        text = "ON-DEVICE AI",
                        style = PramaanTypography.labelSmall,
                        color = PramaanOnSurfaceVariant
                    )
                }
            }

            // Test 1: Standing Vertical Jump
            item {
                PramaanCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFEEF2FF))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Best of 3 Attempts",
                                style = PramaanTypography.labelSmall,
                                color = Color(0xFF4F46E5)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Standing Vertical Jump",
                        style = PramaanTypography.headlineSmall,
                        color = PramaanOnSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Measures explosive leg power with dual optical AI verification and launch pad velocity capture.",
                        style = PramaanTypography.bodySmall,
                        color = PramaanOnSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(PramaanSaffron)
                                .clickable { onNavigateToSetup(TestType.VERTICAL_JUMP) }
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = "Start Test →",
                                style = PramaanTypography.labelLarge,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // Test 2: Sit-ups (30 Seconds)
            item {
                PramaanCard {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFDCFCE7))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Core Endurance • 30s",
                            style = PramaanTypography.labelSmall,
                            color = PramaanEmerald
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Sit-ups (30 Seconds)",
                        style = PramaanTypography.headlineSmall,
                        color = PramaanOnSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "30s timed cadence with automatic rep counting and spinal flexion angle validation.",
                        style = PramaanTypography.bodySmall,
                        color = PramaanOnSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(PramaanSaffron)
                                .clickable { onNavigateToSetup(TestType.SIT_UPS) }
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = "Start Test →",
                                style = PramaanTypography.labelLarge,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // Quick Field Actions
            item {
                Text(
                    text = "QUICK FIELD ACTIONS",
                    style = PramaanTypography.labelSmall,
                    color = PramaanOnSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PramaanCard(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onNavigateToWitness() }
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            tint = PramaanSaffron,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Witness a Test",
                            style = PramaanTypography.titleMedium,
                            color = PramaanOnSurface
                        )
                        Text(
                            text = "Scan coach QR",
                            style = PramaanTypography.bodySmall,
                            color = PramaanOnSurfaceVariant
                        )
                    }

                    PramaanCard(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onNavigateToImport() }
                    ) {
                        Icon(
                            imageVector = Icons.Default.VideoLibrary,
                            contentDescription = null,
                            tint = Color(0xFF4F46E5),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Import Clip",
                            style = PramaanTypography.titleMedium,
                            color = PramaanOnSurface
                        )
                        Text(
                            text = "Evaluate footage",
                            style = PramaanTypography.bodySmall,
                            color = PramaanOnSurfaceVariant
                        )
                    }
                }
            }

            // Recent Field Logs
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Field Logs",
                        style = PramaanTypography.titleLarge,
                        color = PramaanOnSurface
                    )
                }
            }

            if (sessions.isEmpty()) {
                item {
                    Text(
                        text = "No assessments recorded yet. Run your first test above!",
                        style = PramaanTypography.bodySmall,
                        color = PramaanOnSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            } else {
                items(sessions) { session ->
                    PramaanCard(
                        modifier = Modifier.clickable { onNavigateToResult(session.id) }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = session.test.replace('_', ' '),
                                    style = PramaanTypography.titleMedium,
                                    color = PramaanOnSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Session • ${session.id.take(8)}",
                                    style = PramaanTypography.bodySmall,
                                    color = PramaanOnSurfaceVariant
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "${session.best} ${session.unit}",
                                    style = PramaanTypography.titleLarge,
                                    color = PramaanOnSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                val badge = try {
                                    Badge.valueOf(session.badge)
                                } catch (_: Exception) {
                                    Badge.VERIFIED
                                }
                                BadgeChip(badge = badge)
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
