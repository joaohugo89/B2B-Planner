# 06 — Roadmap

A ordem pedida foi mantida, com um ajuste: a tela do Video Studio (MVP 4) subiu para junto
do MVP 2, porque um pipeline sem tela não é testável por quem vai usar.

## Fase 1 — Estrutura ✅ entregue

- **Objetivo**: o Planner aceitar conteúdo audiovisual sem mudar o que já funciona.
- **Entrega**: pacote `video`, migração `V12`, armazenamento por projeto, fila de jobs,
  limites de upload (Spring e nginx) em 512 MB, FFmpeg na imagem Docker.
- **Banco**: `video_projects`, `video_jobs`.
- **Aceite**: os 106 testes anteriores continuam passando; `/api/video/**` exige login.

## Fase 2 — Pipeline de vídeo ✅ entregue

- **Objetivo**: vídeo-base + áudio → MP4 final, com estado visível.
- **Entrega**: upload de vídeo e áudio, etapa RENDER com FFmpeg, edição de SRT, histórico
  e cancelamento de jobs, tela do Video Studio com acompanhamento a cada 2 s.
- **Depende de**: fase 1 e **FFmpeg instalado** (ou backend na imagem Docker).
- **Aceite**: enviar um mp4 e um wav, renderizar, assistir e baixar o resultado; alterar
  só o SRT e renderizar de novo sem reenviar nada.
- **Risco**: FFmpeg ausente no host — a tela avisa qual etapa está indisponível.

## Fase 3 — Motores de IA 🔶 encaixe entregue, motores por instalar

- **Objetivo**: roteiro, voz, legendas e sincronia automáticos.
- **Entregue**: geração de roteiro pela IA configurada; etapas VOZ, LEGENDAS e LIPSYNC
  prontas para qualquer comando configurado.
- **Falta** (trabalho de instalação, não de código do backend):
  1. Instalar Ollama e apontar o painel para `http://localhost:11434/v1`.
  2. Instalar whisper.cpp e configurar `VIDEO_TRANSCRIBE_COMMAND`.
  3. Instalar Ubuntu no WSL2, criar venv com torch `cu128`, instalar Chatterbox e
     escrever o script de voz; configurar `VIDEO_VOICE_COMMAND`.
  4. Instalar MuseTalk no mesmo molde; configurar `VIDEO_LIPSYNC_COMMAND`.
- **Aceite**: roteiro → voz → sincronia → legendas → render sem enviar arquivo de áudio.
- **Riscos**: compatibilidade do MuseTalk com torch para `sm_120` (alto); licença dos
  pesos do MuseTalk (conferir); qualidade da voz em pt-BR.

## Fase 4 — Experiência integrada 🔶 parcial

- **Entregue**: vínculo projeto ↔ post, lista de vídeos no post.
- **Falta**: criar o projeto a partir do post com o tema já preenchido; templates de
  vídeo (vinheta, logotipo, estilo de legenda) como predefinições do render;
  reorganização da navegação em módulos (03); progresso percentual lido da saída do FFmpeg.
- **Aceite**: do post ao vídeo em uma tela, sem redigitar o tema.

## Fase 5 — Evolução

- Motion graphics (decidir entre Motion Canvas, fork `canvas-commons` e filtros FFmpeg).
- Biblioteca de mídia (`media_assets`) e perfis de voz.
- Campanhas (`campaigns`).
- Analytics: antes de qualquer código, conferir no painel da Meta se o app tem
  `instagram_manage_insights` e quais métricas a Graph API devolve para a conta.
- Publicar vídeo (Reels) pelo Instagram: a publicação atual só trata imagem e carrossel.

## Dívidas fora do Content Studio (01)

JDK 25 × 21 no Dockerfile; testes pulados no build da imagem; testes de frontend.
Independentes, baratas, e valem ser feitas antes da fase 3.
