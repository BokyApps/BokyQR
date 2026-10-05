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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import rocks.myburgh.bokyqr.core.PayloadPolicy
import rocks.myburgh.bokyqr.core.ScanDecision
import rocks.myburgh.bokyqr.data.Secrets
import rocks.myburgh.bokyqr.handoff.Handoff
import rocks.myburgh.bokyqr.scan.ScanViewModel

/**
 * Settings. This is where "bring your own key" is spelled out.
 *
 * Keys are written straight into [Secrets], the encrypted store, and are never logged. The screen
 * also carries the limits and caveats each provider's terms require the user to see.
 */
@Composable
fun SettingsScreen(viewModel: ScanViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val secrets = remember { Secrets.get(context) }
    var reveal by remember { mutableStateOf(false) }
    var virusTotal by remember { mutableStateOf(secrets.virusTotalApiKey) }
    var urlscan by remember { mutableStateOf(secrets.urlscanApiKey) }
    var urlhaus by remember { mutableStateOf(secrets.urlhausAuthKey) }
    var gallery by remember { mutableStateOf(viewModel.galleryImportEnabled) }

    val transformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation()

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
                "keys below. They are stored encrypted with a key held in the Android Keystore, are " +
                "excluded from backup and device transfer, and are never logged.",
            style = MaterialTheme.typography.bodySmall,
        )

        TextButton(onClick = { reveal = !reveal }) {
            Text(if (reveal) "Hide keys" else "Show keys")
        }

        OutlinedTextField(
            value = virusTotal,
            onValueChange = {
                virusTotal = it
                secrets.virusTotalApiKey = it
            },
            label = { Text("VirusTotal API key") },
            singleLine = true,
            visualTransformation = transformation,
            modifier = Modifier.fillMaxWidth(),
        )
        LinkButton("Get a VirusTotal API key", "https://www.virustotal.com/gui/my-apikey")
        Text(
            "The public VirusTotal API allows 500 requests per day and 4 requests per minute, and " +
                "must not be used in commercial products.",
            style = MaterialTheme.typography.bodySmall,
        )

        OutlinedTextField(
            value = urlscan,
            onValueChange = {
                urlscan = it
                secrets.urlscanApiKey = it
            },
            label = { Text("urlscan.io API key") },
            singleLine = true,
            visualTransformation = transformation,
            modifier = Modifier.fillMaxWidth(),
        )
        LinkButton("urlscan.io API documentation", "https://urlscan.io/docs/api/")
        Text(
            "Scans are always submitted as unlisted, never public. Unlisted scans are still visible " +
                "to urlscan Pro researchers.",
            style = MaterialTheme.typography.bodySmall,
        )

        OutlinedTextField(
            value = urlhaus,
            onValueChange = {
                urlhaus = it
                secrets.urlhausAuthKey = it
            },
            label = { Text("URLhaus Auth-Key") },
            singleLine = true,
            visualTransformation = transformation,
            modifier = Modifier.fillMaxWidth(),
        )
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
