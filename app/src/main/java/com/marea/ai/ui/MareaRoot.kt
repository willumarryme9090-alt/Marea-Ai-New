package com.marea.ai.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.marea.ai.ChatViewModel
import com.marea.ai.data.AppSettings

@Composable
fun MareaRoot(vm: ChatViewModel, settings: AppSettings) {
    MareaTheme(settings.accent) {
        var showSettings by rememberSaveable { mutableStateOf(false) }
        BackHandler(enabled = showSettings) { showSettings = false }
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (showSettings) {
                SettingsScreen(vm = vm, settings = settings, onBack = { showSettings = false })
            } else {
                MainScreen(vm = vm, settings = settings, onOpenSettings = { showSettings = true })
            }
        }
    }
}
