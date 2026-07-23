package com.natepad.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.natepad.app.ui.components.contentWidth
import kotlinx.coroutines.launch

private data class OnboardingPage(
    val icon: ImageVector,
    val title: String,
    val body: String
)

private val pages = listOf(
    OnboardingPage(
        icon = Icons.Default.Lock,
        title = "Welcome to NatePad",
        body = "A private PGP notepad. Encrypt, decrypt, sign and verify messages — " +
            "everything happens on this device. NatePad has no internet access, " +
            "so your keys and messages can never leave it."
    ),
    OnboardingPage(
        icon = Icons.Default.Key,
        title = "Keys make it work",
        body = "You encrypt a message with the recipient's public key; only their " +
            "private key can open it. Share your public key freely — by text, " +
            "file or QR code — and guard your private key and its passphrase."
    ),
    OnboardingPage(
        icon = Icons.Default.VerifiedUser,
        title = "Create your first key",
        body = "A key pair takes a few seconds to generate. Give it a name, an " +
            "optional email, and a passphrase you won't forget — there is no " +
            "way to recover it later."
    )
)

/** First-run intro: three swipeable pages ending in a create-key call to action. */
@Composable
fun OnboardingScreen(
    onCreateKey: () -> Unit,
    onSkip: () -> Unit
) {
    val pagerState = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val onLastPage = pagerState.currentPage == pages.size - 1

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .contentWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onSkip) { Text("Skip") }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { pageIndex ->
                val page = pages[pageIndex]
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .contentWidth(max = 480.dp)
                        .padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .background(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.shapes.extraLarge
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = page.icon,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(Modifier.height(28.dp))
                    Text(
                        text = page.title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = page.body,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Page indicator dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 16.dp)
            ) {
                pages.indices.forEach { index ->
                    val selected = pagerState.currentPage == index
                    val dotSize by animateDpAsState(
                        targetValue = if (selected) 10.dp else 8.dp, label = "dot_size"
                    )
                    val dotColor by animateColorAsState(
                        targetValue = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                        label = "dot_color"
                    )
                    Box(
                        modifier = Modifier
                            .size(dotSize)
                            .background(dotColor, CircleShape)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .contentWidth(max = 480.dp)
                    .padding(horizontal = 32.dp)
                    .padding(bottom = 24.dp)
            ) {
                if (onLastPage) {
                    Button(
                        onClick = onCreateKey,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) { Text("Create my first key", style = MaterialTheme.typography.titleMedium) }
                    TextButton(
                        onClick = onSkip,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    ) { Text("Maybe later") }
                } else {
                    Button(
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) { Text("Next", style = MaterialTheme.typography.titleMedium) }
                    Spacer(Modifier.height(52.dp))
                }
            }
        }
    }
}
