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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import app.pramaan.AppContainer
import app.pramaan.core.model.Badge
import app.pramaan.data.SessionEntity
import app.pramaan.ui.components.BadgeChip
import app.pramaan.ui.components.PramaanButton
import app.pramaan.ui.components.PramaanCard
import app.pramaan.ui.theme.PramaanEmerald
import app.pramaan.ui.theme.PramaanError
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
fun SyncScreen(
    container: AppContainer,
    onNavigateToResult: (String) -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var serverUrl by remember { mutableStateOf("http://10.0.2.2:8080") }
    var isSyncing by remember { mutableStateOf(false) }
    var syncStatusText by remember { mutableStateOf<String?>(null) }
    var syncErrorMessage by remember { mutableStateOf<String?>(null) }

    val sessions by container.sessionDao.getAllFlow()
        .collectAsState(initial = emptyList())

    val pendingCount = sessions.count { !it.synced }

    fun syncAll() {
        if (isSyncing) return
        isSyncing = true
        syncStatusText = "Connecting to verification server..."
        syncErrorMessage = null

        scope.launch {
            try {
                val pending = sessions.filter { !it.synced }
                var successCount = 0

                for (sess in pending) {
                    syncStatusText = "Uploading session #${sess.id.take(8)}..."
                    val res = withContext(Dispatchers.IO) {
                        container.syncClient.uploadSession(serverUrl, sess.id)
                    }

                    if (res != null) {
                        successCount++
                        // Update Room DB
                        container.sessionDao.update(
                            sess.copy(
                                badge = res.serverBadge.name,
                                synced = true
                            )
                        )
                        // Save certificate if present
                        res.certificate?.let { certJws ->
                            val certFile = container.sessionStore.getCertFile(sess.id)
                            certFile.writeText(certJws)
                        }
                    }
                }

                syncStatusText = "Synced $successCount session(s) successfully!"
                isSyncing = false
            } catch (e: Exception) {
                isSyncing = false
                syncErrorMessage = "Sync failed: ${e.message}"
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Central Server Sync",
                            style = PramaanTypography.titleMedium,
                            color = PramaanOnSurface
                        )
                        Text(
                            text = "STATE & NATIONAL SYNC",
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // Server Config Card
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
                                imageVector = Icons.Default.Dns,
                                contentDescription = null,
                                tint = Color(0xFF4F46E5),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Verification Server",
                                style = PramaanTypography.titleMedium,
                                color = PramaanOnSurface
                            )
                            Text(
                                text = "Independent laptop or cloud authority",
                                style = PramaanTypography.labelSmall,
                                color = PramaanOutline
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = { serverUrl = it },
                        label = { Text("Server Endpoint URL") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    PramaanButton(
                        text = if (isSyncing) "Syncing..." else "Sync $pendingCount Pending Session(s)",
                        icon = Icons.Default.CloudUpload,
                        onClick = { syncAll() },
                        enabled = !isSyncing && pendingCount > 0
                    )

                    syncStatusText?.let { status ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = status, style = PramaanTypography.bodyMedium, color = PramaanEmerald)
                    }

                    syncErrorMessage?.let { err ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = err, style = PramaanTypography.bodyMedium, color = PramaanError)
                    }
                }
            }

            // Session List Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Local Field Records (${sessions.size})",
                        style = PramaanTypography.titleMedium,
                        color = PramaanOnSurface
                    )
                    Text(
                        text = "$pendingCount pending",
                        style = PramaanTypography.labelSmall,
                        color = if (pendingCount > 0) PramaanSaffron else PramaanEmerald
                    )
                }
            }

            // Session items
            items(sessions) { sess ->
                PramaanCard(
                    modifier = Modifier.clickable { onNavigateToResult(sess.id) }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = sess.test.replace('_', ' '),
                                    style = PramaanTypography.titleMedium,
                                    color = PramaanOnSurface
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                if (sess.synced) {
                                    Icon(
                                        imageVector = Icons.Default.CloudDone,
                                        contentDescription = "Synced",
                                        tint = PramaanEmerald,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Session #${sess.id.take(8).uppercase()} • ${if (sess.synced) "Synced with Server" else "Local Only"}",
                                style = PramaanTypography.bodySmall,
                                color = PramaanOutline
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${sess.best} ${sess.unit}",
                                style = PramaanTypography.titleLarge,
                                color = PramaanOnSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            val badge = try {
                                Badge.valueOf(sess.badge)
                            } catch (_: Exception) {
                                Badge.VERIFIED
                            }
                            BadgeChip(badge = badge)
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}
