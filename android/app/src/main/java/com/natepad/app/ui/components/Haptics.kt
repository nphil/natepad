package com.natepad.app.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * Semantic haptic feedback usable from any composable. Compose's own
 * LocalHapticFeedback only exposes LongPress/TextHandleMove, so this goes
 * through View.performHapticFeedback with the platform constants instead,
 * gating the newer ones on API level.
 */
class HapticsCallbacks internal constructor(private val view: View) {

    /** Strong "operation succeeded" thump — encrypt, decrypt, import done. */
    fun success() {
        val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.VIRTUAL_KEY
        }
        view.performHapticFeedback(constant)
    }

    /** Light tap for discrete actions like copy-to-clipboard. */
    fun tap() {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    /** Negative buzz when an operation fails. */
    fun error() {
        val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.REJECT
        } else {
            HapticFeedbackConstants.LONG_PRESS
        }
        view.performHapticFeedback(constant)
    }
}

@Composable
fun rememberHaptics(): HapticsCallbacks {
    val view = LocalView.current
    return remember(view) { HapticsCallbacks(view) }
}
