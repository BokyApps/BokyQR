package rocks.myburgh.bokyqr.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BokyColors = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    onPrimary = Color(0xFF0A0A0A),
    secondary = Color(0xFF9AA0A6),
    background = Color.Black,
    onBackground = Color(0xFFE8EAED),
    surface = Color(0xFF121212),
    onSurface = Color(0xFFE8EAED),
    error = Color(0xFFFF8A80),
)

@Composable
fun BokyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = BokyColors, content = content)
}
