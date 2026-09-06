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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.foneplus.app.audio.Channel

@Composable
fun SetupScreen(
    onOpenBluetoothSettings: () -> Unit,
    onPlayChannel: (Channel) -> Unit,
    onStopAudio: () -> Unit,
    micLevel: Float,
    activeMicName: String,
    availableMics: List<String>,
    selectedMicIndex: Int,
    onMicSelected: (Int) -> Unit,
    isMicTesting: Boolean,
    onToggleMicTest: (Boolean) -> Unit,
    onComplete: (String, String) -> Unit
) {
    var step by remember { mutableIntStateOf(0) }
    var role by remember { mutableIntStateOf(0) }
    var sideDistribution by remember { mutableIntStateOf(0) } // 0: M(L)/P(R), 1: M(R)/P(L)
    
    val titles = listOf(
        "Conecte seu fone",
        "Confirme os canais",
        "Identifique o microfone",
        "Escolha seu papel",
        "Lado do fone"
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
            }
            2 -> {
                Text("Selecione qual microfone deseja habilitar para o teste.")
                Spacer(Modifier.height(16.dp))
                
                availableMics.forEachIndexed { index, mic ->
                    val isSelected = selectedMicIndex == index
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { onMicSelected(index) }
                        )
                        Text(
                            text = mic,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { onToggleMicTest(!isMicTesting) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isMicTesting) "Parar Captura" else "Iniciar Captura (Habilitar)")
                }

                if (isMicTesting) {
                    Spacer(Modifier.height(20.dp))
                    Text("Nível do som no microfone habilitado:", style = MaterialTheme.typography.labelMedium)
                    LinearProgressIndicator(
                        progress = { micLevel },
                        modifier = Modifier.fillMaxWidth().height(12.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Dica: Com o microfone Bluetooth habilitado, o lado do fone que fizer a barra subir é onde está o microfone físico.", style = MaterialTheme.typography.bodySmall)
                }
            }
            3 -> {
                Text("Defina seu papel para preparar os controles da central de comunicação.")
                Spacer(Modifier.height(12.dp))
                OptionRow("Motorista", role == 0) { role = 0 }
                OptionRow("Passageiro", role == 1) { role = 1 }
            }
            4 -> {
                Text("Quem vai usar qual lado do fone compartilhado?")
                Spacer(Modifier.height(12.dp))
                OptionRow("Motorista (Esquerdo) / Passageiro (Direito)", sideDistribution == 0) { sideDistribution = 0 }
                OptionRow("Motorista (Direito) / Passageiro (Esquerdo)", sideDistribution == 1) { sideDistribution = 1 }
            }
        }

        Spacer(Modifier.height(28.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(onClick = {
                onStopAudio()
                onToggleMicTest(false)
                if (step == titles.lastIndex) {
                    val roleStr = if (role == 0) "driver" else "passenger"
                    val distStr = if (sideDistribution == 0) "L-R" else "R-L"
                    onComplete(roleStr, distStr)
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
private fun OptionRow(label: String, selected: Boolean, onSelected: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelected)
        Text(label)
    }
}
