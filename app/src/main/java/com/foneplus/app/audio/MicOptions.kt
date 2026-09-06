package com.foneplus.app.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager

/**
 * Representa uma entrada de microfone detectada pelo Android, com um
 * identificador estavel (nao depende do objeto AudioDeviceInfo, que muda
 * de instancia a cada reconexao) para podermos salvar a escolha do usuario
 * no SharedPreferences e reencontrar o dispositivo certo depois.
 */
data class MicOption(
    val id: String,
    val label: String,
    val type: Int
)

object MicOptions {
    /**
     * Gera um id estavel para um AudioDeviceInfo de entrada. Combina tipo +
     * nome do produto, que na pratica se mantem igual entre reconexoes do
     * mesmo fone/celular.
     */
    fun stableId(device: AudioDeviceInfo): String = "${device.type}|${device.productName}"

    fun typeLabel(type: Int): String = when (type) {
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth (chamada)"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth (midia)"
        AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Microfone do celular"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Fone com fio"
        AudioDeviceInfo.TYPE_USB_HEADSET -> "Fone USB"
        else -> "Outro"
    }

    fun list(context: Context): List<MicOption> {
        val audioManager = context.getSystemService(AudioManager::class.java) ?: return emptyList()
        return audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            .map { device ->
                MicOption(
                    id = stableId(device),
                    label = "${typeLabel(device.type)} — ${device.productName}",
                    type = device.type
                )
            }
            .distinctBy { it.id }
    }

    /**
     * Encontra o AudioDeviceInfo atual que corresponde ao id salvo anteriormente.
     * Pode retornar null se o dispositivo nao estiver mais conectado.
     */
    fun findDevice(context: Context, id: String?): AudioDeviceInfo? {
        if (id == null) return null
        val audioManager = context.getSystemService(AudioManager::class.java) ?: return null
        return audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            .firstOrNull { stableId(it) == id }
    }
}
