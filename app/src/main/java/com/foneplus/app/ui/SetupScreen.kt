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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.foneplus.app.audio.Channel

@Composable
fun SetupScreen(
    onOpenBluetoothSettings: () -> Unit,
    onPlayChannel: (Channel) -> Unit,
    onComplete: (String) -> Unit
) {
    var step by remember { mutableIntStateOf(0) }
    var role by remember { mutableIntStateOf(0) }
    val titles = listOf(
        "Conecte seu fone",
        "Confirme os canais",
        "Identifique o microfone",
        "Escolha seu papel"
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Configuração rápida", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text("Etapa ${step + 1} de ${titles.size}", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(24.dp))
        Text(titles[step], style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))

        when (step) {
            0 -> {
                Text("Conecte o fone ao celular nas configurações do Android.")
                Spacer(Modifier.height(20.dp))
                OutlinedButton(onClick = onOpenBluetoothSettings) {
                    Text("Abrir Bluetooth")
                }
            }
            1 -> {
                Text("Toque em cada botão e confirme em qual ouvido o tom foi reproduzido.")
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { onPlayChannel(Channel.LEFT) }) {
                        Text("Testar L")
                    }
                    OutlinedButton(onClick = { onPlayChannel(Channel.RIGHT) }) {
                        Text("Testar R")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("A identificacao do ouvido e confirmada manualmente pelo usuario.")
            }
            2 -> Text("Fale perto do microfone do fone. A confirmação é manual porque o Android não informa qual cápsula física possui microfone.")
            3 -> {
                Text("Defina seu papel para preparar os controles da central de comunicação.")
                Spacer(Modifier.height(12.dp))
                RoleOption("Motorista", role == 0) { role = 0 }
                RoleOption("Passageiro", role == 1) { role = 1 }
            }
        }

        Spacer(Modifier.height(28.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(onClick = {
                if (step == titles.lastIndex) {
                    onComplete(if (role == 0) "driver" else "passenger")
                } else {
                    step++
                }
            }) {
                Text(if (step == titles.lastIndex) "Concluir" else "Continuar")
            }
        }
    }
}

@Composable
private fun RoleOption(label: String, selected: Boolean, onSelected: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelected)
        Text(label)
    }
}