package rocks.myburgh.bokyqr.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import rocks.myburgh.bokyqr.scan.ScanViewModel

/** One screen at a time: the camera preview, or Settings. No fragments, no navigation graph. */
@Composable
fun AppRoot() {
    val viewModel: ScanViewModel = viewModel()
    var showSettings by remember { mutableStateOf(false) }

    Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
        if (showSettings) {
            SettingsScreen(viewModel = viewModel, onBack = { showSettings = false })
        } else {
            CameraScreen(viewModel = viewModel, onOpenSettings = { showSettings = true })
        }
    }
}
