package app.pramaan.ui.theme

import androidx.compose.ui.graphics.Color
import app.pramaan.core.model.Badge

// Pramaan Talent System - Brand & Role Colors
val PramaanSaffron = Color(0xFFEA580C)
val PramaanSaffronDark = Color(0xFFA33900)
val PramaanEmerald = Color(0xFF16A34A)
val PramaanEmeraldDark = Color(0xFF006E2D)
val PramaanCerulean = Color(0xFF0284C7)
val PramaanCeruleanDark = Color(0xFF006194)
val PramaanNavy = Color(0xFF0F172A)

// Neutral Foundation (High-glare daylight contrast)
val PramaanSurface = Color(0xFFFAF8FF)
val PramaanSurfaceLowest = Color(0xFFFFFFFF)
val PramaanSurfaceLow = Color(0xFFF2F3FF)
val PramaanSurfaceContainer = Color(0xFFEAEDFF)
val PramaanSurfaceHigh = Color(0xFFE2E7FF)
val PramaanOnSurface = Color(0xFF131B2E)
val PramaanOnSurfaceVariant = Color(0xFF5A4138)
val PramaanOutline = Color(0xFFE2E8F0)
val PramaanOutlineVariant = Color(0xFFCBD5E1)
val PramaanError = Color(0xFFDC2626)

// Cryptographic Badge Colors (PRD 5.1)
object BadgeColors {
    fun containerColor(badge: Badge): Color = when (badge) {
        Badge.WITNESSED -> Color(0xFFF0FDF4)
        Badge.VERIFIED -> Color(0xFFDCFCE7)
        Badge.UNSEALED -> Color(0xFFF1F5F9)
        Badge.FLAGGED -> Color(0xFFFEF3C7)
        Badge.TAMPERED -> Color(0xFFFEE2E2)
    }

    fun contentColor(badge: Badge): Color = when (badge) {
        Badge.WITNESSED -> Color(0xFF14532D)
        Badge.VERIFIED -> Color(0xFF16A34A)
        Badge.UNSEALED -> Color(0xFF64748B)
        Badge.FLAGGED -> Color(0xFFD97706)
        Badge.TAMPERED -> Color(0xFFDC2626)
    }

    fun borderColor(badge: Badge): Color = when (badge) {
        Badge.WITNESSED -> Color(0xFF86EFAC)
        Badge.VERIFIED -> Color(0xFFBBF7D0)
        Badge.UNSEALED -> Color(0xFFCBD5E1)
        Badge.FLAGGED -> Color(0xFFFDE68A)
        Badge.TAMPERED -> Color(0xFFFECACA)
    }
}
