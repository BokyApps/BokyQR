package rocks.myburgh.bokyqr.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import rocks.myburgh.bokyqr.R

/** Unwraps the activity a composable is hosted in, however many wrappers are in the way. */
internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Keeps the window out of screenshots, screen recordings and the recent-apps thumbnail while this
 * composable is on screen, and only then.
 *
 * Applied to the two screens that put something on the display worth keeping off it: Settings,
 * which shows the user's own API keys, and the result sheet, which shows the scanned payload and
 * any verdict a provider returned. The camera preview deliberately does not set it — a QR code is
 * not secret, and the flag also blurs the preview in the task switcher, which is a worse trade for
 * a screen the user switches away from constantly.
 *
 * The flag is cleared on dispose so returning to the camera screen does not leave it stuck on.
 *
 * Whether the flag is applied at all is the `screenshot_protection` boolean: true in `src/main`,
 * overridden to false in `src/debug` so a debug build can capture these screens for the store
 * listings. No shipped build loses the protection — debug APKs are never distributed, see the
 * "Certificates: what is trusted" section of README.md.
 */
@Composable
internal fun SecureWindow() {
    val context = LocalContext.current
    DisposableEffect(context) {
        val window = context.findActivity()?.window
        val secure = context.resources.getBoolean(R.bool.screenshot_protection)
        if (secure) window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { if (secure) window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}