package app.pramaan.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PramaanColorScheme = lightColorScheme(
    primary = PramaanSaffron,
    onPrimary = PramaanSurfaceLowest,
    primaryContainer = PramaanSurfaceLow,
    onPrimaryContainer = PramaanSaffronDark,
    secondary = PramaanEmerald,
    onSecondary = PramaanSurfaceLowest,
    secondaryContainer = Color(0xFFDCFCE7),
    onSecondaryContainer = PramaanEmeraldDark,
    tertiary = PramaanCerulean,
    onTertiary = PramaanSurfaceLowest,
    background = PramaanSurface,
    onBackground = PramaanOnSurface,
    surface = PramaanSurfaceLowest,
    onSurface = PramaanOnSurface,
    surfaceVariant = PramaanSurfaceContainer,
    onSurfaceVariant = PramaanOnSurfaceVariant,
    outline = PramaanOutline,
    outlineVariant = PramaanOutlineVariant,
    error = PramaanError,
    onError = PramaanSurfaceLowest
)

@Composable
fun PramaanTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = PramaanColorScheme,
        typography = PramaanTypography,
        content = content
    )
}
