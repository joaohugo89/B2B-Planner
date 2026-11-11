# 08 — Implantação e operação

## Onde cada peça roda

O backend fica sempre no Docker. Voz, sincronia e legendas rodam no Windows, num servidor
de motores (`scripts\motores.ps1`, porta 8095 só desta máquina) que o backend chama por HTTP
em `http://host.docker.internal:8095`. É o adaptador HTTP previsto em 03.

- O servidor de motores não tem regra de negócio nem banco: recebe "rode a etapa X do
  projeto N com estes arquivos", executa o comando configurado para X e informa o andamento.
  Fila, histórico, progresso e cancelamento continuam no backend.
- Os arquivos não viajam pela rede: os uploads do backend são uma pasta do Windows
  (`backend\uploads`, montada no contêiner), e o servidor lê e grava em `backend\uploads\video`.
- O servidor só aceita os três motores configurados, nomes de arquivo no formato que o
  backend gera e entradas que existem na pasta do projeto, e exige a chave `VIDEO_ENGINE_KEY`.
- Um motor por vez (a GPU não comporta dois); um job passa de 2 h e é encerrado, mesmo que o
  backend tenha sumido no meio.
- Desligado, só essas três etapas ficam indisponíveis; o render (FFmpeg) está na imagem.

Sem `VIDEO_ENGINE_URL`, o backend roda os motores ele mesmo, pelos comandos
`VIDEO_*_COMMAND` — útil quando o backend roda fora do Docker.

## Configuração

| Variável | Padrão | Para quê |
|---|---|---|
| `VIDEO_ENGINE_URL` | vazio | Servidor de motores; com o backend no Docker, `http://host.docker.internal:8095` |
| `VIDEO_ENGINE_KEY` | vazio | Chave compartilhada com o servidor de motores (a mesma nos dois lados) |
| `VIDEO_DIR` | `uploads/video` | Pasta dos projetos |
| `FFMPEG_PATH` | `ffmpeg` | Executável do FFmpeg |
| `VIDEO_STEP_TIMEOUT_MINUTES` | `60` | Tempo máximo de uma etapa |
| `VIDEO_VOICE_COMMAND` | vazio | Motor de voz: `{text}` → `{out}` |
| `VIDEO_LIPSYNC_COMMAND` | vazio | Sincronia: `{video}` + `{audio}` → `{out}` |
| `VIDEO_TRANSCRIBE_COMMAND` | vazio | Transcrição: `{audio}` → `{out}` |

Comando vazio = etapa desligada; a tela mostra isso e oferece o caminho manual. O modelo é
separado por espaços, sem aspas: um caminho com espaço precisa de um script intermediário.
Os arquivos são passados como nomes relativos à pasta do projeto, que é o diretório de
trabalho do processo.

## Windows

```
winget install Gyan.FFmpeg
winget install Ollama.Ollama
```

whisper.cpp, com o modelo baixado uma vez (`-of` recebe o nome sem extensão e o programa
acrescenta `.srt`, daí o script):

```bat
:: C:\engines\transcrever.bat  — VIDEO_TRANSCRIBE_COMMAND=C:\engines\transcrever.bat {audio} {out}
@echo off
C:\engines\whisper\whisper-cli.exe -m C:\engines\whisper\ggml-large-v3.bin -l pt -osrt -of "%~dpn2" "%~1"
```

## WSL2 (motores em Python)

```
wsl --install -d Ubuntu
```

Dentro do Ubuntu, um venv por motor, sempre com o torch para a série 50:

```bash
python3 -m venv /opt/engines/voz && . /opt/engines/voz/bin/activate
pip install torch torchaudio --index-url https://download.pytorch.org/whl/cu128
pip install chatterbox-tts
```

O script recebe caminhos do Windows; `wslpath` converte. A pasta do projeto chega como
diretório de trabalho, então os nomes relativos funcionam direto:

```bash
#!/bin/sh
# /opt/engines/voz.sh — VIDEO_VOICE_COMMAND=wsl -d Ubuntu /opt/engines/voz.sh {text} {out}
. /opt/engines/voz/bin/activate
exec python /opt/engines/voz.py "$1" "$2" /opt/engines/minha-voz.wav
```

`voz.py` são cerca de dez linhas que leem o texto, chamam o modelo com o áudio de
referência e gravam o WAV. É o único Python do sistema, e não contém regra de negócio:
não lê banco, não conhece projeto nem usuário. Estes scripts são exemplos a validar na
fase 3 — não foram executados.

## Limites de upload

Spring e nginx: 512 MB. **O Cloudflare Tunnel recusa corpo acima de 100 MB no plano
gratuito** — envio de vídeo maior que isso só funciona pelo endereço local
(`localhost:8090`), não por `planner.joaohugo.com`.

## Logs

Erros HTTP: `logs/erros-AAAA-MM-DD.log` (já existia). Saída de cada motor: os últimos
8 000 caracteres ficam em `video_jobs.log`, visíveis na tela. O relatório de boot
(`StartupCheck`) lista quais motores estão configurados.

## Backup

`scripts/backup.ps1` copia o banco e toda a pasta `backend/uploads` — inclusive
`uploads/video`, que pode passar de dezenas de gigabytes. Se o backup ficar grande demais, os
arquivos `lipsync-*`, `audio-*` e `subtitles-*` são regeneráveis pelos jobs; o que não volta é
o vídeo-base, o avatar (`uploads/video/avatar`) e os `final-*.mp4`.

## Espaço em disco

Substituir um arquivo apaga o anterior, e apagar o projeto apaga a pasta. Não há cota nem
limpeza automática por idade: com 375 GB livres e projetos de centenas de megabytes, o
controle manual pela tela basta por ora. Vale revisitar quando a pasta passar de ~50 GB.
