package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = ForestPrimary,
    onPrimary = OnForestPrimary,
    primaryContainer = SagePrimaryContainer,
    onPrimaryContainer = OnSagePrimaryContainer,
    secondary = TerracottaSecondary,
    onSecondary = OnTerracottaSecondary,
    secondaryContainer = PeachSecondaryContainer,
    onSecondaryContainer = OnPeachSecondaryContainer,
    tertiary = EmeraldTertiary,
    onTertiary = OnEmeraldTertiary,
    tertiaryContainer = MintTertiaryContainer,
    onTertiaryContainer = OnMintTertiaryContainer,
    background = AlabasterBackground,
    onBackground = InkOnBackground,
    surface = PureSurface,
    onSurface = InkOnBackground,
    surfaceVariant = WarmSurfaceVariant,
    onSurfaceVariant = SlateOnSurfaceVariant,
    outline = SandOutline,
    error = AlertCrimson,
    onError = OnForestPrimary,
    errorContainer = AlertCrimsonContainer,
    onErrorContainer = OnAlertCrimsonContainer
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkMintPrimary,
    onPrimary = OnDarkMintPrimary,
    primaryContainer = DarkForestContainer,
    onPrimaryContainer = OnDarkForestContainer,
    secondary = DarkAmberSecondary,
    onSecondary = OnDarkAmberSecondary,
    secondaryContainer = DarkTerracottaContainer,
    onSecondaryContainer = OnDarkTerracottaContainer,
    tertiary = DarkSageTertiary,
    onTertiary = OnDarkSageTertiary,
    tertiaryContainer = DarkSageContainer,
    onTertiaryContainer = OnDarkSageContainer,
    background = ObsidianBackground,
    onBackground = IvoryOnBackground,
    surface = CharcoalSurface,
    onSurface = IvoryOnBackground,
    surfaceVariant = DeepMossSurfaceVariant,
    onSurfaceVariant = MistOnSurfaceVariant,
    outline = MossOutline,
    error = DarkCrimsonError,
    onError = OnDarkCrimsonContainer,
    errorContainer = DarkCrimsonContainer,
    onErrorContainer = OnDarkCrimsonContainer
)

val StockLensShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun StockLensTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = StockLensTypography,
        shapes = StockLensShapes,
        content = content
    )
}
