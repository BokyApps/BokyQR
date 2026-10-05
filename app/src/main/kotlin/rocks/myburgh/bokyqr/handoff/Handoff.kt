package rocks.myburgh.bokyqr.handoff

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import rocks.myburgh.bokyqr.core.PayloadPolicy
import rocks.myburgh.bokyqr.core.ScanDecision

/**
 * The only two intents the app ever builds, and both are built here and nowhere else.
 *
 *  * This object is the launch chokepoint and it enforces [PayloadPolicy] itself. It does not
 *    trust its caller: every string handed to it is classified again here, and a string that is
 *    not the expected [ScanDecision] variant returns false without building an intent at all.
 *  * Only the value carried by the decision is opened ([ScanDecision.OpenableUrl.url] and
 *    [ScanDecision.Passkey.fidoUri]), never the raw argument, so nothing that survives policy
 *    but was not normalised by policy can reach the system.
 *  * The scanned string is never parsed into an intent: no `Intent.parseUri`, ever.
 *  * No component, no selector, no `extras` copied from the code, no `setPackage`.
 *  * An http/https URL becomes a plain `ACTION_VIEW` + `CATEGORY_BROWSABLE` implicit intent, which
 *    the device resolves to the user's default browser: not a WebView, not Custom Tabs.
 *  * A passkey QR becomes a plain `ACTION_VIEW` of the `FIDO:/...` URI and nothing else. The app
 *    does not implement CTAP, BLE or the hybrid tunnel; it hands the URI to whatever already
 *    handles the scheme (Play Services or microG), or tells the user what to do instead.
 */
object Handoff {

    /**
     * Opens an http/https URL in the default browser.
     *
     * Returns false, without starting anything, when [PayloadPolicy.classify] does not return
     * [ScanDecision.OpenableUrl], and returns false when no browser can handle the intent.
     */
    fun openInBrowser(context: Context, url: String): Boolean {
        val decision = PayloadPolicy.classify(url) as? ScanDecision.OpenableUrl ?: return false
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(decision.url))
            .addCategory(Intent.CATEGORY_BROWSABLE)
        return launch(context, intent)
    }

    /**
     * Hands a `FIDO:/...` URI to the system.
     *
     * Returns false, without starting anything, when [PayloadPolicy.classify] does not return
     * [ScanDecision.Passkey], and returns false when nothing handles the scheme.
     */
    fun openPasskey(context: Context, fidoUri: String): Boolean {
        val decision = PayloadPolicy.classify(fidoUri) as? ScanDecision.Passkey ?: return false
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(decision.fidoUri))
        return launch(context, intent)
    }

    private fun launch(context: Context, intent: Intent): Boolean {
        if (context !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }
}
