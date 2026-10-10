package app.pramaan.ui.onboarding

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pramaan.AppContainer
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.Period

@Composable
fun AthleteRegisterScreen(
    container: AppContainer,
    onRegistered: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var name by remember { mutableStateOf("") }
    var dob by remember { mutableStateOf("2010-03-14") }
    var sex by remember { mutableStateOf("F") }
    var heightCmStr by remember { mutableStateOf("152") }
    var weightKgStr by remember { mutableStateOf("42") }
    var district by remember { mutableStateOf("Ranchi") }
    var phone by remember { mutableStateOf("") }
    var guardianConsent by remember { mutableStateOf(false) }

    var selfieBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var selfieBytes by remember { mutableStateOf<ByteArray?>(null) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bmp ->
        if (bmp != null) {
            val scaled = Bitmap.createScaledBitmap(bmp, 256, 256, true)
            selfieBitmap = scaled
            val stream = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 80, stream)
            selfieBytes = stream.toByteArray()
        }
    }

    val calculatedAge by remember(dob) {
        derivedStateOf {
            try {
                val parsed = LocalDate.parse(dob.trim())
                Period.between(parsed, LocalDate.now()).years
            } catch (_: Exception) {
                null
            }
        }
    }

    val isMinor = (calculatedAge ?: 18) < 18

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFF8FAFC)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Athlete Registration",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            )

            Text(
                text = "Your phone key will be bound to your athletic profile.",
                fontSize = 13.sp,
                color = Color(0xFF64748B)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Selfie preview
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE2E8F0))
                    .border(2.dp, Color(0xFF2563EB), CircleShape)
                    .clickable { takePictureLauncher.launch(null) },
                contentAlignment = Alignment.Center
            ) {
                if (selfieBitmap != null) {
                    Image(
                        bitmap = selfieBitmap!!.asImageBitmap(),
                        contentDescription = "Selfie",
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(28.dp)
                        )
                        Text("Add Photo", fontSize = 10.sp, color = Color(0xFF64748B))
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Full Name *") },
                placeholder = { Text("e.g. Manpreet Kaur") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = dob,
                    onValueChange = { dob = it },
                    label = { Text("DOB (YYYY-MM-DD) *") },
                    placeholder = { Text("2010-03-14") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )

                if (calculatedAge != null) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .height(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFEFF6FF))
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$calculatedAge yrs",
                            color = Color(0xFF2563EB),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Sex Selection Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Sex:",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF475569)
                )
                Spacer(modifier = Modifier.width(12.dp))
                listOf("F" to "Female", "M" to "Male", "X" to "Other").forEach { (code, label) ->
                    FilterChip(
                        selected = sex == code,
                        onClick = { sex = code },
                        label = { Text(label) },
                        modifier = Modifier.padding(end = 6.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF2563EB),
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = heightCmStr,
                    onValueChange = { heightCmStr = it },
                    label = { Text("Height (cm) *") },
                    placeholder = { Text("152") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                OutlinedTextField(
                    value = weightKgStr,
                    onValueChange = { weightKgStr = it },
                    label = { Text("Weight (kg)") },
                    placeholder = { Text("42") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = district,
                    onValueChange = { district = it },
                    label = { Text("District") },
                    placeholder = { Text("Ranchi") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone (Optional)") },
                    placeholder = { Text("9876543210") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            // Guardian consent (shown for minors)
            if (isMinor) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFFEF3C7))
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = guardianConsent,
                        onCheckedChange = { guardianConsent = it },
                        colors = CheckboxDefaults.colors(checkedColor = Color(0xFFD97706))
                    )
                    Text(
                        text = "I have parent/guardian consent to participate in athletic talent evaluations.",
                        fontSize = 12.sp,
                        color = Color(0xFF92400E),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = errorMessage!!,
                    color = Color(0xFFDC2626),
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    val hCm = heightCmStr.toDoubleOrNull()
                    val wKg = weightKgStr.toDoubleOrNull()

                    if (name.isBlank()) {
                        errorMessage = "Please enter your name"
                        return@Button
                    }
                    if (hCm == null || hCm <= 0) {
                        errorMessage = "Please enter a valid height in cm"
                        return@Button
                    }
                    if (isMinor && !guardianConsent) {
                        errorMessage = "Guardian consent is required for athletes under 18"
                        return@Button
                    }

                    scope.launch {
                        isLoading = true
                        errorMessage = null
                        val result = container.authRepository.registerAthlete(
                            name = name,
                            dob = dob,
                            sex = sex,
                            heightCm = hCm,
                            weightKg = wKg,
                            district = district.ifBlank { null },
                            phone = phone.ifBlank { null },
                            guardianConsent = guardianConsent,
                            photoBytes = selfieBytes
                        )
                        isLoading = false
                        if (result.isSuccess) {
                            onRegistered()
                        } else {
                            errorMessage = result.exceptionOrNull()?.message ?: "Registration failed"
                        }
                    }
                },
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Register & Bind Device Key", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
