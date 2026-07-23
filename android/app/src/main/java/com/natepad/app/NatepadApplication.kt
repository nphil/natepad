package com.natepad.app

import android.app.Application
import android.content.Context
import com.natepad.app.data.KeyRepository
import com.natepad.app.pgp.PgpService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

class NatepadApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // Register Bouncy Castle provider, replacing Android's built-in BC stub
        Security.removeProvider("BC")
        Security.insertProviderAt(BouncyCastleProvider(), 1)

        backfillKeyExpiry()
    }

    /**
     * One-time migration: records stored before expiry tracking existed have
     * expiresAt = null even when the key itself carries an expiration date.
     * Re-derive it from the stored key material once, then never again.
     */
    private fun backfillKeyExpiry() {
        val prefs = getSharedPreferences("natepad_settings", Context.MODE_PRIVATE)
        if (prefs.getBoolean(PREF_EXPIRY_BACKFILLED, false)) return
        appScope.launch {
            val repo = KeyRepository.getInstance(this@NatepadApplication)
            repo.getKeys().forEach { rec ->
                if (rec.expiresAt == null) {
                    runCatching {
                        PgpService.parseKeys(rec.armoredPublic)
                            .firstNotNullOfOrNull { PgpService.parsedKeyToRecord(it) }
                            ?.expiresAt
                            ?.let { repo.addKey(rec.copy(expiresAt = it)) }
                    }
                }
            }
            prefs.edit().putBoolean(PREF_EXPIRY_BACKFILLED, true).apply()
        }
    }

    private companion object {
        const val PREF_EXPIRY_BACKFILLED = "expiry_backfilled_v1"
    }
}
