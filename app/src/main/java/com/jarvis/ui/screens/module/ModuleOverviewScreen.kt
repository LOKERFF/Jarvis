package com.jarvis.ui.screens.module

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jarvis.ui.nav.SettingsSection
import com.jarvis.ui.screens.settings.SettingsHint
import com.jarvis.ui.screens.settings.SettingsPage
import com.jarvis.ui.theme.GlassCard
import com.jarvis.ui.theme.GlassBorder
import com.jarvis.ui.theme.LocalAccent
import com.jarvis.ui.theme.SectionLabel
import com.jarvis.ui.theme.TextMuted
import com.jarvis.ui.theme.TextPrimary
import com.jarvis.ui.theme.TextSecondary

/**
 * Landing page for the add-on module. Four real destinations, each backed by
 * Room tables that survive restarts.
 */
@Composable
fun ModuleOverviewScreen(
    onBack: () -> Unit,
    onOpenSection: (SettingsSection) -> Unit,
) {
    SettingsPage(
        title = "Self Setup & Learning",
        subtitle = "Self-contained add-on module",
        onBack = onBack,
    ) {
        SettingsHint(
            "Ye module alag hai — bina kisi purane feature ko chhede. Chat me file attach " +
                "karke \"setup kar lo\" bolne se Jarvis khud plan banata hai, har step verify " +
                "karta hai, aur sab kuch permanently save karta hai."
        )

        EntryCard(
            title = "Saved Setups",
            body = "Har setup, uske steps, command output aur exit codes — restart ke baad bhi.",
            action = "Kholo",
            onClick = { onOpenSection(SettingsSection.SAVED_SETUPS) },
        )

        EntryCard(
            title = "Memory",
            body = "Aapki sikhayi hui corrections (AES-GCM encrypted). Har naye jawab se pehle " +
                "apply hoti hain. Search, edit aur delete available hai.",
            action = "Kholo",
            onClick = { onOpenSection(SettingsSection.MEMORY) },
        )

        EntryCard(
            title = "Terminal",
            body = "App sandbox me /system/bin/sh. Live output, dangerous command confirmation, " +
                "aur timestamped command log.",
            action = "Kholo",
            onClick = { onOpenSection(SettingsSection.TERMINAL) },
        )

        GlassCard {
            SectionLabel("Kaise use karein")
            Spacer(modifier = Modifier.height(8.dp))
            Step("1", "Chat me 📎 dabao aur koi file chuno (zip, txt, config…).")
            Step("2", "\"setup kar lo\" likho ya bolo.")
            Step("3", "Plan aur steps chat me dikhenge; khatarnak command par \"Haan\" maanga jayega.")
            Step("4", "Kuch galat lage to \"ye galat hai\" likho — main poochhunga kya sahi hai, " +
                "phir wo baat hamesha ke liye yaad rahegi.")
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Video ya file maangne par wo chat me hi player/card ke saath aa jayegi.",
                color = TextMuted,
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )
        }

        GlassCard {
            SectionLabel("Honest limits")
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Terminal app ke private folder ke andar chalti hai — system me nahi, " +
                    "kyunki root ke bina Android wo allow nahi karta. Isliye \"apt install\" " +
                    "jaisi cheezein fail hongi aur main wo saaf-saaf bataunga, nakli success " +
                    "nahi dikhaunga. Video phone ke encoder se banti hai (text cards), " +
                    "internet se footage download nahi hoti.",
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
        }
    }
}

@Composable
private fun EntryCard(
    title: String,
    body: String,
    action: String,
    onClick: () -> Unit,
) {
    val accent = LocalAccent.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .border(1.dp, GlassBorder.copy(alpha = 0.3f), RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(accent.copy(alpha = 0.16f))
                    .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(9.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = action,
                    color = accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = body,
            color = TextSecondary,
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )
    }
}

@Composable
private fun Step(number: String, text: String) {
    Row(modifier = Modifier.padding(vertical = 3.dp)) {
        Text(
            text = number,
            color = LocalAccent.current,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(end = 8.dp),
        )
        Text(
            text = text,
            color = TextSecondary,
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )
    }
}
