package com.foneplus.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.foneplus.app.audio.MicOption

@Composable
fun SettingsScreen(
    role: String,
    keepRunning: Boolean,
    onKeepRunningChange: (Boolean) -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    onRerunSetup: () -> Unit,
    onBack: () -> Unit,
    micOptions: List<MicOption>,
    selectedPassengerMicId: String?,
    onSelectPassengerMic: (String?) -> Unit,
    onRefreshMicOptions: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Top
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
        HorizontalDivider()
        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Microfone do passageiro", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = onRefreshMicOptions) {
                Text("Atualizar")
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Escolha qual entrada de microfone detectada pelo celular deve ser usada " +
                "para captar a voz do passageiro. Ligue apenas uma opção — ligar uma " +
                "desliga automaticamente as demais.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(12.dp))

        if (micOptions.isEmpty()) {
            Text(
                "Nenhum microfone detectado. Toque em Atualizar com o fone Bluetooth conectado.",
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            micOptions.forEach { option ->
                val isSelected = option.id == selectedPassengerMicId
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = option.label,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = isSelected,
                        onCheckedChange = { turnedOn ->
                            // So uma opcao pode ficar ativa por vez: ligar uma
                            // seleciona ela; desligar a que ja estava ativa
                            // volta para selecao automatica (null).
                            onSelectPassengerMic(if (turnedOn) option.id else null)
                        }
                    )
                }
            }
        }
        Spacer(Modifier.height(28.dp))
        Button(onClick = onBack) {
            Text("Voltar")
        }
    }
}
