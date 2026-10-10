package app.pramaan.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import app.pramaan.AppContainer
import app.pramaan.data.AthleteEntity
import app.pramaan.ui.components.PramaanCard
import app.pramaan.ui.components.PrimaryButton
import app.pramaan.ui.theme.PramaanError
import app.pramaan.ui.theme.PramaanOnSurface
import app.pramaan.ui.theme.PramaanOnSurfaceVariant
import app.pramaan.ui.theme.PramaanSaffron
import app.pramaan.ui.theme.PramaanSurface
import app.pramaan.ui.theme.PramaanTypography
import kotlinx.coroutines.launch
import java.util.UUID

class AthleteEditViewModel(private val container: AppContainer) : ViewModel() {
    suspend fun getAthlete(id: String): AthleteEntity? {
        return container.athleteDao.getById(id)
    }

    suspend fun saveAthlete(athlete: AthleteEntity) {
        container.athleteDao.insert(athlete)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AthleteEditScreen(
    viewModel: AthleteEditViewModel,
    athleteId: String?,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var ageText by remember { mutableStateOf("16") }
    var sex by remember { mutableStateOf("F") }
    var heightText by remember { mutableStateOf("165.0") }
    var weightText by remember { mutableStateOf("55.0") }
    var guardianConsent by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(athleteId) {
        if (!athleteId.isNullOrEmpty()) {
            val existing = viewModel.getAthlete(athleteId)
            if (existing != null) {
                name = existing.name
                ageText = existing.age.toString()
                sex = existing.sex
                heightText = existing.heightCm.toString()
                weightText = existing.weightKg.toString()
                guardianConsent = existing.guardianConsent
            }
        }
    }

    val age = ageText.toIntOrNull() ?: 16
    val isMinor = age < 18

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (athleteId.isNullOrEmpty()) "Add Athlete" else "Edit Athlete", style = PramaanTypography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PramaanSurface)
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
            PramaanCard {
                Text(
                    text = "ATHLETE PROFILE",
                    style = PramaanTypography.labelSmall,
                    color = PramaanOnSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Full Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = ageText,
                        onValueChange = { ageText = it },
                        label = { Text("Age (yrs)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = sex,
                        onValueChange = { sex = it.take(1).uppercase() },
                        label = { Text("Sex (M/F/O)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = heightText,
                        onValueChange = { heightText = it },
                        label = { Text("Height (cm) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = weightText,
                        onValueChange = { weightText = it },
                        label = { Text("Weight (kg)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (isMinor) {
                PramaanCard {
                    Text(
                        text = "LEGAL & COMPLIANCE",
                        style = PramaanTypography.labelSmall,
                        color = PramaanOnSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = guardianConsent,
                            onCheckedChange = { guardianConsent = it },
                            colors = CheckboxDefaults.colors(checkedColor = PramaanSaffron)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Guardian consent obtained (Mandatory for athletes under 18 years)",
                            style = PramaanTypography.bodyMedium,
                            color = PramaanOnSurface
                        )
                    }
                }
            }

            if (errorMessage != null) {
                Text(
                    text = errorMessage ?: "",
                    style = PramaanTypography.bodySmall,
                    color = PramaanError
                )
            }

            PrimaryButton(
                text = "Save Athlete",
                onClick = {
                    val parsedHeight = heightText.toDoubleOrNull()
                    val parsedWeight = weightText.toDoubleOrNull() ?: 50.0

                    if (name.isBlank()) {
                        errorMessage = "Please enter athlete's full name"
                        return@PrimaryButton
                    }
                    if (parsedHeight == null || parsedHeight <= 50.0) {
                        errorMessage = "Valid height (cm) is required for biomechanical scaling"
                        return@PrimaryButton
                    }
                    if (isMinor && !guardianConsent) {
                        errorMessage = "Guardian consent checkbox is required for athletes under 18"
                        return@PrimaryButton
                    }

                    val initials = name.trim().split("\\s+".toRegex())
                        .mapNotNull { it.firstOrNull()?.uppercase() }
                        .joinToString(".") { it }
                        .let { if (it.isNotEmpty()) "$it." else "A." }

                    val id = athleteId ?: UUID.randomUUID().toString()
                    val athlete = AthleteEntity(
                        id = id,
                        name = name.trim(),
                        initials = initials,
                        age = age,
                        sex = sex,
                        heightCm = parsedHeight,
                        weightKg = parsedWeight,
                        guardianConsent = guardianConsent
                    )

                    scope.launch {
                        viewModel.saveAthlete(athlete)
                        onBack()
                    }
                }
            )
        }
    }
}
