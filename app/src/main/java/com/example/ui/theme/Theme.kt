package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val GsLightColorScheme =
  lightColorScheme(
    primary = GsBluePrimary,
    onPrimary = GsWhite,
    primaryContainer = GsBlueLight,
    onPrimaryContainer = GsBlueHover,
    secondary = GsSkyAccent,
    onSecondary = GsWhite,
    secondaryContainer = GsSkyContainer,
    onSecondaryContainer = Color(0xFF0369A1),
    tertiary = GsTealAccent,
    onTertiary = GsWhite,
    tertiaryContainer = GsTealContainer,
    background = GsCanvasBg,
    onBackground = GsTextPrimary,
    surface = GsWhite,
    onSurface = GsTextPrimary,
    surfaceVariant = GsSurfaceSubtle,
    onSurfaceVariant = GsTextSecondary,
    outline = GsBorderStrong,
    outlineVariant = GsBorder,
    error = GsDanger,
    onError = GsWhite,
    errorContainer = GsDangerContainer,
    onErrorContainer = Color(0xFF991B1B),
  )

private val GsDarkColorScheme =
  darkColorScheme(
    primary = GsDarkPrimary,
    onPrimary = Color(0xFF082F49),
    primaryContainer = GsBlueHover,
    onPrimaryContainer = GsBlueLight,
    secondary = GsSkyAccent,
    onSecondary = GsWhite,
    background = GsDarkBg,
    onBackground = Color(0xFFF8FAFC),
    surface = GsDarkSurface,
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = GsDarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFCBD5E1),
    outline = Color(0xFF475569),
    error = Color(0xFFF87171),
  )

@Composable
fun GsPdfEditorTheme(
  darkTheme: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) GsDarkColorScheme else GsLightColorScheme
  MaterialTheme(
    colorScheme = colorScheme,
    typography = AppTypography,
    content = content,
  )
}
