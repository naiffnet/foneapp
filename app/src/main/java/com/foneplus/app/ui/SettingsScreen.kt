package com.foneplus.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    role: String,
    keepRunning: Boolean,
    onKeepRunningChange: (Boolean) -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    onRerunSetup: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Configurações", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text("Papel atual: ${if (role == "driver") "Motorista" else "Passageiro"}")
        Spacer(Modifier.height(24.dp))
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = onOpenBluetoothSettings
        ) {
            Text("Trocar fone Bluetooth")
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = onRerunSetup
        ) {
            Text("Reexecutar configuração e teste L/R")
        }
        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Manter comunicação em segundo plano")
                Text("Usa uma notificação persistente", style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = keepRunning, onCheckedChange = onKeepRunningChange)
        }
        Spacer(Modifier.height(28.dp))
        Button(onClick = onBack) {
            Text("Voltar")
        }
    }
}
