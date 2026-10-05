package rocks.myburgh.bokyqr.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Non-secret preferences. Nothing sensitive is written here: this file records only booleans
 * ("is the gallery import enabled", "has the user accepted the disclosure for provider X").
 *
 * The disclosure flags deliberately store *that* a disclosure was accepted, never the URL that
 * prompted it. Re-opening the app therefore never tells anyone which code was scanned.
 */
class Settings(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    /** Gallery scanning via the Android photo picker. On by default. */
    var galleryImportEnabled: Boolean
        get() = prefs.getBoolean(KEY_GALLERY, true)
        set(value) = prefs.edit().putBoolean(KEY_GALLERY, value).apply()

    /** True once the user has accepted that a provider will receive the full URL. */
    fun isProviderDisclosureAccepted(provider: Provider): Boolean =
        prefs.getBoolean(disclosureKey(provider), false)

    fun acceptProviderDisclosure(provider: Provider) {
        prefs.edit().putBoolean(disclosureKey(provider), true).apply()
    }

    enum class Provider { VIRUS_TOTAL, URLSCAN, URLHAUS }

    private fun disclosureKey(provider: Provider) = "disclosure_accepted_${provider.name.lowercase()}"

    private companion object {
        const val FILE_NAME = "bokyqr_settings"
        const val KEY_GALLERY = "gallery_import_enabled"
    }
}
