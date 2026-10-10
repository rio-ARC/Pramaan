package app.pramaan.ui.athlete

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pramaan.AppContainer
import app.pramaan.api.dto.AthleteSessionDetailDto
import app.pramaan.ui.components.BigMetric
import app.pramaan.ui.components.IntegrityChip
import app.pramaan.ui.components.StatusChip
import kotlinx.coroutines.launch

@Composable
fun SubmissionDetailScreen(
    container: AppContainer,
    sessionId: String,
    onBack: () -> Unit,
    onShowQr: (String) -> Unit,
    onViewCertificate: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val localSession by container.sessionDao.getByIdFlow(sessionId).collectAsState(initial = null)
    var serverDetail by remember { mutableStateOf<AthleteSessionDetailDto?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(sessionId) {
        val result = container.submissionRepository.pollSessionStatus(sessionId)
        if (result.isSuccess) {
            serverDetail = result.getOrThrow()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFF8FAFC)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
                .verticalScroll(scrollState)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = "Submission Audit",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (localSession != null) {
                val sess = localSession!!

                // Score card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = sess.test.replace('_', ' '),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF64748B)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${sess.best} ${sess.unit}",
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF2563EB)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatusChip(status = sess.status)
                            IntegrityChip(integrity = sess.serverIntegrity ?: sess.localIntegrity)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Reviewer info if reviewed
                if (!sess.reviewedByName.isNullOrBlank()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Coach Review",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2563EB)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Evaluated by: ${sess.reviewedByName}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF0F172A)
                            )
                            if (!sess.reviewReason.isNullOrBlank()) {
                                Text(
                                    text = "Note: ${sess.reviewReason}",
                                    fontSize = 13.sp,
                                    color = Color(0xFF475569)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // C1-C4 Checks list
                if (serverDetail != null && serverDetail!!.checks.isNotEmpty()) {
                    Text(
                        text = "Physics & Cryptography Checks",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (chk in serverDetail!!.checks) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = chk.id,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = Color(0xFF2563EB)
                                        )
                                        Text(
                                            text = "Value: ${chk.value} (${chk.limit})",
                                            fontSize = 12.sp,
                                            color = Color(0xFF64748B)
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (chk.pass) Color(0xFFDCFCE7) else Color(0xFFFEE2E2)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (chk.pass) Icons.Default.Check else Icons.Default.Close,
                                            contentDescription = null,
                                            tint = if (chk.pass) Color(0xFF16A34A) else Color(0xFFDC2626),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = Color(0xFFDC2626),
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Primary Action Button according to status
                when (sess.status) {
                    "DRAFT" -> {
                        Button(
                            onClick = {
                                scope.launch {
                                    isSubmitting = true
                                    errorMessage = null
                                    val res = container.submissionRepository.submitSession(sess.id)
                                    isSubmitting = false
                                    if (res.isSuccess) {
                                        onShowQr(sess.id)
                                    } else {
                                        errorMessage = res.exceptionOrNull()?.message ?: "Upload failed"
                                    }
                                }
                            },
                            enabled = !isSubmitting,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                        ) {
                            if (isSubmitting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Submit for Coach Verification", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    "AWAITING_REVIEW" -> {
                        Button(
                            onClick = { onShowQr(sess.id) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                        ) {
                            Icon(imageVector = Icons.Default.QrCode, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Show QR to Coach", fontWeight = FontWeight.Bold)
                        }
                    }
                    "APPROVED" -> {
                        Button(
                            onClick = {
                                val cert = sess.certificateJws ?: serverDetail?.certificateJws
                                if (cert != null) onViewCertificate(cert)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))
                        ) {
                            Icon(imageVector = Icons.Default.Verified, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("View Official Certificate", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
