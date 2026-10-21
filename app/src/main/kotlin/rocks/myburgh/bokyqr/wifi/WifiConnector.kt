package rocks.myburgh.bokyqr.wifi

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.annotation.RequiresApi
import rocks.myburgh.bokyqr.R
import rocks.myburgh.bokyqr.core.WifiPayload

object WifiConnector {
    fun connect(context: Context, network: WifiPayload) {
        try {
            if (Build.VERSION.SDK_INT >= 29 && network.security.equals("WEP", true)) {
                Toast.makeText(context, R.string.wifi_wep_settings, Toast.LENGTH_LONG).show()
                context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                return
            }
            if (Build.VERSION.SDK_INT >= 30) {
                val suggestion = suggestion(network)
                context.startActivity(Intent(Settings.ACTION_WIFI_ADD_NETWORKS).putParcelableArrayListExtra(Settings.EXTRA_WIFI_NETWORK_LIST, arrayListOf(suggestion.build())))
            } else if (Build.VERSION.SDK_INT == 29) {
                val suggestion = suggestion(network)
                val manager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                val status = manager.addNetworkSuggestions(listOf(suggestion.build()))
                Toast.makeText(context, if (status == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS) context.getString(R.string.wifi_suggested) else context.getString(R.string.wifi_suggestion_rejected, status), Toast.LENGTH_LONG).show()
            } else legacy(context, network)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, R.string.wifi_unavailable, Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, context.getString(R.string.wifi_setup_failed, e.message ?: context.getString(R.string.wifi_system_error)), Toast.LENGTH_LONG).show()
        }
    }
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun suggestion(n: WifiPayload): WifiNetworkSuggestion.Builder {
        val b = WifiNetworkSuggestion.Builder().setSsid(n.ssid).setIsHiddenSsid(n.hidden)
        when (n.security.uppercase()) {
            "WPA3", "SAE" -> n.password?.let { b.setWpa3Passphrase(it) }
            "NOPASS", "OPEN" -> Unit
            else -> n.password?.let { b.setWpa2Passphrase(it) }
        }
        return b
    }
    @Suppress("DEPRECATION")
    private fun legacy(context: Context, n: WifiPayload) {
        val manager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val c = WifiConfiguration().apply {
            SSID = "\"${n.ssid}\""; hiddenSSID = n.hidden
            when (n.security.uppercase()) {
                "NOPASS", "OPEN" -> allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                "WEP" -> { val key = n.password.orEmpty(); wepKeys[0] = if (key.matches(Regex("[0-9A-Fa-f]{10}|[0-9A-Fa-f]{26}|[0-9A-Fa-f]{58}"))) key else "\"$key\""; allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE); allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40); allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104) }
                else -> { val key = n.password.orEmpty(); preSharedKey = if (key.matches(Regex("[0-9A-Fa-f]{64}"))) key else "\"$key\""; allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK) }
            }
        }
        val id = manager.addNetwork(c)
        check(id >= 0 && manager.enableNetwork(id, true) && manager.reconnect()) { "Android could not connect" }
        Toast.makeText(context, R.string.wifi_connecting, Toast.LENGTH_LONG).show()
    }
}
