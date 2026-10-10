package app.pramaan.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.pramaan.core.model.CheckResult
import app.pramaan.ui.theme.PramaanEmerald
import app.pramaan.ui.theme.PramaanError
import app.pramaan.ui.theme.PramaanOnSurface
import app.pramaan.ui.theme.PramaanOnSurfaceVariant
import app.pramaan.ui.theme.PramaanOutline
import app.pramaan.ui.theme.PramaanTypography

@Composable
fun ChecksList(
    checks: List<CheckResult>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        checks.forEach { check ->
            CheckItemRow(check = check)
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
fun CheckItemRow(
    check: CheckResult,
    modifier: Modifier = Modifier
) {
    val checkName = when (check.id) {
        "C1" -> "Tamper & Cryptographic Seal"
        "C2" -> "Real-time Gravity Verification"
        "C3" -> "Dual-Method Jump Agreement"
        "C4" -> "Camera Stability (Tripod Gyro)"
        "C5" -> "Witness Independent Co-Signature"
        else -> check.id
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF8FAFC))
            .border(1.dp, PramaanOutline, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        val iconBg = if (check.pass) Color(0xFFDCFCE7) else Color(0xFFFEE2E2)
        val iconTint = if (check.pass) PramaanEmerald else PramaanError

        Row(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(iconBg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (check.pass) Icons.Default.Check else Icons.Default.Close,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier
                    .size(16.dp)
                    .padding(2.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${check.id} • $checkName",
                    style = PramaanTypography.titleMedium,
                    color = PramaanOnSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = check.value,
                    style = PramaanTypography.labelMedium,
                    color = if (check.pass) PramaanEmerald else PramaanError
                )
            }

            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Target limit: ${check.limit}",
                style = PramaanTypography.bodySmall,
                color = PramaanOnSurfaceVariant
            )

            if (!check.reason.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = check.reason ?: "",
                    style = PramaanTypography.bodySmall,
                    color = if (check.pass) PramaanOnSurfaceVariant else PramaanError
                )
            }
        }
    }
}
