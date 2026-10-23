package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val FlowRifeColorScheme = darkColorScheme(
    primary = VulkanCyan,
    onPrimary = ObsidianBg,
    primaryContainer = VulkanCyanDim,
    onPrimaryContainer = TextPrimary,
    secondary = OpticalViolet,
    onSecondary = TextPrimary,
    secondaryContainer = ObsidianCardElevated,
    onSecondaryContainer = OpticalVioletSoft,
    tertiary = EmeraldSync,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = ObsidianSurface,
    onSurface = TextPrimary,
    surfaceVariant = ObsidianCard,
    onSurfaceVariant = TextSecondary,
    error = CrimsonThermal,
    outline = BorderSubtle
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = FlowRifeColorScheme,
        typography = Typography,
        content = content
    )
}
