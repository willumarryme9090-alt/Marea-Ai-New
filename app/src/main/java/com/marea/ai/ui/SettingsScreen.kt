package com.marea.ai.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.speech.SpeechRecognizer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.marea.ai.ChatViewModel
import com.marea.ai.data.Accent
import com.marea.ai.data.AppSettings

@Composable
fun SettingsScreen(vm: ChatViewModel, settings: AppSettings, onBack: () -> Unit) {
    val ui by vm.ui.collectAsState()
    val context = LocalContext.current
    var confirmClear by remember { mutableStateOf(false) }

    // Re-evaluated whenever the app returns to the foreground (e.g. from system settings)
    val micGranted = remember(ui.foreground) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    }
    val recognitionAvailable = remember(ui.foreground) { SpeechRecognizer.isRecognitionAvailable(context) }

    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Settings", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Section("Avatar") {
                SwitchRow("Eye blinking", settings.blinkEnabled) { v -> vm.updateSettings { it.copy(blinkEnabled = v) } }
                SwitchRow("Mouth movement while speaking", settings.mouthAnimEnabled) { v ->
                    vm.updateSettings { it.copy(mouthAnimEnabled = v) }
                }
                Note(
                    "The avatar is your artwork with animated overlays (eyelids and mouth). It is a 2D " +
                        "approximation, not a 3D or fully rigged model. Mouth movement follows real speech " +
                        "playback but is not phoneme-level lip-sync."
                )
            }

            Section("Voice") {
                SwitchRow("Speak replies aloud", settings.speakReplies) { v -> vm.updateSettings { it.copy(speakReplies = v) } }
                Text("Speech rate: ${"%.1f".format(settings.speechRate)}×", fontSize = 13.sp)
                Slider(
                    value = settings.speechRate, valueRange = 0.5f..2f,
                    onValueChange = { v -> vm.updateSettings { it.copy(speechRate = v) } }
                )
                Text("Pitch: ${"%.1f".format(settings.speechPitch)}", fontSize = 13.sp)
                Slider(
                    value = settings.speechPitch, valueRange = 0.5f..2f,
                    onValueChange = { v -> vm.updateSettings { it.copy(speechPitch = v) } }
                )
                OutlinedButton(onClick = { vm.speakSample() }) { Text("Test voice") }
                Note("Uses your phone's built-in text-to-speech voice and language. Install more voices in your phone's system TTS settings.")
            }

            Section("AI connection") {
                Text(
                    if (settings.aiConfigured) "Status: configured" else "Status: not configured — Marea will show an error until you add details.",
                    fontSize = 13.sp,
                    color = if (settings.aiConfigured) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                )
                OutlinedTextField(
                    value = settings.baseUrl, onValueChange = { v -> vm.updateSettings { it.copy(baseUrl = v) } },
                    label = { Text("Base URL (https://…)") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = settings.model, onValueChange = { v -> vm.updateSettings { it.copy(model = v) } },
                    label = { Text("Model name") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = settings.apiKey, onValueChange = { v -> vm.updateSettings { it.copy(apiKey = v) } },
                    label = { Text("API key (optional)") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = settings.systemPrompt, onValueChange = { v -> vm.updateSettings { it.copy(systemPrompt = v) } },
                    label = { Text("Personality / system prompt") }, minLines = 2, maxLines = 5, modifier = Modifier.fillMaxWidth()
                )
                Note(
                    "Works with servers that offer a chat-completions style API at {base URL}/chat/completions. " +
                        "Get the exact URL, model name and key from your provider's documentation. " +
                        "The key is stored only on this phone. A key placed inside an APK can be extracted, " +
                        "so for sharing the app use your own HTTPS backend that holds the key."
                )
            }

            Section("Appearance") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = settings.accent == Accent.BURGUNDY,
                        onClick = { vm.updateSettings { it.copy(accent = Accent.BURGUNDY) } },
                        label = { Text("Burgundy") }
                    )
                    FilterChip(
                        selected = settings.accent == Accent.PURPLE,
                        onClick = { vm.updateSettings { it.copy(accent = Accent.PURPLE) } },
                        label = { Text("Purple") }
                    )
                }
                Note("Dark appearance is used throughout.")
            }

            Section("Conversation history") {
                SwitchRow("Save history on this device", settings.saveHistory) { v -> vm.updateSettings { it.copy(saveHistory = v) } }
                Text("Messages in this chat: ${ui.messages.size}", fontSize = 13.sp)
                Note(
                    "History is plain chat text in app-private storage. The latest 200 messages are kept until " +
                        "you clear them or turn this off (turning it off deletes the saved file). " +
                        "It is not long-term AI memory."
                )
                OutlinedButton(onClick = { confirmClear = true }, enabled = ui.messages.isNotEmpty()) {
                    Text("Clear history")
                }
            }

            Section("Privacy") {
                Note(
                    "Stays on your phone: chat history, settings, your API key.\n\n" +
                        "Sent to the AI provider you configure: your typed or recognized messages and recent " +
                        "conversation context, so it can reply.\n\n" +
                        "Speech recognition and speech output are done by your phone's speech services; depending " +
                        "on your device, voice recognition may involve your phone vendor's cloud service.\n\n" +
                        "Marea never records in the background, and asks for the microphone only when you tap the mic."
                )
            }

            Section("Permission status") {
                Text("Microphone: ${if (micGranted) "allowed" else "not allowed"}", fontSize = 14.sp)
                Text("Speech recognition service: ${if (recognitionAvailable) "available" else "not available"}", fontSize = 14.sp)
                Text("Internet: used only for the AI provider you configure", fontSize = 14.sp)
                Button(onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(Uri.fromParts("package", context.packageName, null))
                    )
                }) { Text("Open app permissions") }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear history?") },
            text = { Text("This permanently deletes the conversation stored on this phone.") },
            confirmButton = {
                TextButton(onClick = { vm.clearHistory(); confirmClear = false }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f), fontSize = 14.sp)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Note(text: String) {
    Text(text, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
