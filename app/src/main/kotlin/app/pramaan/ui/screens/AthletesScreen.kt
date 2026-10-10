package app.pramaan.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pramaan.AppContainer
import app.pramaan.data.AthleteEntity
import app.pramaan.ui.components.InitialsAvatar
import app.pramaan.ui.components.PramaanCard
import app.pramaan.ui.theme.PramaanOnSurface
import app.pramaan.ui.theme.PramaanOnSurfaceVariant
import app.pramaan.ui.theme.PramaanSaffron
import app.pramaan.ui.theme.PramaanSurface
import app.pramaan.ui.theme.PramaanTypography
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class AthletesViewModel(private val container: AppContainer) : ViewModel() {
    val athletes: StateFlow<List<AthleteEntity>> = container.athleteDao.getAllFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AthletesScreen(
    viewModel: AthletesViewModel,
    onBack: () -> Unit,
    onAddAthlete: () -> Unit,
    onEditAthlete: (String) -> Unit,
    onSelectAthleteForTest: (String) -> Unit
) {
    val athletes by viewModel.athletes.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Athlete Registry", style = PramaanTypography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PramaanSurface)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddAthlete,
                containerColor = PramaanSaffron,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Athlete")
            }
        },
        containerColor = PramaanSurface
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${athletes.size} Registered Athletes",
                    style = PramaanTypography.bodySmall,
                    color = PramaanOnSurfaceVariant
                )
            }

            if (athletes.isEmpty()) {
                item {
                    Text(
                        text = "No athletes registered. Tap + to add an athlete before starting tests.",
                        style = PramaanTypography.bodyMedium,
                        color = PramaanOnSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
            } else {
                items(athletes) { athlete ->
                    PramaanCard(
                        modifier = Modifier.clickable { onSelectAthleteForTest(athlete.id) }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            InitialsAvatar(initials = athlete.initials)
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = athlete.name,
                                    style = PramaanTypography.titleMedium,
                                    color = PramaanOnSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${athlete.age} yrs • ${athlete.sex} • ${athlete.heightCm} cm • ${athlete.weightKg} kg",
                                    style = PramaanTypography.bodySmall,
                                    color = PramaanOnSurfaceVariant
                                )
                            }
                            Text(
                                text = "Edit",
                                style = PramaanTypography.labelMedium,
                                color = PramaanSaffron,
                                modifier = Modifier
                                    .clickable { onEditAthlete(athlete.id) }
                                    .padding(8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
