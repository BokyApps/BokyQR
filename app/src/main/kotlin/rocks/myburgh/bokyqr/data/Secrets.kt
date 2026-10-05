package rocks.myburgh.bokyqr.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Bring-your-own-key storage.
 *
 * The app ships no key, no shared key, no demo key and no proxy. The only keys that ever exist are
 * the ones the user pastes in Settings, and they live here: an [EncryptedSharedPreferences] file
 * whose contents are encrypted with a [MasterKey] held in the Android Keystore (`AES256_GCM`).
 *
 * The file is excluded from cloud backup and device transfer in `data_extraction_rules.xml` and
 * `backup_rules.xml`, and `android:allowBackup` is false, so a key never leaves the device through
 * a backup. Nothing here is ever logged.
 *
 * Constructing this touches the Keystore and is deliberately lazy: it must not happen in
 * `Application.onCreate`.
 */
class Secrets private constructor(private val prefs: SharedPreferences) {

    var virusTotalApiKey: String
        get() = prefs.getString(KEY_VIRUS_TOTAL, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_VIRUS_TOTAL, value.trim()).apply()

    var urlscanApiKey: String
        get() = prefs.getString(KEY_URLSCAN, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_URLSCAN, value.trim()).apply()

    var urlhausAuthKey: String
        get() = prefs.getString(KEY_URLHAUS, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_URLHAUS, value.trim()).apply()

    companion object {
        private const val FILE_NAME = "bokyqr_secrets"
        private const val KEY_VIRUS_TOTAL = "virus_total_api_key"
        private const val KEY_URLSCAN = "urlscan_api_key"
        private const val KEY_URLHAUS = "urlhaus_auth_key"

        @Volatile
        private var instance: Secrets? = null

        /** Lazily creates the encrypted store. Keystore work happens here, never in Application. */
        fun get(context: Context): Secrets {
            instance?.let { return it }
            return synchronized(this) {
                instance ?: create(context.applicationContext).also { instance = it }
            }
        }

        private fun create(context: Context): Secrets {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val prefs = EncryptedSharedPreferences.create(
                context,
                FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
            return Secrets(prefs)
        }
    }
}
