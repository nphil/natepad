package com.natepad.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import android.content.Context
import com.natepad.app.data.KeyRepository
import com.natepad.app.ui.screens.CryptoMode
import com.natepad.app.ui.screens.CryptoScreen
import com.natepad.app.ui.screens.CryptoScreenState
import com.natepad.app.ui.screens.HomeScreen
import com.natepad.app.ui.screens.KeysScreen
import com.natepad.app.ui.screens.OnboardingScreen
import com.natepad.app.ui.screens.SettingsScreen
import com.natepad.app.ui.theme.AppTheme
import com.natepad.app.ui.theme.NatepadTheme
import com.natepad.app.ui.theme.NatepadMotion
import com.natepad.app.util.PgpContentDetector
import com.natepad.app.util.PgpContentKind

private enum class NavDestination(
    val label: String,
    val icon: ImageVector,
    val iconSelected: ImageVector
) {
    HOME("Notepad", Icons.Outlined.Edit, Icons.Filled.Edit),
    KEYS("Keys", Icons.Outlined.Key, Icons.Filled.Key),
    SETTINGS("Settings", Icons.Outlined.Settings, Icons.Filled.Settings)
}

/** What is currently on screen — a top-level destination, possibly with the crypto editor over Home. */
private data class ScreenState(val dest: NavDestination, val crypto: Boolean)

/** Root-level content swap: biometric lock wins, then first-run intro, then the app. */
private enum class RootContent { LOCK, ONBOARDING, APP }

@Composable
fun NatepadApp(
    isLocked: Boolean,
    showUnlockButton: Boolean,
    onUnlock: () -> Unit,
    selectedTheme: AppTheme = AppTheme.MATERIAL_YOU,
    onThemeChange: (AppTheme) -> Unit = {},
    externalText: String? = null,
    onExternalTextConsumed: () -> Unit = {}
) {
    NatepadTheme(appTheme = selectedTheme) {
        var currentDest by rememberSaveable { mutableStateOf(NavDestination.HOME) }
        var cryptoMode by rememberSaveable { mutableStateOf(CryptoMode.ENCRYPT) }
        var showCrypto by rememberSaveable { mutableStateOf(false) }
        // Drafts live at app scope so leaving the editor (or switching destinations)
        // never loses what was typed. Cleared only when the process ends.
        val cryptoState = remember { CryptoScreenState() }
        var keyImportRequest by remember { mutableStateOf<String?>(null) }
        var keyGenerateRequest by remember { mutableStateOf(false) }

        // First-run intro. The flag lives in settings prefs so it shows once.
        // Existing installs are grandfathered in: anyone who already has keys
        // clearly knows the app — mark them done instead of showing the intro
        // after an update.
        val context = LocalContext.current
        val settingsPrefs = remember {
            context.getSharedPreferences("natepad_settings", Context.MODE_PRIVATE)
        }
        var showOnboarding by rememberSaveable {
            val done = settingsPrefs.getBoolean("onboarding_done", false)
            val existingUser = !done &&
                KeyRepository.getInstance(context).getKeys().isNotEmpty()
            if (existingUser) {
                settingsPrefs.edit().putBoolean("onboarding_done", true).apply()
            }
            mutableStateOf(!done && !existingUser)
        }

        fun finishOnboarding() {
            if (showOnboarding) {
                settingsPrefs.edit().putBoolean("onboarding_done", true).apply()
                showOnboarding = false
            }
        }

        fun openWithInput(mode: CryptoMode, text: String) {
            cryptoMode = mode
            cryptoState.stateFor(mode).let { st ->
                st.input = text
                st.output = ""
                st.status = null
            }
            currentDest = NavDestination.HOME
            showCrypto = true
        }

        // Text shared into the app (share sheet or Open-with) routes by content:
        // PGP blocks go to the matching workflow, keys to import, and anything
        // else becomes the plaintext of a fresh Encrypt.
        LaunchedEffect(externalText) {
            externalText?.let { text ->
                // Someone sharing content in is already using the app — don't
                // make them sit through the intro first.
                finishOnboarding()
                when (PgpContentDetector.detect(text)) {
                    PgpContentKind.ENCRYPTED_MESSAGE -> openWithInput(CryptoMode.DECRYPT, text)
                    PgpContentKind.SIGNED_MESSAGE -> openWithInput(CryptoMode.VERIFY, text)
                    PgpContentKind.PUBLIC_KEY, PgpContentKind.PRIVATE_KEY -> {
                        keyImportRequest = text
                        showCrypto = false
                        currentDest = NavDestination.KEYS
                    }
                    null -> openWithInput(CryptoMode.ENCRYPT, text)
                }
                onExternalTextConsumed()
            }
        }

        // System back from a crypto workflow returns Home instead of closing the app.
        BackHandler(enabled = showCrypto && !isLocked) { showCrypto = false }

        // Drafts persist, passphrases don't: wipe typed passphrases whenever the
        // editor closes so secrets never sit in memory-resident state longer than
        // the editing session.
        LaunchedEffect(showCrypto) {
            if (!showCrypto) {
                cryptoState.encrypt.passphrase = ""
                cryptoState.sign.passphrase = ""
            }
        }

        val focusManager = LocalFocusManager.current

        // Main app UI, declared as a lambda so it captures the state above.
        val appUi: @Composable () -> Unit = {
            // NavigationSuiteScaffold picks the right navigation UI from the window size
            // class: bottom bar on compact windows (phones, small split-screen windows),
            // navigation rail on medium/expanded (tablets, desktop windows) — and keeps
            // adapting as the window is resized.
            NavigationSuiteScaffold(
                navigationSuiteItems = {
                    NavDestination.entries.forEach { dest ->
                        item(
                            selected = currentDest == dest,
                            onClick = {
                                // Re-selecting Notepad pops the editor back to the dashboard.
                                if (dest == NavDestination.HOME && currentDest == NavDestination.HOME) {
                                    showCrypto = false
                                }
                                currentDest = dest
                                if (dest != NavDestination.HOME) showCrypto = false
                            },
                            icon = {
                                Icon(
                                    imageVector = if (currentDest == dest) dest.iconSelected else dest.icon,
                                    contentDescription = dest.label
                                )
                            },
                            label = { Text(dest.label) }
                        )
                    }
                }
            ) {
                val screen = ScreenState(currentDest, currentDest == NavDestination.HOME && showCrypto)

                AnimatedContent(
                    targetState = screen,
                    transitionSpec = { screenTransition() },
                    label = "screen",
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding()
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { focusManager.clearFocus() })
                        }
                ) { target ->
                    when {
                        target.crypto -> CryptoScreen(
                            states = cryptoState,
                            mode = cryptoMode,
                            onModeChange = { cryptoMode = it },
                            onBack = { showCrypto = false },
                            modifier = Modifier.fillMaxSize()
                        )
                        target.dest == NavDestination.HOME -> HomeScreen(
                            onModeSelected = { mode ->
                                cryptoMode = mode; showCrypto = true
                            },
                            onNavigateToKeys = { currentDest = NavDestination.KEYS },
                            modifier = Modifier.fillMaxSize(),
                            onOpenWithInput = { mode, text -> openWithInput(mode, text) },
                            onImportKey = { text ->
                                keyImportRequest = text; currentDest = NavDestination.KEYS
                            }
                        )
                        target.dest == NavDestination.KEYS -> KeysScreen(
                            modifier = Modifier.fillMaxSize(),
                            importRequest = keyImportRequest,
                            onImportRequestConsumed = { keyImportRequest = null },
                            generateRequest = keyGenerateRequest,
                            onGenerateRequestConsumed = { keyGenerateRequest = false }
                        )
                        else -> SettingsScreen(
                            selectedTheme = selectedTheme,
                            onThemeChange = onThemeChange,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }

        // The lock swap happens BELOW every remember/rememberSaveable above, so
        // locking unmounts the app UI (no touch passthrough, nothing readable by
        // accessibility services) while drafts and navigation state survive.
        AnimatedContent(
            targetState = if (isLocked) RootContent.LOCK
            else if (showOnboarding) RootContent.ONBOARDING
            else RootContent.APP,
            transitionSpec = {
                fadeIn(NatepadMotion.effectsDefault()) togetherWith fadeOut(NatepadMotion.effectsFast())
            },
            label = "lock"
        ) { content ->
            when (content) {
                RootContent.LOCK -> LockScreen(showUnlockButton = showUnlockButton, onUnlock = onUnlock)
                RootContent.ONBOARDING -> OnboardingScreen(
                    onCreateKey = {
                        finishOnboarding()
                        currentDest = NavDestination.KEYS
                        keyGenerateRequest = true
                    },
                    onSkip = ::finishOnboarding
                )
                RootContent.APP -> appUi()
            }
        }
    }
}

/**
 * M3 transition patterns, driven by the theme's expressive motion springs:
 * - Home <-> crypto editor: shared-axis X (push forward / pop back)
 * - between top-level destinations: fade-through with a slight scale
 */
private fun androidx.compose.animation.AnimatedContentTransitionScope<ScreenState>.screenTransition(): ContentTransform = when {
    initialState.crypto != targetState.crypto -> {
        val forward = targetState.crypto
        val enterOffset: (Int) -> Int = { full -> if (forward) full / 4 else -full / 4 }
        val exitOffset: (Int) -> Int = { full -> if (forward) -full / 4 else full / 4 }
        (slideInHorizontally(NatepadMotion.spatialDefault(), enterOffset) + fadeIn(NatepadMotion.effectsDefault())) togetherWith
            (slideOutHorizontally(NatepadMotion.spatialDefault(), exitOffset) + fadeOut(NatepadMotion.effectsFast()))
    }
    else -> {
        // Fade-through: scale is a bounds change, so it rides the spatial spring.
        (fadeIn(NatepadMotion.effectsDefault()) + scaleIn(initialScale = 0.92f, animationSpec = NatepadMotion.spatialDefault())) togetherWith
            fadeOut(NatepadMotion.effectsFast())
    }
}

/**
 * Full-screen branded lock UI. Shows a spinner while the biometric auto-prompt is
 * in progress; "Try again" button appears only after a failed/cancelled attempt.
 */
@Composable
private fun LockScreen(showUnlockButton: Boolean, onUnlock: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.shapes.extraLarge
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = "NatePad",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Unlock with your fingerprint or face",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(28.dp))
            if (showUnlockButton) {
                Button(onClick = onUnlock) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.size(8.dp))
                    Text("Try again")
                }
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(36.dp),
                    strokeWidth = 3.dp
                )
            }
        }
    }
}
