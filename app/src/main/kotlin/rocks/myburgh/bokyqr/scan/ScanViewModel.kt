package rocks.myburgh.bokyqr.scan

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import rocks.myburgh.bokyqr.core.PayloadPolicy
import rocks.myburgh.bokyqr.core.ScanDecision
import rocks.myburgh.bokyqr.data.Secrets
import rocks.myburgh.bokyqr.data.Settings
import rocks.myburgh.bokyqr.net.ProviderException
import rocks.myburgh.bokyqr.net.ProviderOutcome
import rocks.myburgh.bokyqr.net.UrlhausClient
import rocks.myburgh.bokyqr.net.UrlscanClient
import rocks.myburgh.bokyqr.net.VirusTotalClient

/** State of one reputation provider the user may have tapped. */
sealed interface ProviderState {
    data object Idle : ProviderState
    data object Loading : ProviderState
    data class Done(val outcome: ProviderOutcome) : ProviderState
    data class Failed(val message: String) : ProviderState
}

/**
 * Holds the current `:core` decision and the state of the three optional reputation providers.
 *
 * Classification is always `:core.PayloadPolicy`; this class never re-implements or loosens any
 * part of the policy. Providers only ever run when the user taps them, and each one is gated by a
 * one-time disclosure that the full URL will leave the phone.
 */
class ScanViewModel(application: Application) : AndroidViewModel(application) {

    // Kept as our own val: the constructor parameter is not visible from member functions, and
    // AndroidViewModel's own `application` property would be shadowed by it.
    private val app: Application = application
    private val settings = Settings(app)

    private val secrets: Secrets
        get() = Secrets.get(app)

    var decision: ScanDecision? by mutableStateOf(null)
        private set

    /** True while the result sheet is up, which is exactly when the analyzer is paused. */
    var sheetVisible: Boolean by mutableStateOf(false)
        private set

    var notice: String? by mutableStateOf(null)
        private set

    var virusTotalState: ProviderState by mutableStateOf(ProviderState.Idle)
        private set
    var urlscanState: ProviderState by mutableStateOf(ProviderState.Idle)
        private set
    var urlhausState: ProviderState by mutableStateOf(ProviderState.Idle)
        private set

    fun onPayload(raw: String) {
        decision = PayloadPolicy.classify(raw)
        virusTotalState = ProviderState.Idle
        urlscanState = ProviderState.Idle
        urlhausState = ProviderState.Idle
        sheetVisible = true
    }

    fun dismissSheet() {
        sheetVisible = false
    }

    fun showNotice(message: String) {
        notice = message
    }

    fun clearNotice() {
        notice = null
    }

    // -- Gallery setting ---------------------------------------------------------------------

    val galleryImportEnabled: Boolean get() = settings.galleryImportEnabled

    fun setGalleryImportEnabled(enabled: Boolean) {
        settings.galleryImportEnabled = enabled
    }

    // -- Provider disclosure -----------------------------------------------------------------

    fun needsDisclosure(provider: Settings.Provider): Boolean =
        !settings.isProviderDisclosureAccepted(provider)

    fun acceptDisclosure(provider: Settings.Provider) {
        // Records only that the disclosure was accepted. The scanned URL is never stored.
        settings.acceptProviderDisclosure(provider)
    }

    // -- Providers ---------------------------------------------------------------------------

    fun runVirusTotal(url: String) = runProvider(
        setState = { virusTotalState = it },
        missingKey = "VirusTotal needs your own API key. Add it in Settings; the app ships no key.",
        block = { key -> VirusTotalClient.lookup(url, key) },
        keyOf = { secrets.virusTotalApiKey },
    )

    fun runUrlscan(url: String) = runProvider(
        setState = { urlscanState = it },
        missingKey = "urlscan.io needs your own API key. Add it in Settings; the app ships no key.",
        block = { key -> UrlscanClient.submit(url, key) },
        keyOf = { secrets.urlscanApiKey },
    )

    fun runUrlhaus(url: String) = runProvider(
        setState = { urlhausState = it },
        missingKey = "URLhaus needs your own Auth-Key. Add it in Settings; the app ships no key. " +
            "Without a key the app will not call the network.",
        block = { key -> UrlhausClient.lookup(url, key) },
        keyOf = { secrets.urlhausAuthKey },
    )

    private fun runProvider(
        setState: (ProviderState) -> Unit,
        missingKey: String,
        block: suspend (String) -> ProviderOutcome,
        keyOf: () -> String,
    ) {
        val key = keyOf()
        if (key.isBlank()) {
            setState(ProviderState.Failed(missingKey))
            return
        }
        setState(ProviderState.Loading)
        viewModelScope.launch {
            val next = try {
                ProviderState.Done(block(key))
            } catch (e: ProviderException) {
                ProviderState.Failed(e.message ?: "The provider rejected the request.")
            } catch (_: Exception) {
                ProviderState.Failed("The provider could not be reached. Check the connection.")
            }
            setState(next)
        }
    }
}
