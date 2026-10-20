package com.marea.ai.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.marea.ai.data.Accent

val Burgundy = Color(0xFFB3294E)
val Violet = Color(0xFF8B5CF6)
val BgBlack = Color(0xFF0B0B0D)
val Charcoal = Color(0xFF17171B)
val Charcoal2 = Color(0xFF222228)

fun accentColor(a: Accent): Color = if (a == Accent.BURGUNDY) Burgundy else Violet

@Composable
fun MareaTheme(accent: Accent, content: @Composable () -> Unit) {
    val c = accentColor(accent)
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = c,
            onPrimary = Color.White,
            secondary = c,
            background = BgBlack,
            onBackground = Color(0xFFECECF1),
            surface = Charcoal,
            onSurface = Color(0xFFECECF1),
            surfaceVariant = Charcoal2,
            onSurfaceVariant = Color(0xFFB8B8C2),
            error = Color(0xFFFF6B6B)
        ),
        content = content
    )
}
