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
import androidx.compose.ui.platform.testTag
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
    var status by remember { mutableStateOf("Core start ho raha hai…") }

    LaunchedEffect(Unit) {
        delay(500)
        status = "Encrypted key vault check ho raha hai…"
        delay(500)
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
    onContinueGuest: () -> Unit = onSignedIn,
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
                text = "Aapka personal AI assistant, seedha aapke phone par.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
            Spacer(modifier = Modifier.height(24.dp))

            GlassCard {
                SectionLabel("Step 1 of 4 · Optional Sign-In")
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Account Setup",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Google Sign-In bilkul optional hai. Aap chahein to Google se sign-in karein " +
                        "ya bina login ke seedha Guest Mode me Jarvis use karein.",
                    style = MaterialTheme.typography.bodyMedium,
                )

                if (!configured) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .background(accent.copy(alpha = 0.12f))
                            .padding(14.dp)
                    ) {
                        Text(
                            text = "Is build me Google OAuth client ID configure nahi hai, isliye aap " +
                                "seedha Guest Mode me sabhi features smoothly use kar sakte hain.",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))
                if (configured) {
                    NeonButton(
                        text = if (vm.signIn.loading) "Google khul raha hai…" else "Google se Sign In karo",
                        onClick = { vm.signInWithGoogle(context) { onSignedIn() } },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("google_signin_button"),
                        enabled = !vm.signIn.loading,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    GhostButton(
                        text = "Guest Mode me aage badho",
                        onClick = {
                            vm.continueInLimitedMode()
                            onContinueGuest()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("guest_mode_button"),
                        enabled = !vm.signIn.loading,
                        accent = accent,
                    )
                } else {
                    NeonButton(
                        text = "Guest Mode me aage badho",
                        onClick = {
                            vm.continueInLimitedMode()
                            onContinueGuest()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("guest_mode_button"),
                        accent = accent,
                    )
                }

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
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            GlassCard {
                SectionLabel("Privacy First")
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "• Kabhi bhi SMS, OTP ya call-log permission nahi mangi jaati.\n" +
                        "• Passwords aur OTP fields kabhi padhe nahi jaate.\n" +
                        "• AI ko bhejne se pehle sensitive details redact hoti hain.\n" +
                        "• Aapki Gemini API key Android Keystore me encrypted rehti hai.",
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
                text = "Integrity Check",
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = "Play Integrity check karta hai ki app install genuine hai ya local.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
            Spacer(modifier = Modifier.height(20.dp))

            GlassCard {
                val (label, color) = when (status) {
                    AppPreferences.STATUS_VERIFIED -> "VERIFIED" to SuccessGreen
                    AppPreferences.STATUS_LIMITED -> "LOCAL / GUEST MODE" to WarningAmber
                    AppPreferences.STATUS_ERROR -> "CHECK NAHI HO PAYA" to DangerRed
                    else -> "CHECK CHAL RAHA HAI…" to TextMuted
                }
                StatusChip(text = label, color = color, filled = true)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = if (vm.integrityUi.loading) {
                        "Google Play services se connect ho raha hai…"
                    } else {
                        detail.ifBlank { "Abhi koi result nahi aaya." }
                    },
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Note: Direct sideloaded APKs me limited status dikhna normal hai. " +
                        "Aap bina ruke aage badh sakte hain.",
                    color = TextMuted,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                )
                Spacer(modifier = Modifier.height(16.dp))
                NeonButton(
                    text = if (vm.integrityUi.loading) "Skip karke Aage Badho" else "Aage Badho",
                    onClick = onContinue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("integrity_continue_button"),
                    accent = accent,
                )
                Spacer(modifier = Modifier.height(10.dp))
                GhostButton(
                    text = "Dobara Check Karo",
                    onClick = { vm.runIntegrityCheck() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !vm.integrityUi.loading,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Integrity status Settings › Security me bhi kabhi bhi dekh sakte hain.",
                color = TextMuted,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                textAlign = TextAlign.Start,
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
