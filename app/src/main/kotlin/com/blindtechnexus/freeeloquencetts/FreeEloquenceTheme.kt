package com.blindtechnexus.freeeloquencetts

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape

private val LightColors = lightColorScheme(
	primary = Color(0xFF075E61),
	onPrimary = Color.White,
	primaryContainer = Color(0xFFA8EFF0),
	onPrimaryContainer = Color(0xFF002021),
	secondary = Color(0xFF4A635F),
	onSecondary = Color.White,
	secondaryContainer = Color(0xFFCCE8E3),
	onSecondaryContainer = Color(0xFF06201C),
	tertiary = Color(0xFF4A607C),
	background = Color(0xFFF5FAF9),
	onBackground = Color(0xFF171D1C),
	surface = Color(0xFFF8FAF9),
	onSurface = Color(0xFF171D1C),
	surfaceVariant = Color(0xFFDAE5E2),
	onSurfaceVariant = Color(0xFF3F4947),
	outline = Color(0xFF6F7977)
)

private val DarkColors = darkColorScheme(
	primary = Color(0xFF8CD3D4),
	onPrimary = Color(0xFF003738),
	primaryContainer = Color(0xFF004F51),
	onPrimaryContainer = Color(0xFFA8EFF0),
	secondary = Color(0xFFB0CCC7),
	onSecondary = Color(0xFF1B3531),
	secondaryContainer = Color(0xFF324B47),
	onSecondaryContainer = Color(0xFFCCE8E3),
	tertiary = Color(0xFFB2C8E8),
	background = Color(0xFF0E1514),
	onBackground = Color(0xFFDEE4E2),
	surface = Color(0xFF101817),
	onSurface = Color(0xFFDEE4E2),
	surfaceVariant = Color(0xFF3F4947),
	onSurfaceVariant = Color(0xFFBEC9C6),
	outline = Color(0xFF899391)
)

private val AppTypography = Typography(
	headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp),
	titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
	titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
	bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 25.sp),
	bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
	labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp)
)

private val AppShapes = Shapes(
	extraSmall = RoundedCornerShape(8.dp),
	small = RoundedCornerShape(12.dp),
	medium = RoundedCornerShape(18.dp),
	large = RoundedCornerShape(24.dp),
	extraLarge = RoundedCornerShape(30.dp)
)

/** A calm, high-contrast visual system shared by every screen. */
@Composable
fun FreeEloquenceTheme(content: @Composable () -> Unit) {
	MaterialTheme(
		colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
		typography = AppTypography,
		shapes = AppShapes,
		content = content
	)
}
