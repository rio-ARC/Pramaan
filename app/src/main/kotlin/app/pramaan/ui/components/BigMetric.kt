package app.pramaan.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.pramaan.ui.theme.PramaanOnSurface
import app.pramaan.ui.theme.PramaanOnSurfaceVariant
import app.pramaan.ui.theme.PramaanTypography

@Composable
fun BigMetric(
    value: String,
    unit: String,
    label: String? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        if (label != null) {
            Text(
                text = label.uppercase(),
                style = PramaanTypography.labelMedium,
                color = PramaanOnSurfaceVariant
            )
        }
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.Start
        ) {
            Text(
                text = value,
                style = PramaanTypography.displayLarge,
                color = PramaanOnSurface
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = unit,
                style = PramaanTypography.headlineMedium,
                color = PramaanOnSurfaceVariant
            )
        }
    }
}
