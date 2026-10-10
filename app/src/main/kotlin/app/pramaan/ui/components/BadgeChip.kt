package app.pramaan.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.pramaan.core.model.Badge
import app.pramaan.ui.theme.BadgeColors
import app.pramaan.ui.theme.PramaanTypography

@Composable
fun BadgeChip(
    badge: Badge,
    modifier: Modifier = Modifier
) {
    val container = BadgeColors.containerColor(badge)
    val content = BadgeColors.contentColor(badge)
    val border = BadgeColors.borderColor(badge)

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(9999.dp))
            .background(container)
            .border(1.dp, border, RoundedCornerShape(9999.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (badge) {
            Badge.WITNESSED, Badge.VERIFIED -> {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(14.dp)
                )
            }
            Badge.FLAGGED -> {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(14.dp)
                )
            }
            Badge.TAMPERED -> {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(14.dp)
                )
            }
            Badge.UNSEALED -> {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(content)
                )
            }
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = badge.name,
            style = PramaanTypography.labelSmall,
            color = content
        )
    }
}
