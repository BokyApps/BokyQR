package rocks.myburgh.bokyqr

import android.app.Application

/**
 * Deliberately inert.
 *
 * There is no network call, no crypto/Keystore work, no analytics and no crash reporter in
 * `onCreate`. The encrypted key store is created lazily, the first time Settings or a provider
 * needs it, never while the process is starting.
 */
class BokyApp : Application()
