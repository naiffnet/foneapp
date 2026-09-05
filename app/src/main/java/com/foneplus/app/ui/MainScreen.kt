package com.foneplus.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MainScreen(
    connectionStatus: String,
    isTalking: Boolean,
    duckingEnabled: Boolean,
    onDuckingChange: (Boolean) -> Unit,
    handsFreeEnabled: Boolean,
    onHandsFreeChange: (Boolean) -> Unit,
    speechSensitivity: Float,
    onSpeechSensitivityChange: (Float) -> Unit,
    leAudioSupported: Boolean,
    onOpenSettings: () -> Unit,
    onTalkingChange: (Boolean) -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Foneplus", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (isTalking) "Transmitindo sua voz" else "Pronto para conectar",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.height(32.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.large
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("Fone Bluetooth", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(connectionStatus)
            }
        }
        Spacer(Modifier.height(32.dp))
        Button(
            modifier = Modifier.size(width = 220.dp, height = 120.dp),
            onClick = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                onTalkingChange(!isTalking)
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isTalking) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
        ) {
            Text(if (isTalking) "Soltar para parar" else "Segure para falar")
        }
        Spacer(Modifier.height(28.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Modo maos-livres")
                Text("Experimental: dois microfones", style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = handsFreeEnabled, onCheckedChange = onHandsFreeChange)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = if (leAudioSupported) {
                "LE Audio detectado; modo de dois fones disponivel para testes."
            } else {
                "Dois fones separados: suporte experimental nao detectado."
            },
            style = MaterialTheme.typography.bodySmall
        )
        if (handsFreeEnabled) {
            Text("Sensibilidade do detector de fala")
            Slider(
                value = speechSensitivity,
                onValueChange = onSpeechSensitivityChange,
                valueRange = 200f..2_000f
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Abaixar música durante a fala")
                Text("Audio Focus", style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = duckingEnabled, onCheckedChange = onDuckingChange)
        }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onOpenSettings) {
            Text("Configurações")
        }
    }
}