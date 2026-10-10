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
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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

    var serverUrl by remember {
        mutableStateOf(prefs.getString("server_url", "") ?: "")
    }

    var isScanningQr by remember { mutableStateOf(false) }
    var isTestingConnection by remember { mutableStateOf(false) }
    var connectionTestResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    var isSyncing by remember { mutableStateOf(false) }
    var syncStatusText by remember { mutableStateOf<String?>(null) }
    var syncErrorMessage by remember { mutableStateOf<String?>(null) }

    val sessions by container.sessionDao.getAllFlow()
        .collectAsState(initial = emptyList())

    val pendingCount = sessions.count { !it.synced }

    fun saveUrl(url: String) {
        val clean = url.trim()
        serverUrl = clean
        prefs.edit().putString("server_url", clean).apply()
    }

    fun testConnection() {
        if (serverUrl.isBlank()) {
            connectionTestResult = false to "Please scan the server QR code or enter a URL first."
            return
        }
        isTestingConnection = true
        connectionTestResult = null
        scope.launch {
            val res = withContext(Dispatchers.IO) {
                container.syncClient.testConnection(serverUrl)
            }
            isTestingConnection = false
            if (res.isSuccess) {
                connectionTestResult = true to (res.getOrNull() ?: "Connected successfully!")
            } else {
                val err = res.exceptionOrNull()?.localizedMessage ?: "Unknown connection error"
                connectionTestResult = false to "Cannot reach server at $serverUrl.\n\nTroubleshooting:\n1. Ensure laptop and phone are on the same Wi-Fi or Mobile Hotspot.\n2. Run allow-firewall.bat on your laptop to allow port 8080.\n\nError details: $err"
            }
        }
    }

    fun syncAll() {
        if (isSyncing) return
        if (serverUrl.isBlank()) {
            syncErrorMessage = "Please scan the pairing QR from your laptop or enter the server URL."
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
                        container.sessionDao.update(
                            sess.copy(
                                badge = res.serverBadge.name,
                                synced = true
                            )
                        )
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
                syncErrorMessage = "Sync failed: ${e.message}\nMake sure your laptop server is running and port 8080 is allowed through Windows Firewall."
            }
        }
    }

    if (isScanningQr) {
        // Fullscreen QR scanner to pair phone with laptop dashboard QR code
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Scan Server Pairing QR", style = PramaanTypography.titleMedium) },
                    navigationIcon = {
                        IconButton(onClick = { isScanningQr = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
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
                        if (url.startsWith("http://") || url.startsWith("https://")) {
                            saveUrl(url)
                            isScanningQr = false
                            testConnection()
                        }
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
                                    text = "Laptop or central authority",
                                    style = PramaanTypography.labelSmall,
                                    color = PramaanOutline
                                )
                            }
                        }

                        // Button to scan server QR from laptop screen
                        Button(
                            onClick = { isScanningQr = true },
                            colors = ButtonDefaults.buttonColors(containerColor = PramaanSaffron),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Scan QR", style = PramaanTypography.labelSmall)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = { saveUrl(it) },
                        label = { Text("Server Endpoint URL (e.g. http://192.168.1.5:8080)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(
                            onClick = { testConnection() },
                            enabled = !isTestingConnection && serverUrl.isNotBlank(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Sensors, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isTestingConnection) "Testing..." else "Test Connection",
                                style = PramaanTypography.labelSmall
                            )
                        }
                    }

                    connectionTestResult?.let { (success, message) ->
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (success) Color(0xFFDCFCE7) else Color(0xFFFEE2E2))
                                .padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(
                                    imageVector = if (success) Icons.Default.CheckCircle else Icons.Default.Error,
                                    contentDescription = null,
                                    tint = if (success) PramaanEmerald else PramaanError,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = message,
                                    style = PramaanTypography.bodySmall,
                                    color = if (success) Color(0xFF14532D) else Color(0xFF7F1D1D)
                                )
                            }
                        }
                    }

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
