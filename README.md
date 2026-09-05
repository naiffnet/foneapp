# Foneplus

Aplicativo Android nativo para comunicação entre motorista e passageiro usando um fone Bluetooth compartilhado.

Consulte a especificação completa em [spec.md](spec.md).
Consulte também a política de privacidade em [PRIVACY.md](PRIVACY.md).

## Estado atual

O MVP ja possui onboarding, teste de canal L/R, permissao contextual de microfone, Foreground Service, Audio Focus opcional e o primeiro pipeline PTT com `AudioRecord`/`AudioTrack`. A validacao em um aparelho fisico continua obrigatoria para confirmar roteamento Bluetooth, latencia e compatibilidade.

## Próximas etapas

1. Testar o pipeline PTT em fones e aparelhos fisicos.
2. Exibir na UI o dispositivo Bluetooth e estados de desconexao.
3. Adicionar captura SCO do passageiro e diagnostico de nivel.
4. Testar o modo maos-livres antes de expor recursos experimentais.

## Build

Abra o projeto no Android Studio com Android SDK 35 instalado e execute a configuração `app` em um dispositivo físico. Bluetooth real não é confiável no emulador para validar este produto.