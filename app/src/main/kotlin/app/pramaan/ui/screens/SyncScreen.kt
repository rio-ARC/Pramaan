package app.pramaan.ui.screens

import android.content.Context
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.pramaan.AppContainer
import app.pramaan.core.model.Badge
import app.pramaan.data.HealthCheckResult
import app.pramaan.data.SessionEntity
import app.pramaan.ui.components.BadgeChip
import app.pramaan.ui.components.PramaanButton
import app.pramaan.ui.components.PramaanCard
import app.pramaan.ui.components.QrScanner
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val prefs = remember { context.getSharedPreferences("pramaan_prefs", Context.MODE_PRIVATE) }
    var serverUrl by remember { mutableStateOf(prefs.getString("server_url", "") ?: "") }

    var isScanningServerQr by remember { mutableStateOf(false) }
    var isTestingConnection by remember { mutableStateOf(false) }
    var healthResult by remember { mutableStateOf<HealthCheckResult?>(null) }

    var isSyncing by remember { mutableStateOf(false) }
    var syncStatusText by remember { mutableStateOf<String?>(null) }
    var syncErrorMessage by remember { mutableStateOf<String?>(null) }

    val sessions by container.sessionDao.getAllFlow()
        .collectAsState(initial = emptyList())

    val pendingCount = sessions.count { !it.synced }

    fun updateServerUrl(newUrl: String) {
        serverUrl = newUrl
        prefs.edit().putString("server_url", newUrl).apply()
    }

    fun testConnection(targetUrl: String = serverUrl) {
        if (targetUrl.isBlank()) {
            healthResult = HealthCheckResult(false, 0, "Please enter or scan a Server Endpoint URL first.")
            return
        }
        isTestingConnection = true
        healthResult = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                container.syncClient.checkHealth(targetUrl)
            }
            healthResult = result
            isTestingConnection = false
        }
    }

    fun syncAll() {
        if (isSyncing) return
        if (serverUrl.isBlank()) {
            syncErrorMessage = "Server Endpoint URL is required. Scan laptop QR or enter URL."
            return
        }
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

    if (isScanningServerQr) {
        // Fullscreen QR Scanner for Server Pairing
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Scan Server Pairing QR", style = PramaanTypography.titleMedium) },
                    navigationIcon = {
                        IconButton(onClick = { isScanningServerQr = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                )
            }
        ) { pad ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(pad)
            ) {
                QrScanner(
                    onQrScanned = { scanned ->
                        val url = scanned.trim()
                        updateServerUrl(url)
                        isScanningServerQr = false
                        testConnection(url)
                    }
                )
            }
        }
        return
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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

                        // Scan Server QR Action
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFFFEDD5))
                                .clickable { isScanningServerQr = true }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.QrCodeScanner,
                                    contentDescription = "Scan QR",
                                    tint = PramaanSaffron,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Scan Server QR",
                                    style = PramaanTypography.labelSmall,
                                    color = PramaanSaffron
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = { updateServerUrl(it) },
                        label = { Text("Server Endpoint URL (e.g. http://192.168.1.45:8080)") },
                        placeholder = { Text("Scan QR or enter http://<laptop-ip>:8080") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Test Connection Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(
                            onClick = { testConnection() },
                            enabled = !isTestingConnection && serverUrl.isNotBlank()
                        ) {
                            if (isTestingConnection) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = PramaanSaffron
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Testing...")
                            } else {
                                Icon(
                                    imageVector = Icons.Default.NetworkCheck,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Test Connection")
                            }
                        }
                    }

                    // Diagnostic status banner
                    healthResult?.let { res ->
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (res.isSuccess) Color(0xFFDCFCE7) else Color(0xFFFEE2E2))
                                .padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (res.isSuccess) Icons.Default.CheckCircle else Icons.Default.Close,
                                    contentDescription = null,
                                    tint = if (res.isSuccess) PramaanEmerald else PramaanError,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = res.message,
                                    style = PramaanTypography.bodySmall,
                                    color = if (res.isSuccess) PramaanEmerald else PramaanError
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    PramaanButton(
                        text = if (isSyncing) "Syncing..." else "Sync $pendingCount Pending Session(s)",
                        icon = Icons.Default.CloudUpload,
                        onClick = { syncAll() },
                        enabled = !isSyncing && pendingCount > 0 && serverUrl.isNotBlank()
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
