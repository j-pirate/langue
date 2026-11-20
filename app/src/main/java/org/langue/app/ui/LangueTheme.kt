package org.langue.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import org.langue.app.R

internal val Paper = Color(0xFFF8F3E9)
internal val Ink = Color(0xFF282522)
internal val Quiet = Color(0xFF70665C)
internal val Success = Color(0xFF28744E)
internal val Mistake = Color(0xFFA42C31)

@Composable
internal fun LangueTheme(content: @Composable () -> Unit) {
    val accent = colorResource(R.color.brand_accent)
    val scheme = lightColorScheme(
        primary = accent, onPrimary = Color.White, primaryContainer = accent.copy(alpha = .1f),
        onPrimaryContainer = Ink, background = Paper, onBackground = Ink,
        surface = Paper, onSurface = Ink, surfaceVariant = Color(0xFFEFE6D8),
        onSurfaceVariant = Quiet, outline = Color(0xFFB9AFA0), error = Mistake,
    )
    MaterialTheme(colorScheme = scheme, content = content)
}
