# Servidor de motores do Video Studio: voz, sincronia labial e legendas.
#
# O backend fica no Docker; estes motores rodam no Windows e no WSL, onde o contêiner não
# alcança. O backend pede cada etapa por HTTP (porta 8095, só desta máquina) e os dois lados
# leem e gravam os arquivos na mesma pasta: backend\uploads\video.
#
# Os motores acompanham o contêiner do planner: ligam quando ele está rodando e se desligam
# sozinhos quando ele para (ou o Docker Desktop fecha), devolvendo a memória. Ligar e desligar com o
# Docker aberto é o botão "Motores de IA" do Video Studio.
#
# Uso:
#   .\scripts\motores.ps1 -IniciarAoEntrar      liga e desliga junto com o Docker, sem janela
#   .\scripts\motores.ps1 -NaoIniciarAoEntrar   desfaz o de cima
#   .\scripts\motores.ps1 -Oculto               liga agora, sem janela, se o Docker estiver no ar
#   .\scripts\motores.ps1                       numa janela, para ver a saída (fechar desliga)

param(
    [switch]$Oculto,
    [switch]$IniciarAoEntrar,
    [switch]$NaoIniciarAoEntrar
)

$ErrorActionPreference = 'Stop'
$raiz = Split-Path $PSScriptRoot -Parent
$tarefa = 'JH.COMP motores'
$conteiner = 'b2bplanner-backend-1'

function EstaOuvindo([int]$porta) {
    [bool](Get-NetTCPConnection -LocalPort $porta -State Listen -ErrorAction SilentlyContinue)
}

# Pergunta ao Docker pelo contêiner do planner, e não "alguém responde na 8081?": outro
# backend nessa porta religaria os motores à toa. Docker fechado também dá "não".
function PlannerRodando {
    $estado = docker inspect -f '{{.State.Running}}' $conteiner 2>$null
    $LASTEXITCODE -eq 0 -and "$estado".Trim() -eq 'true'
}

if ($NaoIniciarAoEntrar -or $IniciarAoEntrar) {
    Unregister-ScheduledTask -TaskName $tarefa -Confirm:$false -ErrorAction SilentlyContinue
    # Versão anterior: um atalho na pasta Inicializar.
    Remove-Item (Join-Path ([Environment]::GetFolderPath('Startup')) 'JH.COMP motores.lnk') -ErrorAction SilentlyContinue
}

if ($NaoIniciarAoEntrar) {
    Write-Host 'Os motores não ligam mais sozinhos.'
    return
}

if ($IniciarAoEntrar) {
    # Uma checagem a cada 2 minutos: se o Docker está no ar e os motores não, liga. Quem
    # desliga é o próprio servidor, ao perceber que o backend sumiu. conhost --headless
    # roda o PowerShell sem abrir nem piscar janela.
    $acao = New-ScheduledTaskAction -Execute 'conhost.exe' -WorkingDirectory $raiz `
        -Argument "--headless powershell.exe -NoProfile -ExecutionPolicy Bypass -File `"$PSCommandPath`" -Oculto"
    $gatilhos = @(
        (New-ScheduledTaskTrigger -AtLogOn -User $env:USERNAME),
        (New-ScheduledTaskTrigger -Once -At (Get-Date) -RepetitionInterval (New-TimeSpan -Minutes 2))
    )
    $ajustes = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries `
        -MultipleInstances IgnoreNew -ExecutionTimeLimit (New-TimeSpan -Minutes 5)
    Register-ScheduledTask -TaskName $tarefa -Action $acao -Trigger $gatilhos -Settings $ajustes `
        -Description 'Liga os motores do Video Studio quando o Docker está no ar (scripts\motores.ps1).' | Out-Null
    Write-Host "Pronto: os motores ligam em até 2 minutos depois que o Docker sobe e se desligam quando ele fecha."
    Write-Host "Para conferir ou remover: Agendador de Tarefas > '$tarefa', ou .\scripts\motores.ps1 -NaoIniciarAoEntrar"
    Start-ScheduledTask -TaskName $tarefa
    return
}

# Um só: dois servidores disputariam a porta e a placa de vídeo.
if (EstaOuvindo 8095) {
    if (-not $Oculto) { Write-Host 'O servidor de motores já está rodando (porta 8095).' }
    return
}
# Sem o backend do planner, não há quem chame os motores: ligar agora só prenderia memória.
if ($Oculto -and -not (PlannerRodando)) {
    return
}

$chave = (Get-Content (Join-Path $raiz '.env') | Where-Object { $_ -match '^VIDEO_ENGINE_KEY=' } |
        Select-Object -First 1) -replace '^VIDEO_ENGINE_KEY=', ''
if (-not $chave) { throw 'Falta VIDEO_ENGINE_KEY no .env (a mesma chave que o backend usa).' }

$env:MOTORES_CHAVE = $chave.Trim()
$env:MOTORES_PORTA = '8095'
$env:MOTORES_PASTA = Join-Path $raiz 'backend\uploads\video'
$env:MOTORES_CONTEINER = $conteiner
New-Item -ItemType Directory -Force $env:MOTORES_PASTA | Out-Null

# Cada motor recebe os nomes de arquivo relativos à pasta do projeto, que é o diretório
# de trabalho do processo. Aspas agrupam um caminho com espaço.
$env:MOTOR_LEGENDAS = 'C:\engines\transcrever.bat {audio} {out}'
$env:MOTOR_VOZ = 'wsl -d Ubuntu --exec /opt/engines/voz.sh {text} {out}'
$env:MOTOR_LIPSYNC = 'wsl -d Ubuntu --exec /opt/engines/lipsync.sh {video} {audio} {out}'

# Compilado uma vez e de novo só quando o arquivo muda: rodar direto do fonte carregaria o
# compilador inteiro na memória (122 MB contra 47 MB, medido). O servidor só usa o JDK.
$fonte = Join-Path $raiz 'backend\src\main\java\br\com\jhcomp\planner\video\motores\ServidorMotores.java'
$classes = Join-Path $raiz 'backend\target\motores'
$compilado = Join-Path $classes 'br\com\jhcomp\planner\video\motores\ServidorMotores.class'
if (-not (Test-Path $compilado) -or (Get-Item $fonte).LastWriteTime -gt (Get-Item $compilado).LastWriteTime) {
    javac -d $classes $fonte
    if ($LASTEXITCODE -ne 0) { throw 'Não foi possível compilar o servidor de motores.' }
}
# Heap pequeno e um compilador JIT só: o servidor não faz conta, só repassa pedidos.
$jvm = "-Xmx16m -Xss256k -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -XX:CICompilerCount=1 " +
       "-XX:ReservedCodeCacheSize=8m -XX:MaxMetaspaceSize=32m -cp `"$classes`" br.com.jhcomp.planner.video.motores.ServidorMotores"
if ($Oculto) {
    $logs = Join-Path $raiz 'backend\logs'
    New-Item -ItemType Directory -Force $logs | Out-Null
    # O java segue sozinho e este PowerShell termina: nada além do servidor fica na memória.
    Start-Process java -ArgumentList $jvm -WindowStyle Hidden `
        -RedirectStandardOutput (Join-Path $logs 'motores.log') `
        -RedirectStandardError (Join-Path $logs 'motores-erros.log')
} else {
    Start-Process java -ArgumentList $jvm -NoNewWindow -Wait
}
