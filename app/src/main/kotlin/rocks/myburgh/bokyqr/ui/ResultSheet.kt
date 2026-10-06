package rocks.myburgh.bokyqr.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import rocks.myburgh.bokyqr.core.DisplayReason
import rocks.myburgh.bokyqr.core.PayloadPolicy
import rocks.myburgh.bokyqr.core.ScanDecision
import rocks.myburgh.bokyqr.core.confirmBeforeOpen
import rocks.myburgh.bokyqr.data.Settings
import rocks.myburgh.bokyqr.handoff.Handoff
import rocks.myburgh.bokyqr.net.ProviderOutcome
import rocks.myburgh.bokyqr.scan.ProviderState
import rocks.myburgh.bokyqr.scan.ScanViewModel

/**
 * The result sheet. It shows what `:core` decided and offers only the actions that decision allows.
 *
 * Nothing here launches on its own: every provider call and every browser hand-off is a user tap,
 * and a bad reputation verdict from either VirusTotal or URLhaus adds a second confirmation that
 * names the source that produced it.
 *
 * The window is marked secure while this sheet is up: it is showing the scanned payload and any
 * verdict, and neither belongs in the recent-apps thumbnail or a screenshot.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultSheet(
    viewModel: ScanViewModel,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val decision = viewModel.decision ?: return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val copy = rememberSecureCopier()

    SecureWindow()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (decision) {
                is ScanDecision.OpenableUrl ->
                    OpenableUrlBody(decision, viewModel, context, copy, onOpenSettings)
                is ScanDecision.Passkey -> PasskeyBody(decision, context, copy)
                is ScanDecision.DisplayOnly -> DisplayOnlyBody(decision, copy)
            }
        }
    }
}

@Composable
private fun OpenableUrlBody(
    url: ScanDecision.OpenableUrl,
    viewModel: ScanViewModel,
    context: Context,
    copy: (String, String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    var showOpenConfirm by remember { mutableStateOf(false) }
    var pendingDisclosure by remember { mutableStateOf<Settings.Provider?>(null) }

    Text("Scanned URL", style = MaterialTheme.typography.titleMedium)
    Text(url.url, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
    if (url.hostUnicode != url.hostAscii) {
        Text(
            "Unicode host: ${url.hostUnicode}",
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            "Punycode host: ${url.hostAscii}",
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
        )
    }

    val virusTotal = (viewModel.virusTotalState as? ProviderState.Done)
        ?.outcome as? ProviderOutcome.VirusTotal
    val urlhaus = (viewModel.urlhausState as? ProviderState.Done)
        ?.outcome as? ProviderOutcome.Urlhaus

    // A second tap is required when either provider the user actually ran says this URL is bad.
    // URLhaus counts the same as VirusTotal: it is a shorter list, but every name on it is a URL
    // someone has already reported as malware delivery, and a single tap should not reach one.
    val warning = when {
        virusTotal != null && confirmBeforeOpen(virusTotal.malicious, virusTotal.suspicious) ->
            OpenWarning(
                title = "VirusTotal flagged this URL",
                body = "VirusTotal reports ${virusTotal.malicious} malicious and " +
                    "${virusTotal.suspicious} suspicious verdicts for this URL. " +
                    "Open it in your browser anyway?",
            )
        urlhaus?.listed == true ->
            OpenWarning(
                title = "URLhaus lists this URL",
                body = "URLhaus lists this exact URL in its malware blocklist" +
                    (urlhaus.threat?.let { " as $it" } ?: "") +
                    ". Open it in your browser anyway?",
            )
        else -> null
    }

    val open: () -> Unit = {
        if (!Handoff.openInBrowser(context, url.url)) {
            viewModel.showNotice("No browser is available to open this URL.")
        }
    }

    Button(
        onClick = { if (warning != null) showOpenConfirm = true else open() },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Open in browser")
    }

    Text(
        "Reputation checks below are off until you tap them. BokyQR never fetches the scanned URL " +
            "itself; a provider only runs when you ask it to.",
        style = MaterialTheme.typography.bodySmall,
    )

    val runProvider: (Settings.Provider) -> Unit = { provider ->
        when (provider) {
            Settings.Provider.VIRUS_TOTAL -> viewModel.runVirusTotal(url.url)
            Settings.Provider.URLSCAN -> viewModel.runUrlscan(url.url)
            Settings.Provider.URLHAUS -> viewModel.runUrlhaus(url.url)
        }
    }
    val tap: (Settings.Provider) -> Unit = { provider ->
        if (viewModel.needsDisclosure(provider)) pendingDisclosure = provider else runProvider(provider)
    }

    // A provider already Loading disables its button: a second tap would be a second concurrent
    // lookup, and against VirusTotal's four-per-minute limit that is how a lookup gets a 429.
    OutlinedButton(
        onClick = { tap(Settings.Provider.VIRUS_TOTAL) },
        enabled = viewModel.virusTotalState !is ProviderState.Loading,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Check with VirusTotal")
    }
    // The public report page for this URL. It is rebuilt here from the locally computed url id,
    // but it is still a link to a third-party site, so it passes the same two gates as the
    // urlscan link below: PayloadPolicy must call it an openable https URL, and the host must be
    // virustotal.com itself. No usable link, no button.
    val virusTotalLink: ScanDecision.OpenableUrl? = virusTotal
        ?.let { PayloadPolicy.classify(it.guiUrl) as? ScanDecision.OpenableUrl }
        ?.takeIf { it.hostAscii == "www.virustotal.com" || it.hostAscii == "virustotal.com" }
    val virusTotalTrailing: (@Composable () -> Unit)? = virusTotalLink?.let { link ->
        {
            IconButton(onClick = { Handoff.openInBrowser(context, link.url) }) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "Open VirusTotal details",
                )
            }
        }
    }
    ProviderStatusLine(
        state = viewModel.virusTotalState,
        loadingText = "Checking VirusTotal... this can take a minute for a new link.",
        doneColor = { outcome ->
            // Red only for confirmed malicious verdicts, amber for suspicious ones, green for a
            // report with neither. The counts themselves stay in the line either way.
            val result = outcome as ProviderOutcome.VirusTotal
            when {
                result.malicious > 0 -> Color.Red
                result.suspicious > 0 -> Color(0xFFFFC107)
                else -> Color(0xFF4CAF50)
            }
        },
        trailing = virusTotalTrailing,
    ) { outcome ->
        outcome as ProviderOutcome.VirusTotal
        "VirusTotal: ${outcome.malicious} malicious, ${outcome.suspicious} suspicious, " +
            "${outcome.harmless} harmless, ${outcome.undetected} undetected."
    }

    OutlinedButton(
        onClick = { tap(Settings.Provider.URLSCAN) },
        enabled = viewModel.urlscanState !is ProviderState.Loading,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Submit to urlscan.io")
    }
    ProviderStatusLine(viewModel.urlscanState) { outcome ->
        (outcome as ProviderOutcome.Urlscan)
        if (outcome.resultUrl.isBlank()) {
            "urlscan.io: submitted as unlisted."
        } else {
            "urlscan.io: submitted as unlisted. Unlisted scans are still visible to urlscan Pro researchers."
        }
    }
    val urlscanOutcome = (viewModel.urlscanState as? ProviderState.Done)
        ?.outcome as? ProviderOutcome.Urlscan

    // Belt and braces. UrlscanClient already rebuilds this URL from a validated uuid, but this
    // button hands a network-supplied string to ACTION_VIEW, so it re-checks the two things that
    // matter before it does: PayloadPolicy must call it an openable http(s) URL, and the host
    // must be urlscan.io itself.
    val urlscanLink: ScanDecision.OpenableUrl? = urlscanOutcome?.resultUrl
        ?.takeIf { it.isNotBlank() }
        ?.let { PayloadPolicy.classify(it) as? ScanDecision.OpenableUrl }
        ?.takeIf { it.hostAscii == "urlscan.io" }

    when {
        urlscanLink != null -> OutlinedButton(
            onClick = { Handoff.openInBrowser(context, urlscanLink.url) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Open urlscan.io result")
        }
        // A result was reported but we will not offer to open it. Say so rather than silently
        // dropping the button, so the scan still reads as complete.
        urlscanOutcome != null && urlscanOutcome.resultUrl.isNotBlank() -> Text(
            "urlscan.io returned a result link this app will not open. The scan was submitted as " +
                "unlisted; copy the scan id from the status line and look it up on urlscan.io yourself.",
            style = MaterialTheme.typography.bodySmall,
        )
    }

    OutlinedButton(
        onClick = { tap(Settings.Provider.URLHAUS) },
        enabled = viewModel.urlhausState !is ProviderState.Loading,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Look up in URLhaus")
    }
    ProviderStatusLine(
        state = viewModel.urlhausState,
        doneColor = { outcome ->
            // Amber for a miss, never green: URLhaus is a known-malware lookup, and "not listed"
            // is not the same as clean.
            if ((outcome as ProviderOutcome.Urlhaus).listed) Color.Red else Color(0xFFFFC107)
        },
    ) { outcome ->
        val urlhaus = outcome as ProviderOutcome.Urlhaus
        if (urlhaus.listed) {
            "URLhaus: this URL is in the URLhaus malware list" +
                (urlhaus.threat?.let { " ($it)" } ?: "") + "."
        } else {
            "URLhaus: not in the URLhaus malware list. URLhaus is a known-malware lookup, not a " +
                "full scan, so a miss is not a clean result."
        }
    }

    OutlinedButton(
        onClick = { copy("Scanned URL", url.url) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Copy URL")
    }

    TextButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
        Text("Add or change provider keys in Settings")
    }

    pendingDisclosure?.let { provider ->
        AlertDialog(
            onDismissRequest = { pendingDisclosure = null },
            title = { Text(disclosureTitle(provider)) },
            text = { Text(disclosureText(provider)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.acceptDisclosure(provider)
                    pendingDisclosure = null
                    runProvider(provider)
                }) { Text("Continue") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDisclosure = null }) { Text("Cancel") }
            },
        )
    }

    if (showOpenConfirm && warning != null) {
        AlertDialog(
            onDismissRequest = { showOpenConfirm = false },
            title = { Text(warning.title) },
            text = { Text(warning.body) },
            confirmButton = {
                TextButton(onClick = {
                    showOpenConfirm = false
                    open()
                }) { Text("Open anyway") }
            },
            dismissButton = {
                TextButton(onClick = { showOpenConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

/** What the user must be told, and by whom, before "Open in browser" opens a flagged URL. */
private class OpenWarning(val title: String, val body: String)

@Composable
private fun ProviderStatusLine(
    state: ProviderState,
    loadingText: String = "Checking...",
    doneColor: ((ProviderOutcome) -> Color)? = null,
    trailing: (@Composable () -> Unit)? = null,
    doneText: (ProviderOutcome) -> String,
) {
    when (state) {
        ProviderState.Idle -> Unit
        ProviderState.Loading -> Text(loadingText, style = MaterialTheme.typography.bodySmall)
        is ProviderState.Done -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = doneText(state.outcome),
                style = MaterialTheme.typography.bodySmall,
                color = doneColor?.invoke(state.outcome) ?: Color.Unspecified,
                modifier = Modifier.weight(1f, fill = false),
            )
            trailing?.invoke()
        }
        is ProviderState.Failed -> Text(
            state.message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun PasskeyBody(
    decision: ScanDecision.Passkey,
    context: Context,
    copy: (String, String) -> Unit,
) {
    var handoffFailed by remember { mutableStateOf(false) }

    Text("Passkey", style = MaterialTheme.typography.titleMedium)
    Text(decision.fidoUri, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
    Text(
        "BokyQR does not talk CTAP, BLE or the hybrid tunnel. It hands this FIDO URI to whatever app " +
            "on the device already handles the scheme.",
        style = MaterialTheme.typography.bodySmall,
    )

    Button(
        onClick = { handoffFailed = !Handoff.openPasskey(context, decision.fidoUri) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Open passkey")
    }

    if (handoffFailed) {
        Text(
            "No app on this device handles the FIDO scheme. Scan this QR with the system camera, or " +
                "open it with Play Services or microG, which can provide a handler.",
            style = MaterialTheme.typography.bodySmall,
        )
    }

    OutlinedButton(
        onClick = { copy("Passkey URI", decision.fidoUri) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Copy")
    }
}

@Composable
private fun DisplayOnlyBody(
    decision: ScanDecision.DisplayOnly,
    copy: (String, String) -> Unit,
) {
    Text("Text", style = MaterialTheme.typography.titleMedium)
    Text(decision.text, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
    Text(reasonText(decision.reason), style = MaterialTheme.typography.bodySmall)
    OutlinedButton(
        onClick = { copy("Scanned text", decision.text) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Copy")
    }
}

private fun reasonText(reason: DisplayReason): String = when (reason) {
    DisplayReason.BLANK -> "Nothing was scanned."
    DisplayReason.CONTROL_CHARACTERS -> "Rejected: contains control characters."
    DisplayReason.WHITESPACE_INSIDE -> "Rejected: contains whitespace."
    DisplayReason.BACKSLASH_TRAIN -> "Rejected: contains a backslash."
    DisplayReason.TOO_LONG -> "Rejected: longer than the length limit."
    DisplayReason.FOREIGN_SCHEME -> "Not an http or https URL. Shown as text only."
    DisplayReason.USERINFO -> "Rejected: carries a user:password@ part."
    DisplayReason.UNSAFE_HOST -> "Rejected: the host is not a usable name."
    DisplayReason.MALFORMED -> "Not a URL. Shown as text only."
    DisplayReason.LOCAL_OR_PRIVATE_HOST ->
        "Not opened: this points at this device or the network it is on (localhost, a private or " +
            "link-local address, or a cloud metadata address), not at a public website. The text " +
            "is still here to copy."
}

private fun disclosureTitle(provider: Settings.Provider): String = when (provider) {
    Settings.Provider.VIRUS_TOTAL -> "Send to VirusTotal?"
    Settings.Provider.URLSCAN -> "Send to urlscan.io?"
    Settings.Provider.URLHAUS -> "Send to URLhaus?"
}

private fun disclosureText(provider: Settings.Provider): String = when (provider) {
    Settings.Provider.VIRUS_TOTAL ->
        "The full URL leaves this phone and is sent to VirusTotal using your own API key. " +
            "BokyQR will not fetch the URL itself."
    Settings.Provider.URLSCAN ->
        "The full URL leaves this phone and is sent to urlscan.io using your own API key. The scan " +
            "is submitted as unlisted, never public, but unlisted scans are still visible to " +
            "urlscan Pro researchers."
    Settings.Provider.URLHAUS ->
        "The full URL leaves this phone and is sent to URLhaus using your own Auth-Key. URLhaus is " +
            "a known-malware lookup, not a full scan: it only says whether this exact URL is already " +
            "in the URLhaus blocklist."
}
