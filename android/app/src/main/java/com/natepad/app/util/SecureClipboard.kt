package com.natepad.app.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Clipboard helper for secrets: marks the clip sensitive (masked in the Android 13+
 * clipboard preview / keyboard suggestions) and clears it after [CLEAR_AFTER_MS].
 * Clearing is best-effort — Android only lets the focused app touch the clipboard,
 * so if NatePad is in the background when the timer fires the clip stays until the
 * user copies something else.
 */
object SecureClipboard {
    const val CLEAR_AFTER_MS = 60_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var clearJob: Job? = null

    /** Plain copy for non-secret content (ciphertext, public keys, signed messages). */
    fun copy(context: Context, text: String, label: String = "NatePad") {
        val cm = context.applicationContext
            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    fun copySensitive(context: Context, text: String, label: String = "NatePad") {
        val appContext = context.applicationContext
        val cm = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clip.description.extras = PersistableBundle().apply {
            // ClipDescription.EXTRA_IS_SENSITIVE — inlined so it works below API 33 too
            putBoolean("android.content.extra.IS_SENSITIVE", true)
        }
        cm.setPrimaryClip(clip)

        clearJob?.cancel()
        clearJob = scope.launch {
            delay(CLEAR_AFTER_MS)
            runCatching {
                // Only wipe if our secret is still what's on the clipboard.
                val current = cm.primaryClip?.getItemAt(0)?.text?.toString()
                if (current == text) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        cm.clearPrimaryClip()
                    } else {
                        cm.setPrimaryClip(ClipData.newPlainText("", ""))
                    }
                }
            }
        }
    }
}

/** What kind of PGP block a piece of text contains. */
enum class PgpContentKind { ENCRYPTED_MESSAGE, SIGNED_MESSAGE, PUBLIC_KEY, PRIVATE_KEY }

object PgpContentDetector {
    fun detect(text: String): PgpContentKind? = when {
        text.contains("-----BEGIN PGP SIGNED MESSAGE-----") -> PgpContentKind.SIGNED_MESSAGE
        text.contains("-----BEGIN PGP MESSAGE-----") -> PgpContentKind.ENCRYPTED_MESSAGE
        text.contains("-----BEGIN PGP PRIVATE KEY BLOCK-----") ||
            text.contains("-----BEGIN PGP SECRET KEY BLOCK-----") -> PgpContentKind.PRIVATE_KEY
        text.contains("-----BEGIN PGP PUBLIC KEY BLOCK-----") -> PgpContentKind.PUBLIC_KEY
        else -> null
    }
}
