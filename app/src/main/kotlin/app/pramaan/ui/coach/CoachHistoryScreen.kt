package app.pramaan.ui.coach

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.pramaan.AppContainer
import app.pramaan.api.dto.CoachHistoryItemDto
import app.pramaan.ui.components.InitialsAvatar
import app.pramaan.ui.components.IntegrityChip
import app.pramaan.ui.components.PramaanCard
import app.pramaan.ui.components.StatusChip
import app.pramaan.ui.theme.PramaanNavy
import app.pramaan.ui.theme.PramaanOnSurface
import app.pramaan.ui.theme.PramaanOnSurfaceVariant
import app.pramaan.ui.theme.PramaanOutline
import app.pramaan.ui.theme.PramaanSurface
import app.pramaan.ui.theme.PramaanTypography
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoachHistoryScreen(
    container: AppContainer,
    onViewCertificate: (sessionId: String) -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var historyItems by remember { mutableStateOf<List<CoachHistoryItemDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    suspend fun loadHistory() {
        isLoading = true
        val res = container.reviewRepository.getHistory()
        if (res.isSuccess) {
            historyItems = res.getOrNull() ?: emptyList()
        }
        isLoading = false
    }

    LaunchedEffect(Unit) {
        loadHistory()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "My Review History",
                        style = PramaanTypography.titleMedium,
                        color = PramaanOnSurface
                    )
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
                actions = {
                    IconButton(onClick = { scope.launch { loadHistory() } }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
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

        if (historyItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = PramaanOutline,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No Reviews Recorded",
                        style = PramaanTypography.titleSmall,
                        color = PramaanOnSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Submissions that you review and sign will appear in this audit log.",
                        style = PramaanTypography.bodySmall,
                        color = PramaanOnSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            items(historyItems) { item ->
                PramaanCard(
                    modifier = Modifier.clickable {
                        if (item.certificateJws != null) {
                            onViewCertificate(item.sessionId)
                        }
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
                            Spacer(modifier = Modifier.height(4.dp))
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
                                StatusChip(status = item.status)
                                IntegrityChip(integrity = item.integrity)
                            }
                        }

                        if (item.certificateJws != null) {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "View Certificate",
                                tint = PramaanOutline
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}
