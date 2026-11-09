# 10 — Backlog

Prioridade: P0 bloqueia o uso, P1 é o próximo passo natural, P2 é evolução.
"Pronto" significa: testes passando, verificado na tela, documentado aqui.

## Épico A — Estrutura e pipeline ✅

| Item | Estado |
|---|---|
| A1 Migração `V12` (`video_projects`, `video_jobs`) | feito |
| A2 `VideoStorage` com lista de extensões e pasta por projeto | feito |
| A3 `EngineRunner` (processo, tempo limite, cancelamento, log) | feito |
| A4 Fila de uma thread + destrave de órfãos no boot | feito |
| A5 Etapa RENDER com FFmpeg | feito |
| A6 API `/api/video` | feito |
| A7 Tela do Video Studio | feito |
| A8 FFmpeg na imagem Docker; limites de upload | feito |

## Épico B — Motores de IA (P0 para o fluxo automático)

- **B1** Como criador, quero gerar a voz do roteiro sem gravar áudio.
  - Instalar Ubuntu/WSL2, venv com torch `cu128`, Chatterbox.
  - Escrever `voz.sh`/`voz.py`; gravar o áudio de referência; `VIDEO_VOICE_COMMAND`.
  - Pronto: job VOZ conclui e o áudio soa como a referência em pt-BR.
- **B2** Como criador, quero meu vídeo-base falando o áudio gerado.
  - Instalar MuseTalk; resolver `mmcv`/`mmpose` para torch recente (risco alto).
  - **Conferir a licença dos pesos** na fonte oficial; `VIDEO_LIPSYNC_COMMAND`.
  - Pronto: job LIPSYNC conclui com pico de VRAM abaixo de 11 GB.
- **B3** Como criador, quero legendas automáticas.
  - Instalar whisper.cpp + modelo; `transcrever.bat`; `VIDEO_TRANSCRIBE_COMMAND`.
  - Pronto: SRT em português com tempos coerentes.
- **B4** Roteiro local: instalar Ollama, configurar o painel.
  - Tarefa de código: aceitar provedor sem chave quando a URL base for local (01, item 3).
- **B5** Medir tempos e VRAM por etapa e atualizar 05.

## Épico C — Experiência (P1)

- **C1** Botão "Produzir vídeo" no post: cria o projeto vinculado e já pede o roteiro com
  o título e o resumo do post como tema.
- **C2** Predefinições de render: logotipo sobreposto, estilo de legenda, vinheta.
- **C3** Progresso percentual lendo `time=` da saída do FFmpeg.
- **C4** Navegação agrupada por módulo (03).
- **C5** Baixar o SRT e o áudio pela tela.

## Épico D — Dívidas (P1, independentes)

- **D1** Alinhar o JDK do Dockerfile ao `pom.xml`.
- **D2** Rodar os testes no build da imagem (ou corrigir o comentário do hook).
- **D3** Vitest no frontend, começando por `assetUrl.ts`.

## Épico E — Evolução (P2)

- **E1** Biblioteca de mídia e perfis de voz.
- **E2** Campanhas.
- **E3** Analytics, após conferir permissões de insights na Meta.
- **E4** Publicar Reels pelo Instagram.
- **E5** Motion graphics.
- **E6** Adaptador HTTP para motores (backend em contêiner + GPU no host).

## Primeira tarefa a executar

**B3 (whisper.cpp)**: é o motor mais simples — um executável, sem Python, sem GPU
obrigatória —, e valida de ponta a ponta o contrato de comando, o script intermediário e
a tela, antes de gastar tempo com o ambiente de GPU de B1 e B2. Pré-requisito de tudo:
`winget install Gyan.FFmpeg`.
