# Foneplus

## Especificacao tecnica

Versao 0.1 - App de comunicacao motorista/passageiro via Bluetooth.

> Documento de arquitetura, viabilidade, roadmap e estado de implementacao.

## 1. Objetivo

Permitir que motorista e passageiro se comuniquem dentro do mesmo veiculo usando um smartphone e um fone Bluetooth, com foco inicial em um fone estereo compartilhado.

O produto deve priorizar o modo Push-to-talk (PTT), por ser mais previsivel em diferentes aparelhos Android. O modo maos-livres sera desenvolvido depois da validacao do pipeline de audio.

## 2. Veredito de viabilidade

| Funcionalidade | Status tecnico | Observacao |
|---|---|---|
| Separar saida em L/R de um fone | Viavel | Usar `AudioTrack` ou processamento PCM estereo. Validar em hardware real. |
| Capturar microfone do celular | Viavel | Fluxo normal com `AudioRecord`. |
| Capturar microfone Bluetooth SCO | Viavel com ressalvas | Qualidade e comportamento dependem do aparelho e do fone. |
| Identificar automaticamente a capsula com microfone | Nao disponivel | O Android nao expoe esse mapeamento; o teste precisa ser manual. |
| Testar qual canal e esquerdo/direito | Viavel | Tom de teste e confirmacao do usuario. |
| Dois fones com audios diferentes | Experimental | Depende de Dual Audio ou LE Audio; nao e recurso principal. |
| Dois microfones Bluetooth simultaneos | Praticamente inviavel | O modelo padrao do Android suporta uma conexao SCO ativa. |
| Execucao em segundo plano | Viavel | Usar Foreground Service com tipo de microfone. |
| Reduzir volume de outros apps | Viavel por cooperacao | Usar Audio Focus com ducking. |
| Silenciar outro app a forca | Nao permitido | O sandbox do Android impede controle direto sobre outro app. |

## 3. Arquitetura

### MVP: modo PTT com um fone

1. O usuario segura o botao PTT.
2. O app solicita ou verifica `RECORD_AUDIO`.
3. O microfone selecionado captura a fala.
4. O processamento prepara o audio PCM.
5. O `AudioTrack` reproduz a fala no canal configurado do fone.
6. Ao soltar o botao, a captura e interrompida.

### Modo avancado: maos-livres

- Captura do microfone do celular para a fala do motorista.
- Captura do microfone SCO para a fala do passageiro.
- AEC e Noise Suppression em cada sessao.
- Mixagem dos sinais em canais L/R.
- Watchdog para desconexao e reconexao Bluetooth.

O modo avancado somente deve ser ativado depois de o PTT funcionar em aparelhos fisicos.

## 4. Stack

- Linguagem: Kotlin
- Interface: Jetpack Compose
- Audio: `AudioRecord` e `AudioTrack`
- Efeitos: `AcousticEchoCanceler` e `NoiseSuppressor`
- Roteamento: `AudioDeviceInfo`, `setPreferredDevice()` e `setCommunicationDevice()` quando disponivel
- Segundo plano: Foreground Service
- Configuracao local: preferencias ou DataStore
- Testes finais: aparelhos fisicos; o emulador nao valida Bluetooth real

## 5. Telas

### Onboarding

- Abrir configuracoes Bluetooth.
- Confirmar canais esquerdo e direito.
- Identificar manualmente o fone com microfone.
- Escolher motorista ou passageiro.
- Concluir e salvar a configuracao local.

### Central de comunicacao

- Status do fone.
- Estado de transmissao.
- Botao PTT.
- Toggle de Audio Focus/ducking.
- Indicador futuro de nivel de audio.
- Mute e diagnostico futuro.

### Configuracoes

- Reexecutar teste de canal.
- Trocar fone associado.
- Sensibilidade de ganho/AEC.
- Manter servico em segundo plano.
- Modo experimental de dois fones, apenas quando houver suporte detectado.

### Diagnostico

Mensagens acionaveis para permissao negada, Bluetooth desligado, fone desconectado e falha de SCO.

## 6. Pipeline de audio

### PTT inicial

O primeiro pipeline deve usar uma unica fonte por vez para reduzir eco e conflitos de roteamento:

- PTT do motorista: captura do microfone do celular e reproducao no canal configurado do fone.
- PTT do passageiro: captura SCO e reproducao no alto-falante do celular ou canal configurado.
- Ao parar a fala, liberar ou pausar a sessao de captura.

### Maos-livres posterior

- Criar `AudioRecord` para o microfone do celular.
- Criar `AudioRecord` para `TYPE_BLUETOOTH_SCO` quando suportado.
- Aplicar AEC/NS por sessao.
- Mixar PCM em `AudioTrack` estereo.
- Medir latencia de ida e volta; alvo inicial inferior a 200 ms.
- Tratar perda de SCO sem travar a interface.

## 7. Permissoes

Declaradas no manifesto:

- `RECORD_AUDIO`
- `BLUETOOTH_CONNECT`
- `BLUETOOTH_SCAN`
- `MODIFY_AUDIO_SETTINGS`
- `FOREGROUND_SERVICE`
- `FOREGROUND_SERVICE_MICROPHONE`
- `FOREGROUND_SERVICE_MEDIA_PLAYBACK`
- `POST_NOTIFICATIONS`

As permissoes perigosas devem ser solicitadas em contexto. O app ja solicita `RECORD_AUDIO` quando o usuario inicia o PTT.

## 8. Roadmap

### Fase 0 - Validacao de hardware

- [x] Criar teste de tom PCM separado nos canais L/R.
- [ ] Testar separacao L/R em pelo menos tres fones.
- [ ] Testar captura SCO em aparelhos variados.
- [ ] Mapear Dual Audio e LE Audio.
- [x] Criar prova de conceito de tom L/R.

### Fase 1 - MVP PTT

- [x] Criar projeto Kotlin/Compose.
- [x] Criar tela inicial do PTT.
- [x] Solicitar permissao de microfone ao iniciar transmissao.
- [x] Criar onboarding inicial e persistir conclusao/papel.
- [ ] Implementar descoberta e estado visual real do Bluetooth.
- [x] Implementar primeiro caminho `AudioRecord`/`AudioTrack` para PTT.
- [x] Criar Foreground Service com notificacao persistente.
- [x] Adicionar Audio Focus com ducking opcional.
- [x] Adicionar tratamento de desconexao Bluetooth no servico.
- [ ] Validar em carro ou moto.

### Fase 2 - Maos-livres

- [x] Dois fluxos de captura.
- [x] AEC e Noise Suppression.
- [x] Mixagem L/R.
- [x] Diagnostico de desconexao SCO e liberacao segura do pipeline.
- [ ] Testes de eco e latencia.

### Fase 3 - Segundo plano e ducking

- [x] Audio Focus real durante fala.
- [x] Notificacao persistente de controle.
- [ ] Testes com Spotify, YouTube Music e Waze.
- [x] Detector de fala por limiar e sensibilidade configuravel em runtime.

### Fase 4 - Dois fones experimental

- [x] Detectar suporte de hardware LE Audio.
- [ ] Pesquisar LE Audio e streams distintos.
- [x] Exibir aviso de recurso experimental.
- [ ] Fallback para um fone ou mesmo audio nos dois.

### Fase 5 - Resiliencia e polimento

- [x] Estados de permissao, Bluetooth desconectado e falha de inicializacao visiveis.
- [ ] Testes Samsung, Motorola e Xiaomi.
- [ ] Medicao de bateria.
- [x] Feedback haptico e botoes grandes.
- [x] Configuracoes persistidas para papel, segundo plano e sensibilidade.

### Fase 6 - Distribuicao

- [x] Icone e identidade visual inicial.
- [x] Politica de privacidade inicial.
- [ ] Revisao das politicas da Play Store para microfone em segundo plano.
- [ ] Publicacao como app de comunicacao/utilidade.

## 9. Riscos e mitigacoes

| Risco | Mitigacao |
|---|---|
| SCO inconsistente entre fabricantes | Testar cedo em hardware e manter fallback PTT. |
| Eco no modo maos-livres | AEC/NS, ajuste de ganho e fallback automatico para PTT. |
| Expectativa de dois fones separados | Marcar como experimental e nunca prometer compatibilidade geral. |
| Consumo de bateria | Foreground Service controlado e modo de economia. |
| Apps que ignoram Audio Focus | Documentar que ducking depende da cooperacao do outro app. |

## 10. Definition of Done do MVP

1. Dois usuarios conseguem se ouvir em um veiculo usando um fone Bluetooth.
2. O app continua funcional com tela apagada e troca de aplicativo.
3. Musica/navegacao reduz volume durante a fala quando o app respeita Audio Focus.
4. O onboarding e concluido em menos de dois minutos sem explicacao externa.
5. Desconexao, permissao negada e Bluetooth desligado exibem mensagens acionaveis.
6. O comportamento foi validado em aparelhos fisicos, nao apenas no emulador.

## Estado atual do repositorio

A fundacao visual e de fluxo esta criada, mas o produto ainda nao atende todos os criterios do MVP de audio.

| Area | Estado |
|---|---|
| Projeto Kotlin/Compose | Implementado |
| Onboarding basico | Implementado |
| Abertura das configuracoes Bluetooth | Implementado |
| Persistencia de conclusao e papel | Implementado |
| Tela PTT | Implementada e conectada ao servico |
| Permissao runtime de microfone | Implementado |
| Bluetooth real e estado de conexao | Estado diagnostico implementado; validacao fisica pendente |
| `AudioRecord`/`AudioTrack` PTT | Implementado; requer teste fisico |
| Foreground Service | Implementado |
| Audio Focus real | Implementado no caminho PTT |
| Teste L/R com audio | Implementado; confirmacao do ouvido e manual |
| Testes em aparelhos fisicos | Pendente |
| Modo maos-livres | Implementado; requer testes de eco/latencia |
| Modo dois fones | Detecao e aviso implementados; streams separados ainda dependem de suporte/API |

Portanto, as dez secoes da especificacao estao registradas e as fases 1 a 6 possuem implementacao de codigo ou preparacao correspondente. Permanecem dependentes de validacao externa os testes em aparelhos, compatibilidade entre fabricantes, medicao de bateria, revisao da Play Store e publicacao.
