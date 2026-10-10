package app.pramaan.ui.onboarding

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pramaan.AppContainer
import app.pramaan.ui.components.QrScanner
import kotlinx.coroutines.launch

@Composable
fun ConnectScreen(
    container: AppContainer,
    onConnected: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val savedUrl by container.authStore.serverUrlFlow.collectAsState(initial = "http://10.0.2.2:8080")

    var urlInput by remember { mutableStateOf("") }
    var isTesting by remember { mutableStateOf(false) }
    var isHealthy by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showScanner by remember { mutableStateOf(false) }

    LaunchedEffect(savedUrl) {
        if (urlInput.isEmpty()) {
            urlInput = savedUrl
            // Auto-test on first load
            isTesting = true
            val res = container.authRepository.testConnection(savedUrl)
            isHealthy = res.isSuccess
            isTesting = false
        }
    }

    if (showScanner) {
        Box(modifier = Modifier.fillMaxSize()) {
            QrScanner(
                modifier = Modifier.fillMaxSize(),
                onQrScanned = { scanned ->
                    if (scanned.startsWith("http://") || scanned.startsWith("https://")) {
                        showScanner = false
                        urlInput = scanned
                        scope.launch {
                            isTesting = true
                            val res = container.authRepository.testConnection(scanned)
                            isHealthy = res.isSuccess
                            errorMessage = if (res.isFailure) res.exceptionOrNull()?.message else null
                            isTesting = false
                        }
                    }
                }
            )
            Button(
                onClick = { showScanner = false },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(24.dp)
            ) {
                Text("Cancel")
            }
        }
        return
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFF8FAFC)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFFF7ED)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Wifi,
                    contentDescription = null,
                    tint = Color(0xFFEA580C),
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Connect to PRAMAAN",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Connect to the laptop verification authority on the same Wi-Fi or Hotspot.",
                fontSize = 14.sp,
                color = Color(0xFF64748B),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = urlInput,
                onValueChange = {
                    urlInput = it
                    isHealthy = false
                    errorMessage = null
                },
                label = { Text("Server Authority URL") },
                placeholder = { Text("http://192.168.43.10:8080") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        scope.launch {
                            isTesting = true
                            errorMessage = null
                            val res = container.authRepository.testConnection(urlInput)
                            isHealthy = res.isSuccess
                            if (res.isFailure) {
                                errorMessage = res.exceptionOrNull()?.message ?: "Connection failed"
                            }
                            isTesting = false
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A))
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Test Connection")
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                OutlinedButton(
                    onClick = { showScanner = true },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = "Scan QR",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Scan QR")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isHealthy) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFDCFCE7))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color(0xFF16A34A),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Connected to PRAMAAN Verification Server!",
                        color = Color(0xFF166534),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else if (errorMessage != null) {
                Text(
                    text = "Connection Error: $errorMessage",
                    color = Color(0xFFDC2626),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onConnected,
                enabled = isHealthy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA580C))
            ) {
                Text("Continue", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
