package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class MehdiExtendedColors(
    val navyBrand: Color,
    val purpleBrand: Color,
    val turquoiseAccent: Color,
    val turquoiseContainer: Color,
    val cardSurface: Color,
    val cardElevated: Color,
    val subtleBorder: Color,
    val secondaryText: Color,
    val focusRing: Color,
    val liveBadge: Color,
    val warning: Color,
    val error: Color,
    val isDark: Boolean
)

val LocalMehdiColors = staticCompositionLocalOf {
    MehdiExtendedColors(
        navyBrand = DeepNavy,
        purpleBrand = ModernPurple,
        turquoiseAccent = TurquoiseAccent,
        turquoiseContainer = TurquoiseContainerLight,
        cardSurface = PureWhite,
        cardElevated = LightSurfaceVariant,
        subtleBorder = LightOutline,
        secondaryText = SecondaryTextLight,
        focusRing = ModernPurple,
        liveBadge = StatusLiveRed,
        warning = StatusWarning,
        error = StatusError,
        isDark = false
    )
}

private val LightColorScheme = lightColorScheme(
    primary = ModernPurple,
    onPrimary = PureWhite,
    primaryContainer = ModernPurpleContainerLight,
    onPrimaryContainer = DeepNavy,
    secondary = TurquoiseAccent,
    onSecondary = PureWhite,
    secondaryContainer = TurquoiseContainerLight,
    onSecondaryContainer = DeepNavy,
    tertiary = DeepNavy,
    onTertiary = PureWhite,
    background = SoftBackground,
    onBackground = DeepNavy,
    surface = PureWhite,
    onSurface = DeepNavy,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = SecondaryTextLight,
    outline = LightOutline,
    outlineVariant = LightOutline,
    error = StatusError,
    onError = PureWhite
)

private val DarkColorScheme = darkColorScheme(
    primary = ModernPurple,
    onPrimary = PureWhite,
    primaryContainer = ModernPurpleContainerDark,
    onPrimaryContainer = OnDarkPrimaryText,
    secondary = TurquoiseAccent,
    onSecondary = DeepNavyDarker,
    secondaryContainer = TurquoiseContainerDark,
    onSecondaryContainer = OnDarkPrimaryText,
    tertiary = ModernPurpleLight,
    onTertiary = DeepNavyDarker,
    background = MidnightCanvas,
    onBackground = OnDarkPrimaryText,
    surface = MidnightCard,
    onSurface = OnDarkPrimaryText,
    surfaceVariant = MidnightCardElevated,
    onSurfaceVariant = SecondaryTextDark,
    outline = MidnightOutline,
    outlineVariant = MidnightOutline,
    error = StatusError,
    onError = PureWhite
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun MehdiTvTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extendedColors = if (darkTheme) {
        MehdiExtendedColors(
            navyBrand = DeepNavy,
            purpleBrand = ModernPurple,
            turquoiseAccent = TurquoiseAccent,
            turquoiseContainer = TurquoiseContainerDark,
            cardSurface = MidnightCard,
            cardElevated = MidnightCardElevated,
            subtleBorder = MidnightOutline,
            secondaryText = SecondaryTextDark,
            focusRing = TurquoiseAccent,
            liveBadge = StatusLiveRed,
            warning = StatusWarning,
            error = StatusError,
            isDark = true
        )
    } else {
        MehdiExtendedColors(
            navyBrand = DeepNavy,
            purpleBrand = ModernPurple,
            turquoiseAccent = TurquoiseAccent,
            turquoiseContainer = TurquoiseContainerLight,
            cardSurface = PureWhite,
            cardElevated = LightSurfaceVariant,
            subtleBorder = LightOutline,
            secondaryText = SecondaryTextLight,
            focusRing = ModernPurple,
            liveBadge = StatusLiveRed,
            warning = StatusWarning,
            error = StatusError,
            isDark = false
        )
    }

    CompositionLocalProvider(LocalMehdiColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}
