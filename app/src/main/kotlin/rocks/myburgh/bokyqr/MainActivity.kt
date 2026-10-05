package rocks.myburgh.bokyqr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import rocks.myburgh.bokyqr.ui.AppRoot
import rocks.myburgh.bokyqr.ui.BokyTheme

/** The one and only activity. Camera preview first, no fragments, no WebView, no splash delay. */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BokyTheme {
                AppRoot()
            }
        }
    }
}
