package com.natepad.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DismissibleDrawerSheet
import androidx.compose.material3.DismissibleNavigationDrawer
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.natepad.app.ui.screens.CryptoMode
import com.natepad.app.ui.screens.CryptoScreen
import com.natepad.app.ui.screens.HomeScreen
import com.natepad.app.ui.screens.KeysScreen
import com.natepad.app.ui.screens.SettingsScreen
import com.natepad.app.ui.theme.AppTheme
import com.natepad.app.ui.theme.NatepadTheme
import kotlinx.coroutines.launch

private enum class NavDestination(
    val label: String,
    val icon: ImageVector,
    val iconSelected: ImageVector
) {
    HOME("Notepad", Icons.Outlined.Edit, Icons.Filled.Edit),
    KEYS("Keys", Icons.Outlined.Key, Icons.Filled.Key),
    SETTINGS("Settings", Icons.Outlined.Settings, Icons.Filled.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NatepadApp(
    isLocked: Boolean,
    showUnlockButton: Boolean,
    onUnlock: () -> Unit,
    selectedTheme: AppTheme = AppTheme.MATERIAL_YOU,
    onThemeChange: (AppTheme) -> Unit = {}
) {
    NatepadTheme(appTheme = selectedTheme) {
        // All state declared unconditionally — required by Compose slot tracking.
        val configuration = LocalConfiguration.current
        val screenWidthDp = configuration.screenWidthDp
        val isTablet = screenWidthDp >= 600
        val isExpanded = screenWidthDp >= 840

        var currentDest by rememberSaveable { mutableStateOf(NavDestination.HOME) }
        var cryptoMode by rememberSaveable { mutableStateOf(CryptoMode.ENCRYPT) }
        var showCrypto by rememberSaveable { mutableStateOf(false) }

        val expandedDrawerState = rememberDrawerState(DrawerValue.Open)
        val tabletDrawerState = rememberDrawerState(DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        val focusManager = LocalFocusManager.current

        if (isLocked) {
            LockScreen(showUnlockButton = showUnlockButton, onUnlock = onUnlock)
            return@NatepadTheme
        }

        val onNavSelect: (NavDestination) -> Unit = { dest ->
            currentDest = dest
            if (dest != NavDestination.HOME) showCrypto = false
            if (!isExpanded && tabletDrawerState.isOpen) {
                scope.launch { tabletDrawerState.close() }
            }
        }

        val topBarIcon: ImageVector = when {
            showCrypto -> Icons.Default.Lock
            else -> currentDest.iconSelected
        }
        val topBarLabel: String = when {
            showCrypto -> cryptoMode.label
            else -> currentDest.label
        }

        // imePadding: content slides up above keyboard instead of being covered.
        // pointerInput tap: tapping empty area clears focus → keyboard dismisses.
        val screenContent: @Composable (Modifier) -> Unit = { mod ->
            Box(
                modifier = mod
                    .imePadding()
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { focusManager.clearFocus() })
                    }
            ) {
                when {
                    currentDest == NavDestination.HOME && showCrypto -> {
                        CryptoScreen(initialMode = cryptoMode, isTablet = isTablet, modifier = Modifier.fillMaxSize())
                    }
                    currentDest == NavDestination.HOME -> {
                        HomeScreen(
                            onModeSelected = { mode -> cryptoMode = mode; showCrypto = true },
                            onNavigateToKeys = { currentDest = NavDestination.KEYS },
                            isTablet = isTablet,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    currentDest == NavDestination.KEYS -> {
                        KeysScreen(isTablet = isTablet, modifier = Modifier.fillMaxSize())
                    }
                    else -> SettingsScreen(
                        selectedTheme = selectedTheme,
                        onThemeChange = onThemeChange,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        when {
            // ── Expanded (≥840dp): dismissible drawer alongside content ──────
            isExpanded -> {
                val drawerOpen = expandedDrawerState.targetValue == DrawerValue.Open
                DismissibleNavigationDrawer(
                    drawerState = expandedDrawerState,
                    drawerContent = {
                        DismissibleDrawerSheet {
                            DrawerContent(currentDest = currentDest, onNavSelect = onNavSelect)
                        }
                    }
                ) {
                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = { TopBarTitle(icon = topBarIcon, label = topBarLabel) },
                                navigationIcon = {
                                    HamburgerButton(isOpen = drawerOpen) {
                                        scope.launch {
                                            if (drawerOpen) expandedDrawerState.close()
                                            else expandedDrawerState.open()
                                        }
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                )
                            )
                        }
                    ) { innerPadding ->
                        screenContent(Modifier.padding(innerPadding).fillMaxSize())
                    }
                }
            }

            // ── Tablet (600–839dp): modal drawer via hamburger ───────────────
            isTablet -> {
                val drawerOpen = tabletDrawerState.targetValue == DrawerValue.Open
                ModalNavigationDrawer(
                    drawerState = tabletDrawerState,
                    drawerContent = {
                        ModalDrawerSheet {
                            DrawerContent(currentDest = currentDest, onNavSelect = onNavSelect)
                        }
                    }
                ) {
                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = { TopBarTitle(icon = topBarIcon, label = topBarLabel) },
                                navigationIcon = {
                                    HamburgerButton(isOpen = drawerOpen) {
                                        scope.launch { tabletDrawerState.open() }
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                )
                            )
                        }
                    ) { innerPadding ->
                        screenContent(Modifier.padding(innerPadding).fillMaxSize())
                    }
                }
            }

            // ── Phone (<600dp): bottom navigation bar ────────────────────────
            else -> {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { TopBarTitle(icon = topBarIcon, label = topBarLabel) },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                    },
                    bottomBar = {
                        NavigationBar {
                            NavDestination.entries.forEach { dest ->
                                NavigationBarItem(
                                    icon = {
                                        Icon(
                                            imageVector = if (currentDest == dest) dest.iconSelected else dest.icon,
                                            contentDescription = dest.label
                                        )
                                    },
                                    label = { Text(dest.label) },
                                    selected = currentDest == dest,
                                    onClick = { onNavSelect(dest) }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    screenContent(Modifier.padding(innerPadding).fillMaxSize())
                }
            }
        }
    }
}

/** Top bar title: icon + text, animates on destination change. */
@Composable
private fun TopBarTitle(icon: ImageVector, label: String) {
    AnimatedContent(
        targetState = icon to label,
        transitionSpec = {
            (fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.9f)) togetherWith
                (fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.9f))
        },
        label = "top_bar_title"
    ) { (ic, lbl) ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = ic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.size(10.dp))
            Text(lbl)
        }
    }
}

/** Hamburger button that cross-fades to a Close icon when the drawer is open. */
@Composable
private fun HamburgerButton(isOpen: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        AnimatedContent(
            targetState = isOpen,
            transitionSpec = {
                (fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.7f)) togetherWith
                    (fadeOut(tween(180)) + scaleOut(tween(180), targetScale = 0.7f))
            },
            label = "hamburger"
        ) { open ->
            Icon(
                imageVector = if (open) Icons.Default.Close else Icons.Default.Menu,
                contentDescription = if (open) "Close menu" else "Open menu"
            )
        }
    }
}

@Composable
private fun DrawerContent(
    currentDest: NavDestination,
    onNavSelect: (NavDestination) -> Unit
) {
    Text(
        text = "NatePad",
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 28.dp, top = 24.dp, end = 28.dp, bottom = 4.dp)
    )
    Text(
        text = "PGP Notepad",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 28.dp, bottom = 20.dp)
    )
    NavDestination.entries.forEach { dest ->
        NavigationDrawerItem(
            icon = {
                Icon(
                    imageVector = if (currentDest == dest) dest.iconSelected else dest.icon,
                    contentDescription = dest.label
                )
            },
            label = { Text(dest.label) },
            selected = currentDest == dest,
            onClick = { onNavSelect(dest) },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
        )
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
                        RoundedCornerShape(24.dp)
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
