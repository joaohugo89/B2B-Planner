# Liga o servidor de motores do Video Studio: voz, sincronia labial e legendas.
#
# O backend fica no Docker, sempre no ar; estes motores rodam no Windows e no WSL, onde o
# contêiner não alcança. O backend pede cada etapa por HTTP (porta 8095, só desta máquina)
# e os dois lados leem e gravam os arquivos na mesma pasta: backend\uploads\video.
#
# Desligado, o planner e o site continuam funcionando; só voz, sincronia e legendas ficam
# indisponíveis no Video Studio. O render (FFmpeg) não depende disto.
#
# Uso:  .\scripts\motores.ps1     (fechar a janela desliga os motores)

$ErrorActionPreference = 'Stop'
$raiz = Split-Path $PSScriptRoot -Parent

$chave = (Get-Content (Join-Path $raiz '.env') | Where-Object { $_ -match '^VIDEO_ENGINE_KEY=' } |
        Select-Object -First 1) -replace '^VIDEO_ENGINE_KEY=', ''
if (-not $chave) { throw 'Falta VIDEO_ENGINE_KEY no .env (a mesma chave que o backend usa).' }

$env:MOTORES_CHAVE = $chave.Trim()
$env:MOTORES_PORTA = '8095'
$env:MOTORES_PASTA = Join-Path $raiz 'backend\uploads\video'
New-Item -ItemType Directory -Force $env:MOTORES_PASTA | Out-Null

# Cada motor recebe os nomes de arquivo relativos à pasta do projeto, que é o diretório
# de trabalho do processo. Aspas agrupam um caminho com espaço.
$env:MOTOR_LEGENDAS = 'C:\engines\transcrever.bat {audio} {out}'
$env:MOTOR_VOZ = 'wsl -d Ubuntu --exec /opt/engines/voz.sh {text} {out}'
wsl -d Ubuntu test -x /opt/engines/lipsync.sh
if ($LASTEXITCODE -eq 0) {
    $env:MOTOR_LIPSYNC = 'wsl -d Ubuntu --exec /opt/engines/lipsync.sh {video} {audio} {out}'
}

# Roda direto do código-fonte (Java 21), sem build: o servidor só usa o JDK.
java (Join-Path $raiz 'backend\src\main\java\br\com\jhcomp\planner\video\motores\ServidorMotores.java')
