package com.jarvis.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jarvis.BuildConfig
import com.jarvis.core.ServiceLocator
import com.jarvis.core.StartDestination
import com.jarvis.data.prefs.AppPreferences
import com.jarvis.ui.theme.DangerRed
import com.jarvis.ui.theme.GhostButton
import com.jarvis.ui.theme.GlassCard
import com.jarvis.ui.theme.JarvisBackground
import com.jarvis.ui.theme.LocalAccent
import com.jarvis.ui.theme.NeonButton
import com.jarvis.ui.theme.SectionLabel
import com.jarvis.ui.theme.StatusChip
import com.jarvis.ui.theme.SuccessGreen
import com.jarvis.ui.theme.TextMuted
import com.jarvis.ui.theme.TextPrimary
import com.jarvis.ui.theme.TextSecondary
import com.jarvis.ui.theme.WarningAmber
import com.jarvis.ui.vm.SetupViewModel
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onReady: (StartDestination) -> Unit) {
    val accent = LocalAccent.current
    val transition = rememberInfiniteTransition(label = "splash")
    val pulse by transition.animateFloat(
        initialValue = 0.86f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "splash-pulse",
    )
    var status by remember { mutableStateOf("Waking up the core…") }

    LaunchedEffect(Unit) {
        delay(600)
        status = "Checking the encrypted key vault…"
        delay(600)
        onReady(ServiceLocator.startDestination())
    }

    JarvisBackground {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size((150f * pulse).dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(accent.copy(alpha = 0.35f), accent.copy(alpha = 0.05f), Color.Transparent)
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(accent)
                )
            }
            Spacer(modifier = Modifier.height(26.dp))
            Text(
                text = "JARVIS",
                style = MaterialTheme.typography.displayLarge,
                color = TextPrimary,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = status,
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )
        }
        Text(
            text = "v${BuildConfig.VERSION_NAME}",
            color = TextMuted,
            fontSize = 11.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(22.dp),
        )
    }
}

@Composable
fun SignInScreen(
    onSignedIn: () -> Unit,
    vm: SetupViewModel = viewModel(),
) {
    val context = LocalContext.current
    val configured = remember { ServiceLocator.auth.isConfigured() }
    val accent = LocalAccent.current

    JarvisBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Spacer(modifier = Modifier.height(22.dp))
            Text(
                text = "JARVIS",
                style = MaterialTheme.typography.displayLarge,
                color = accent,
            )
            Text(
                text = "A personal assistant that stays on your phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
            Spacer(modifier = Modifier.height(24.dp))

            GlassCard {
                SectionLabel("Step 1 of 4")
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Verify it's you",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Google sign-in is required before Jarvis can store anything. " +
                        "The app keeps your email on this device only; it is never uploaded.",
                    style = MaterialTheme.typography.bodyMedium,
                )

                if (!configured) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .background(WarningAmber.copy(alpha = 0.14f))
                            .padding(14.dp)
                    ) {
                        Text(
                            text = "This build has no Google OAuth client id yet. Add " +
                                "jarvisGoogleWebClientId to gradle.properties (see README), or " +
                                "continue in limited mode. Limited mode is clearly marked " +
                                "everywhere and is not real verification.",
                            color = WarningAmber,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))
                NeonButton(
                    text = if (vm.signIn.loading) "Opening Google…" else "Sign in with Google",
                    onClick = { vm.signInWithGoogle(context) { onSignedIn() } },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = configured && !vm.signIn.loading,
                )

                if (vm.signIn.loading) {
                    Spacer(modifier = Modifier.height(14.dp))
                    CircularProgressIndicator(color = accent, strokeWidth = 2.dp)
                }

                if (vm.signIn.error != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = vm.signIn.error ?: "",
                        color = DangerRed,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    GhostButton(
                        text = "Continue in limited mode",
                        onClick = {
                            vm.continueInLimitedMode()
                            onSignedIn()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        accent = WarningAmber,
                    )
                }

                if (!configured && !vm.signIn.loading && vm.signIn.error == null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    GhostButton(
                        text = "Continue in limited mode",
                        onClick = {
                            vm.continueInLimitedMode()
                            onSignedIn()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        accent = WarningAmber,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            GlassCard {
                SectionLabel("Privacy first")
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "• No SMS, OTP or call-log permissions. Ever.\n" +
                        "• Passwords and OTP fields are never read.\n" +
                        "• Everything sent to an AI provider is redacted first.\n" +
                        "• The API key is sealed with the Android Keystore.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun IntegrityScreen(
    onContinue: () -> Unit,
    vm: SetupViewModel = viewModel(),
) {
    val status by ServiceLocator.prefs.integrityStatus.collectAsStateWithLifecycle()
    val detail by ServiceLocator.prefs.integrityDetail.collectAsStateWithLifecycle()
    val accent = LocalAccent.current
    var started by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!started) {
            started = true
            vm.runIntegrityCheck()
        }
    }

    JarvisBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Spacer(modifier = Modifier.height(22.dp))
            SectionLabel("Step 2 of 4")
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Integrity check",
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = "Play Integrity tells us whether this install is genuine and " +
                    "untampered.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
            Spacer(modifier = Modifier.height(20.dp))

            GlassCard {
                val (label, color) = when (status) {
                    AppPreferences.STATUS_VERIFIED -> "VERIFIED" to SuccessGreen
                    AppPreferences.STATUS_LIMITED -> "NOT VERIFIED · LIMITED MODE" to WarningAmber
                    AppPreferences.STATUS_ERROR -> "COULD NOT CHECK" to DangerRed
                    else -> "CHECKING…" to TextMuted
                }
                StatusChip(text = label, color = color, filled = true)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = if (vm.integrityUi.loading) {
                        "Talking to Google Play services…"
                    } else {
                        detail.ifBlank { "No result yet." }
                    },
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Honest note: the integrity verdict is encrypted and can only be " +
                        "read by a server. A sideloaded APK will normally show limited mode. " +
                        "Jarvis never claims to be verified without a token.",
                    color = TextMuted,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                )
                Spacer(modifier = Modifier.height(16.dp))
                NeonButton(
                    text = if (vm.integrityUi.loading) "Checking…" else "Continue",
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !vm.integrityUi.loading,
                    accent = accent,
                )
                Spacer(modifier = Modifier.height(10.dp))
                GhostButton(
                    text = "Run the check again",
                    onClick = { vm.runIntegrityCheck() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !vm.integrityUi.loading,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "You can also continue without a verdict. The status appears on the " +
                    "home screen so it is never hidden.",
                color = TextMuted,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                textAlign = TextAlign.Start,
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
