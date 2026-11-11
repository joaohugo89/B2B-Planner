# Sobe o planner no modo vídeo: um backend só, rodando no Windows, com os motores de IA.
#
# Por que o backend sai do Docker: um contêiner não consegue iniciar programas do Windows
# nem do WSL, e é assim que voz, sincronia labial e transcrição são chamados. O resto
# continua em contêiner: banco (5440), frontend (localhost:8090) e o túnel do domínio,
# que passam a falar com este backend em vez do contêiner "backend".
#
# Uso:  .\scripts\video-local.ps1     (deixe a janela aberta: fechar derruba o backend)
# Voltar ao backend em contêiner: apague a linha MODO_BACKEND=host do .env e rode o
# "docker compose ... up -d --build" de sempre (docs/DOCKER.md).

$ErrorActionPreference = 'Stop'
$raiz = Split-Path $PSScriptRoot -Parent
$envFile = Join-Path $raiz '.env'

# Marca o modo no .env: é por ele que o hook de pre-push sabe não subir o outro backend.
if (-not (Select-String -Path $envFile -Pattern '^MODO_BACKEND=host' -Quiet)) {
    Add-Content -Path $envFile -Value "`nMODO_BACKEND=host" -Encoding utf8
}

# As mesmas chaves do contêiner: com outra JHCOMP_ENCRYPTION_KEY, a chave de IA e o token
# do Instagram gravados no banco deixariam de abrir.
Get-Content $envFile | Where-Object { $_ -match '^\s*[A-Z_]+=' } | ForEach-Object {
    $nome, $valor = $_ -split '=', 2
    Set-Item -Path "Env:$($nome.Trim())" -Value $valor.Trim()
}

$arquivos = @('-f', 'docker-compose.prod.yml', '-f', 'docker-compose.local.yml')
if ($env:CLOUDFLARE_TUNNEL_TOKEN) { $arquivos += @('-f', 'docker-compose.dominio.yml') }
$arquivos += @('-f', 'docker-compose.video.yml')

Push-Location $raiz
try {
    # O contêiner do backend sai de cena: dois backends no mesmo banco disputariam a fila.
    docker rm -f b2bplanner-backend-1 2>$null | Out-Null
    # --build: o frontend em contêiner é uma imagem; sem rebuild ele serve o código antigo.
    docker compose @arquivos --env-file .env up -d --build
    if ($LASTEXITCODE -ne 0) { throw 'O docker compose falhou; veja a mensagem acima.' }
} finally {
    Pop-Location
}

if (-not $env:DB_NAME) { $env:DB_NAME = 'jhcomp_planner' }
$env:DB_URL = "jdbc:postgresql://localhost:5440/$($env:DB_NAME)"
$env:SERVER_PORT = '8081'
# Só na própria máquina: o Docker alcança via host.docker.internal, a rede local não.
$env:SERVER_ADDRESS = '127.0.0.1'
# É este backend que atende o domínio público: valem as travas de produção.
$env:SPRING_PROFILES_ACTIVE = 'prod'

$env:VIDEO_TRANSCRIBE_COMMAND = 'C:\engines\transcrever.bat {audio} {out}'
$env:VIDEO_VOICE_COMMAND = 'wsl -d Ubuntu --exec /opt/engines/voz.sh {text} {out}'
wsl -d Ubuntu test -x /opt/engines/lipsync.sh
if ($LASTEXITCODE -eq 0) {
    $env:VIDEO_LIPSYNC_COMMAND = 'wsl -d Ubuntu --exec /opt/engines/lipsync.sh {video} {audio} {out}'
}

Write-Host ''
Write-Host 'Backend no Windows, em http://localhost:8090 e no domínio. Feche esta janela para parar.' -ForegroundColor Green
Set-Location (Join-Path $raiz 'backend')
mvn -q spring-boot:run
