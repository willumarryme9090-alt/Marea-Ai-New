package com.marea.ai.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.marea.ai.ChatViewModel
import com.marea.ai.avatar.AvatarState
import com.marea.ai.avatar.MareaAvatar
import com.marea.ai.data.AppSettings
import com.marea.ai.data.ChatMessage
import com.marea.ai.data.Role

@Composable
fun MainScreen(vm: ChatViewModel, settings: AppSettings, onOpenSettings: () -> Unit) {
    val ui by vm.ui.collectAsState()
    val context = LocalContext.current
    var input by rememberSaveable { mutableStateOf("") }
    var showRationale by remember { mutableStateOf(false) }
    val accent = accentColor(settings.accent)

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) vm.startListening()
        else vm.reportError(
            "Microphone permission denied. You can still type to Marea, or allow it in Settings → Permission status."
        )
    }

    fun onMicClick() {
        if (ui.listening) { vm.stopListening(); return }
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) vm.startListening() else showRationale = true
    }

    fun submit() {
        if (input.isBlank() || ui.thinking) return
        vm.send(input)
        input = ""
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Marea AI", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(12.dp))
            StatusChip(ui.avatarState, accent)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings")
            }
        }

        // Avatar
        Box(Modifier.weight(1.25f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            MareaAvatar(
                state = ui.avatarState,
                animationsActive = ui.foreground,
                blinkEnabled = settings.blinkEnabled,
                mouthEnabled = settings.mouthAnimEnabled,
                accent = accent,
                modifier = Modifier.fillMaxHeight().aspectRatio(736f / 1308f)
            )
        }

        Spacer(Modifier.height(8.dp))

        // Transcript
        Box(Modifier.weight(0.85f).fillMaxWidth()) {
            Transcript(ui.messages, settings.aiConfigured, accent)
        }

        // Error banner
        ui.error?.let { msg ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x33FF6B6B))
                    .clickable { vm.dismissError() }
                    .padding(10.dp)
            ) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(8.dp))
                Text(msg, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text("Dismiss", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Input row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Message Marea…") },
                modifier = Modifier.weight(1f),
                maxLines = 3,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit() })
            )
            if (ui.thinking || ui.speaking) {
                IconButton(onClick = { vm.stopAll() }) {
                    Icon(Icons.Filled.Stop, contentDescription = "Stop speaking or cancel request")
                }
            }
            FilledIconButton(
                onClick = { onMicClick() },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (ui.listening) accent else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = Color.White
                )
            ) {
                Icon(
                    if (ui.listening) Icons.Filled.Stop else Icons.Filled.Mic,
                    contentDescription = if (ui.listening) "Stop listening" else "Start voice input"
                )
            }
            IconButton(onClick = { submit() }, enabled = input.isNotBlank() && !ui.thinking) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send message")
            }
        }
    }

    if (showRationale) {
        AlertDialog(
            onDismissRequest = { showRationale = false },
            title = { Text("Microphone access") },
            text = {
                Text(
                    "Marea AI listens only after you tap the mic button, to turn your speech into text. " +
                        "Nothing is recorded in the background. Speech recognition is done by your phone's " +
                        "speech service, which may send audio to its provider depending on your device settings."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showRationale = false
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }) { Text("Continue") }
            },
            dismissButton = { TextButton(onClick = { showRationale = false }) { Text("Not now") } }
        )
    }
}

@Composable
private fun StatusChip(state: AvatarState, accent: Color) {
    val (label, color) = when (state) {
        AvatarState.IDLE -> "Ready" to MaterialTheme.colorScheme.onSurfaceVariant
        AvatarState.LISTENING -> "Listening…" to accent
        AvatarState.THINKING -> "Thinking…" to accent
        AvatarState.SPEAKING -> "Speaking…" to accent
        AvatarState.ERROR -> "Error" to MaterialTheme.colorScheme.error
    }
    Text(
        label, fontSize = 12.sp, color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
private fun Transcript(messages: List<ChatMessage>, aiConfigured: Boolean, accent: Color) {
    if (messages.isEmpty()) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Say hi — tap the mic or type a message.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!aiConfigured) {
                Text(
                    "AI is not configured yet. Open Settings → AI connection.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        return
    }
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) { listState.animateScrollToItem(messages.lastIndex) }
    LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxSize()) {
        items(messages, key = { it.id }) { m ->
            val mine = m.role == Role.USER
            Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                Text(
                    m.text, fontSize = 14.sp,
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (mine) accent.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}
