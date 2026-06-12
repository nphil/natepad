package com.natepad.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
    onUnlock: () -> Unit
) {
    if (isLocked) {
        LockScreen(onUnlock = onUnlock)
        return
    }

    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val isTablet = screenWidthDp >= 600
    val isExpanded = screenWidthDp >= 840

    var currentDest by rememberSaveable { mutableStateOf(NavDestination.HOME) }
    var cryptoMode by rememberSaveable { mutableStateOf(CryptoMode.ENCRYPT) }
    var showCrypto by rememberSaveable { mutableStateOf(false) }

    // Hoist all drawer state so Compose slot count stays stable across config changes.
    val expandedDrawerState = rememberDrawerState(DrawerValue.Open)
    val tabletDrawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    val onNavSelect: (NavDestination) -> Unit = { dest ->
        currentDest = dest
        if (dest != NavDestination.HOME) showCrypto = false
        if (!isExpanded && tabletDrawerState.isOpen) {
            scope.launch { tabletDrawerState.close() }
        }
    }

    val topBarTitle = when {
        showCrypto -> cryptoMode.label
        else -> currentDest.label
    }

    // imePadding: content slides up above the keyboard instead of being covered.
    // pointerInput tap handler: tapping any empty area clears focus → keyboard dismisses.
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
                else -> SettingsScreen(modifier = Modifier.fillMaxSize())
            }
        }
    }

    when {
        // ── Expanded (≥840dp): dismissible drawer that slides alongside content ──
        isExpanded -> {
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
                            title = { Text(topBarTitle) },
                            navigationIcon = {
                                IconButton(onClick = {
                                    scope.launch {
                                        if (expandedDrawerState.isOpen) expandedDrawerState.close()
                                        else expandedDrawerState.open()
                                    }
                                }) {
                                    Icon(Icons.Default.Menu, contentDescription = "Toggle sidebar")
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

        // ── Tablet (600–839dp): modal drawer opened via hamburger ─────────────
        isTablet -> {
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
                            title = { Text(topBarTitle) },
                            navigationIcon = {
                                IconButton(onClick = { scope.launch { tabletDrawerState.open() } }) {
                                    Icon(Icons.Default.Menu, contentDescription = "Open menu")
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

        // ── Phone (<600dp): bottom navigation bar ─────────────────────────────
        else -> {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(topBarTitle) },
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
 * Full-screen branded lock UI. The biometric prompt fires automatically on
 * foreground (MainActivity.onStart) — the button is the retry path after a
 * cancelled or failed prompt.
 */
@Composable
private fun LockScreen(onUnlock: () -> Unit) {
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
            Button(onClick = onUnlock) {
                Icon(
                    imageVector = Icons.Default.Fingerprint,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.size(8.dp))
                Text("Unlock")
            }
        }
    }
}
