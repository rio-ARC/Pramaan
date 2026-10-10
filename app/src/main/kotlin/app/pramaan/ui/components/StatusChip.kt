package app.pramaan.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun StatusChip(status: String, modifier: Modifier = Modifier) {
    val (bgColor, textColor, label) = when (status.uppercase()) {
        "APPROVED" -> Triple(Color(0xFFDCFCE7), Color(0xFF16A34A), "Approved")
        "AWAITING_REVIEW" -> Triple(Color(0xFFDBEAFE), Color(0xFF2563EB), "Waiting for coach")
        "REJECTED" -> Triple(Color(0xFFFEE2E2), Color(0xFFDC2626), "Rejected")
        "AUTO_REJECTED" -> Triple(Color(0xFFFEE2E2), Color(0xFFDC2626), "Auto-rejected")
        "DRAFT" -> Triple(Color(0xFFF1F5F9), Color(0xFF64748B), "Draft")
        else -> Triple(Color(0xFFF1F5F9), Color(0xFF64748B), status)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun IntegrityChip(integrity: String, modifier: Modifier = Modifier) {
    val (bgColor, textColor, label) = when (integrity.uppercase()) {
        "VERIFIED" -> Triple(Color(0xFFDCFCE7), Color(0xFF16A34A), "AI Verified ✓")
        "FLAGGED" -> Triple(Color(0xFFFEF3C7), Color(0xFFD97706), "Flagged ⚠")
        "TAMPERED" -> Triple(Color(0xFFFEE2E2), Color(0xFFDC2626), "Tampered ✕")
        "UNSEALED" -> Triple(Color(0xFFE2E8F0), Color(0xFF475569), "Unsealed")
        else -> Triple(Color(0xFFE2E8F0), Color(0xFF475569), integrity)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
