package rocks.myburgh.bokyqr

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import rocks.myburgh.bokyqr.data.Secrets

/**
 * Almost inert.
 *
 * There is no network call, no analytics and no crash reporter in `onCreate`, and nothing here
 * blocks the main thread. The one thing that does happen is warming the encrypted key store on
 * [Dispatchers.IO]: building it opens the Android Keystore and reads an
 * `EncryptedSharedPreferences` file, which is slow enough to be visible as a stutter the first
 * time Settings or a provider asks for a key. Doing it in the background here moves that cost off
 * the first tap. The scope is a [SupervisorJob] so a failure warms nothing and takes nothing else
 * with it, and any Keystore error is swallowed here and rediscovered as a missing key later.
 */
class BokyApp : Application() {

    override fun onCreate() {
        super.onCreate()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            Secrets.getOrNull(this@BokyApp)
        }
    }
}
