package com.natepad.app.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.natepad.app.ui.screens.CryptoMode
import com.natepad.app.ui.screens.CryptoScreen
import com.natepad.app.ui.screens.HomeScreen
import com.natepad.app.ui.screens.KeysScreen
import com.natepad.app.ui.screens.SettingsScreen

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

    val onNavSelect: (NavDestination) -> Unit = { dest ->
        currentDest = dest
        if (dest != NavDestination.HOME) showCrypto = false
    }

    val topBarTitle = when {
        showCrypto -> cryptoMode.label
        else -> currentDest.label
    }

    val topBar: @Composable () -> Unit = {
        TopAppBar(
            title = { Text(topBarTitle) },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )
    }

    val screenContent: @Composable (Modifier) -> Unit = { mod ->
        when {
            currentDest == NavDestination.HOME && showCrypto -> {
                CryptoScreen(initialMode = cryptoMode, isTablet = isTablet, modifier = mod)
            }
            currentDest == NavDestination.HOME -> {
                HomeScreen(
                    onModeSelected = { mode ->
                        cryptoMode = mode
                        showCrypto = true
                    },
                    onNavigateToKeys = { currentDest = NavDestination.KEYS },
                    isTablet = isTablet,
                    modifier = mod
                )
            }
            currentDest == NavDestination.KEYS -> {
                KeysScreen(isTablet = isTablet, modifier = mod)
            }
            else -> {
                SettingsScreen(modifier = mod)
            }
        }
    }

    when {
        isExpanded -> {
            PermanentNavigationDrawer(
                drawerContent = {
                    PermanentDrawerSheet {
                        Text(
                            text = "NatePad",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp)
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
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                        }
                    }
                }
            ) {
                Scaffold(topBar = topBar) { innerPadding ->
                    screenContent(Modifier.padding(innerPadding).fillMaxSize())
                }
            }
        }

        isTablet -> {
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail {
                    NavDestination.entries.forEach { dest ->
                        NavigationRailItem(
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
                Scaffold(
                    topBar = topBar,
                    modifier = Modifier.weight(1f)
                ) { innerPadding ->
                    screenContent(Modifier.padding(innerPadding).fillMaxSize())
                }
            }
        }

        else -> {
            Scaffold(
                topBar = topBar,
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
private fun LockScreen(onUnlock: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        icon = { Icon(Icons.Default.Lock, contentDescription = null) },
        title = { Text("NatePad is Locked") },
        text = { Text("Authenticate to access your encrypted notepad.") },
        confirmButton = {
            Button(onClick = onUnlock) { Text("Unlock") }
        }
    )
}
