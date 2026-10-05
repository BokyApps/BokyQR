package rocks.myburgh.bokyqr.ui

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Copies [text] and marks the clip sensitive where the platform can act on that.
 *
 * On API 33 and later `ClipDescription.EXTRA_IS_SENSITIVE` tells the system keyboard and IME not
 * to learn from the copy: no suggestion history, no on-device personalisation, and a clipboard
 * history entry that does not surface the text. Every copy this app makes goes through here —
 * API keys on Settings, and the scanned payload, FIDO URI and inert text on the result sheet.
 *
 * This deliberately does not use Compose's `LocalClipboardManager`, which is deprecated and has no
 * way to set the sensitive flag; it goes to the platform `ClipboardManager` instead.
 *
 * [label] is the clip label shown by the system clipboard UI. It is a description of what was
 * copied, never the secret itself.
 */
internal fun copyToClipboard(context: Context, label: String, text: String) {
    val clip = ClipData.newPlainText(label, text)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras = PersistableBundle().apply {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
    }
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
}

/** Convenience wrapper: the app-wide context is already a safe clipboard target. */
@Composable
internal fun rememberSecureCopier(): (String, String) -> Unit {
    val context = LocalContext.current
    return { label, text -> copyToClipboard(context, label, text) }
}