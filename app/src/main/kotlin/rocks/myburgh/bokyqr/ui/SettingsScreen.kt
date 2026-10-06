package rocks.myburgh.bokyqr.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import rocks.myburgh.bokyqr.core.PayloadPolicy
import rocks.myburgh.bokyqr.core.ScanDecision
import rocks.myburgh.bokyqr.data.Secrets
import rocks.myburgh.bokyqr.handoff.Handoff
import rocks.myburgh.bokyqr.scan.ScanViewModel

/** How long the check mark stays up after a key is written to [Secrets]. */
private const val SAVE_FEEDBACK_MILLIS = 2_000L

/**
 * Settings. This is where "bring your own key" is spelled out.
 *
 * A key is edited as a draft first: typing changes the field and nothing else. It is written into
 * [Secrets], the encrypted store, only when the Save button next to its field is tapped, and it is
 * never logged. The screen also carries the limits and caveats each provider's terms require the
 * user to see.
 *
 * The window is secure while this screen is up: it shows the user's own API keys, so it must not
 * reach a screenshot, a screen recording or the recent-apps thumbnail.
 */
@Composable
fun SettingsScreen(viewModel: ScanViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val secrets = remember { Secrets.getOrNull(context) }
    var reveal by remember { mutableStateOf(false) }
    var virusTotal by remember { mutableStateOf(secrets?.virusTotalApiKey.orEmpty()) }
    var urlscan by remember { mutableStateOf(secrets?.urlscanApiKey.orEmpty()) }
    var urlhaus by remember { mutableStateOf(secrets?.urlhausAuthKey.orEmpty()) }
    var gallery by remember { mutableStateOf(viewModel.galleryImportEnabled) }
    var virusTotalSaved by remember { mutableStateOf(false) }
    var urlscanSaved by remember { mutableStateOf(false) }
    var urlhausSaved by remember { mutableStateOf(false) }

    val transformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation()
    val copy = rememberSecureCopier()

    // Writes one draft to the encrypted store and reports whether the write actually happened. A
    // Keystore that refuses to hand out the store means there is nothing to confirm, not a silent
    // success.
    fun persist(write: (Secrets) -> Unit): Boolean =
        secrets?.let { store -> runCatching { write(store) }.isSuccess } ?: false

    // The check mark is feedback, not state: it clears itself after a moment, and editing the
    // draft clears it early.
    LaunchedEffect(virusTotalSaved) {
        if (virusTotalSaved) {
            delay(SAVE_FEEDBACK_MILLIS)
            virusTotalSaved = false
        }
    }
    LaunchedEffect(urlscanSaved) {
        if (urlscanSaved) {
            delay(SAVE_FEEDBACK_MILLIS)
            urlscanSaved = false
        }
    }
    LaunchedEffect(urlhausSaved) {
        if (urlhausSaved) {
            delay(SAVE_FEEDBACK_MILLIS)
            urlhausSaved = false
        }
    }

    SecureWindow()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Back") }
            Text("Settings", style = MaterialTheme.typography.titleLarge)
        }

        Text("Bring your own key", style = MaterialTheme.typography.titleMedium)
        Text(
            "BokyQR ships no key, no shared key, no demo key and no proxy. Paste your own provider " +
                "keys below and tap Save. They are stored encrypted with a key held in the Android " +
                "Keystore, are excluded from backup and device transfer, and are never logged.",
            style = MaterialTheme.typography.bodySmall,
        )

        TextButton(onClick = { reveal = !reveal }) {
            Text(if (reveal) "Hide keys" else "Show keys")
        }

        KeyField(
            label = "VirusTotal API key",
            value = virusTotal,
            onValueChange = {
                virusTotal = it
                virusTotalSaved = false
            },
            transformation = transformation,
            saved = virusTotalSaved,
            onSave = { if (persist { it.virusTotalApiKey = virusTotal }) virusTotalSaved = true },
        )
        CopyKeyButton("Copy VirusTotal API key", "VirusTotal API key", virusTotal, copy)
        LinkButton("Get a VirusTotal API key", "https://www.virustotal.com/gui/my-apikey")
        Text(
            "The public VirusTotal API allows 500 requests per day and 4 requests per minute, and " +
                "must not be used in commercial products.",
            style = MaterialTheme.typography.bodySmall,
        )

        KeyField(
            label = "urlscan.io API key",
            value = urlscan,
            onValueChange = {
                urlscan = it
                urlscanSaved = false
            },
            transformation = transformation,
            saved = urlscanSaved,
            onSave = { if (persist { it.urlscanApiKey = urlscan }) urlscanSaved = true },
        )
        CopyKeyButton("Copy urlscan.io API key", "urlscan.io API key", urlscan, copy)
        LinkButton("urlscan.io API documentation", "https://urlscan.io/docs/api/")
        Text(
            "Scans are always submitted as unlisted, never public. Unlisted scans are still visible " +
                "to urlscan Pro researchers.",
            style = MaterialTheme.typography.bodySmall,
        )

        KeyField(
            label = "URLhaus Auth-Key",
            value = urlhaus,
            onValueChange = {
                urlhaus = it
                urlhausSaved = false
            },
            transformation = transformation,
            saved = urlhausSaved,
            onSave = { if (persist { it.urlhausAuthKey = urlhaus }) urlhausSaved = true },
        )
        CopyKeyButton("Copy URLhaus Auth-Key", "URLhaus Auth-Key", urlhaus, copy)
        LinkButton("URLhaus API documentation", "https://urlhaus.abuse.ch/api/")
        Text(
            "URLhaus is a known-malware lookup, not a full scan. It only says whether this exact URL " +
                "is already in the URLhaus malware list. A miss is not the same as clean.",
            style = MaterialTheme.typography.bodySmall,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Import QR from gallery", style = MaterialTheme.typography.bodyLarge)
            Switch(
                checked = gallery,
                onCheckedChange = {
                    gallery = it
                    viewModel.setGalleryImportEnabled(it)
                },
            )
        }
        Text(
            "Uses the Android photo picker only. No photo or media permission is requested. On by " +
                "default; when off, the gallery button is hidden and the picker never opens.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/**
 * One API-key row: the draft text the user is editing plus a button that writes it to the
 * encrypted store. The button shows a check mark only after a write that actually happened, and
 * the screen clears it again shortly after.
 */
@Composable
private fun KeyField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    transformation: VisualTransformation,
    saved: Boolean,
    onSave: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            visualTransformation = transformation,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onSave) {
            Icon(
                imageVector = if (saved) Icons.Filled.Check else Icons.Filled.Save,
                contentDescription = if (saved) "Saved" else "Save",
            )
        }
    }
}

/**
 * Copies an API key to the clipboard as a sensitive clip, so the keyboard on the next app does not
 * learn it and it does not turn up in clipboard history. Disabled when there is nothing to copy.
 */
@Composable
private fun CopyKeyButton(
    label: String,
    clipLabel: String,
    key: String,
    copy: (String, String) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        TextButton(onClick = { copy(clipLabel, key) }, enabled = key.isNotBlank()) { Text(label) }
    }
}

/**
 * Opens a documentation link. The URL is a hard-coded https constant at every call site, but it
 * still goes through PayloadPolicy on the way to ACTION_VIEW: the browser intent is the one place
 * where an unexpected scheme becomes someone else's app, and a link that fails the policy should
 * be a dead button rather than a silently opened intent.
 */
@Composable
private fun LinkButton(label: String, url: String) {
    val context: Context = LocalContext.current
    val openable = PayloadPolicy.classify(url) as? ScanDecision.OpenableUrl
    TextButton(
        enabled = openable != null,
        onClick = { openable?.let { Handoff.openInBrowser(context, it.url) } },
    ) { Text(label) }
}
