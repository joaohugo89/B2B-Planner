# Servidor de motores do Video Studio: voz, sincronia labial e legendas.
#
# O backend fica no Docker, sempre no ar; estes motores rodam no Windows e no WSL, onde o
# contêiner não alcança. O backend pede cada etapa por HTTP (porta 8095, só desta máquina)
# e os dois lados leem e gravam os arquivos na mesma pasta: backend\uploads\video.
#
# Parado, o servidor quase não custa nada: cada motor é um processo que nasce no pedido e
# devolve a placa de vídeo ao terminar. Por isso ele pode ficar sempre rodando, escondido,
# e ligar ou desligar é um botão no Video Studio.
#
# Uso:
#   .\scripts\motores.ps1                       numa janela (fechar desliga)
#   .\scripts\motores.ps1 -IniciarAoEntrar      liga sozinho, escondido, ao entrar no Windows
#   .\scripts\motores.ps1 -NaoIniciarAoEntrar   desfaz o de cima
#   .\scripts\motores.ps1 -Oculto               escondido agora, com saída em backend\logs\motores.log

param(
    [switch]$Oculto,
    [switch]$IniciarAoEntrar,
    [switch]$NaoIniciarAoEntrar
)

$ErrorActionPreference = 'Stop'
$raiz = Split-Path $PSScriptRoot -Parent
$atalho = Join-Path ([Environment]::GetFolderPath('Startup')) 'JH.COMP motores.lnk'

if ($NaoIniciarAoEntrar) {
    Remove-Item $atalho -ErrorAction SilentlyContinue
    Write-Host 'Os motores não ligam mais sozinhos ao entrar no Windows.'
    return
}

if ($IniciarAoEntrar) {
    # Um atalho na pasta Inicializar: visível em Configurações > Aplicativos > Inicialização,
    # e apagar o arquivo desfaz.
    $shell = New-Object -ComObject WScript.Shell
    $link = $shell.CreateShortcut($atalho)
    $link.TargetPath = (Get-Command powershell.exe).Source
    $link.Arguments = "-NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File `"$PSCommandPath`" -Oculto"
    $link.WorkingDirectory = $raiz
    $link.WindowStyle = 7
    $link.Save()
    Write-Host "Pronto: os motores ligam sozinhos ao entrar no Windows ($atalho)."
    Write-Host 'Para ligar agora, sem reiniciar:  .\scripts\motores.ps1 -Oculto'
    return
}

# Um só: dois servidores disputariam a porta e a placa de vídeo.
if (Get-NetTCPConnection -LocalPort 8095 -State Listen -ErrorAction SilentlyContinue) {
    Write-Host 'O servidor de motores já está rodando (porta 8095).'
    return
}

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
$fonte = Join-Path $raiz 'backend\src\main\java\br\com\jhcomp\planner\video\motores\ServidorMotores.java'
if ($Oculto) {
    $logs = Join-Path $raiz 'backend\logs'
    New-Item -ItemType Directory -Force $logs | Out-Null
    & java $fonte *>> (Join-Path $logs 'motores.log')
} else {
    java $fonte
}
