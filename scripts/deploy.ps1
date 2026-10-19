<#
    Deploy do JH.COMP Planner num comando. Em ordem, parando no primeiro erro:

      1. valida as variáveis de ambiente obrigatórias em produção;
      2. roda 'mvn test' no backend;
      3. roda 'npm run build' no frontend;
      4. constrói as duas imagens Docker.

    Uso:
      .\scripts\deploy.ps1
      .\scripts\deploy.ps1 -Tag v1.2.0
      .\scripts\deploy.ps1 -EnvFile .env            # carrega as variáveis de um arquivo
      .\scripts\deploy.ps1 -PularTestes             # pula mvn test e npm run build
      .\scripts\deploy.ps1 -SomenteValidar          # só a etapa 1

    As variáveis podem vir do ambiente da sessão ou de um arquivo passado em -EnvFile
    (formato CHAVE=valor, uma por linha; linhas começando com # são ignoradas).

    Docker: se o comando 'docker' não existir na máquina, o script AVISA e termina com
    as etapas 1-3 feitas, em vez de falhar. Veja a nota no README (seção 5.4): a etapa de
    build das imagens ainda não foi exercitada nesta máquina.
#>
param(
    [string]$Tag = 'latest',
    [string]$EnvFile,
    [switch]$PularTestes,
    [switch]$SomenteValidar
)

$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot

# Variáveis sem as quais o backend não sobe (ou sobe inseguro) em produção.
$obrigatorias = @(
    @{ Nome = 'SPRING_PROFILES_ACTIVE'; Para = "Perfil do Spring; em produção tem de ser 'prod'" }
    @{ Nome = 'DB_URL'; Para = 'JDBC do Postgres, ex.: jdbc:postgresql://host:5432/jhcomp_planner' }
    @{ Nome = 'DB_USER'; Para = 'Usuário do banco' }
    @{ Nome = 'DB_PASSWORD'; Para = 'Senha do banco' }
    @{ Nome = 'JWT_SECRET'; Para = 'Assina os tokens de acesso (openssl rand -base64 48)' }
    @{ Nome = 'JHCOMP_ENCRYPTION_KEY'; Para = 'Chave AES de 32 bytes em Base64 (openssl rand -base64 32)' }
    @{ Nome = 'CORS_ALLOWED_ORIGINS'; Para = 'Domínio(s) do frontend liberados na API' }
)

function Get-Variavel([string]$nome) {
    $v = [System.Environment]::GetEnvironmentVariable($nome)
    if ([string]::IsNullOrWhiteSpace($v)) { return $null }
    return $v
}

# --- 0. Arquivo de variáveis, se houver ------------------------------------
if ($EnvFile) {
    $caminhoEnv = if ([System.IO.Path]::IsPathRooted($EnvFile)) { $EnvFile } else { Join-Path $root $EnvFile }
    if (-not (Test-Path $caminhoEnv)) {
        Write-Host "ERRO: arquivo de variáveis não encontrado: $caminhoEnv" -ForegroundColor Red
        exit 1
    }
    Write-Host "Carregando variáveis de $caminhoEnv" -ForegroundColor DarkGray
    foreach ($linha in Get-Content $caminhoEnv) {
        $t = $linha.Trim()
        if ($t -eq '' -or $t.StartsWith('#')) { continue }
        $i = $t.IndexOf('=')
        if ($i -lt 1) { continue }
        $chave = $t.Substring(0, $i).Trim()
        $valor = $t.Substring($i + 1).Trim().Trim('"').Trim("'")
        [System.Environment]::SetEnvironmentVariable($chave, $valor)
    }
}

# --- 1. Validação ----------------------------------------------------------
Write-Host ""
Write-Host "== 1/4  Variáveis de ambiente obrigatórias ==" -ForegroundColor Cyan

$faltando = @()
foreach ($v in $obrigatorias) {
    if (Get-Variavel $v.Nome) {
        Write-Host ("  OK       {0}" -f $v.Nome) -ForegroundColor Green
    }
    else {
        Write-Host ("  FALTANDO {0}" -f $v.Nome) -ForegroundColor Red
        $faltando += $v
    }
}

if ($faltando.Count -gt 0) {
    Write-Host ""
    Write-Host "Deploy interrompido: $($faltando.Count) variável(is) obrigatória(s) faltando." -ForegroundColor Red
    Write-Host ""
    foreach ($v in $faltando) {
        Write-Host ("  {0}" -f $v.Nome) -ForegroundColor Yellow
        Write-Host ("      {0}" -f $v.Para) -ForegroundColor DarkGray
    }
    Write-Host ""
    Write-Host "Defina-as na sessão:" -ForegroundColor DarkGray
    Write-Host ("  `$env:{0} = '...'" -f $faltando[0].Nome) -ForegroundColor DarkGray
    Write-Host "ou passe um arquivo:  .\scripts\deploy.ps1 -EnvFile .env" -ForegroundColor DarkGray
    Write-Host "(modelo em .env.prod.example; tabela completa no README, secao 4.2)" -ForegroundColor DarkGray
    exit 1
}

$perfil = Get-Variavel 'SPRING_PROFILES_ACTIVE'
if ($perfil -ne 'prod') {
    Write-Host ""
    Write-Host "AVISO: SPRING_PROFILES_ACTIVE='$perfil' (esperado 'prod' em produção)." -ForegroundColor Yellow
}

Write-Host ""
Write-Host "Todas as variáveis obrigatórias estão definidas." -ForegroundColor Green

if ($SomenteValidar) {
    Write-Host "-SomenteValidar: parando aqui." -ForegroundColor DarkGray
    exit 0
}

# --- 2. mvn test -----------------------------------------------------------
Write-Host ""
Write-Host "== 2/4  mvn test (backend) ==" -ForegroundColor Cyan
if ($PularTestes) {
    Write-Host "  -PularTestes: pulado." -ForegroundColor DarkGray
}
else {
    Push-Location (Join-Path $root 'backend')
    try {
        & mvn test
        if ($LASTEXITCODE -ne 0) {
            Write-Host ""
            Write-Host "Deploy interrompido: 'mvn test' falhou (código $LASTEXITCODE)." -ForegroundColor Red
            exit 1
        }
    }
    finally {
        Pop-Location
    }
    Write-Host "  Testes do backend OK." -ForegroundColor Green
}

# --- 3. npm run build ------------------------------------------------------
Write-Host ""
Write-Host "== 3/4  npm run build (frontend) ==" -ForegroundColor Cyan
if ($PularTestes) {
    Write-Host "  -PularTestes: pulado." -ForegroundColor DarkGray
}
else {
    Push-Location (Join-Path $root 'frontend')
    try {
        if (-not (Test-Path 'node_modules')) {
            Write-Host "  node_modules ausente — rodando 'npm ci' ..." -ForegroundColor DarkGray
            & npm ci
            if ($LASTEXITCODE -ne 0) {
                Write-Host "Deploy interrompido: 'npm ci' falhou (código $LASTEXITCODE)." -ForegroundColor Red
                exit 1
            }
        }
        & npm run build
        if ($LASTEXITCODE -ne 0) {
            Write-Host ""
            Write-Host "Deploy interrompido: 'npm run build' falhou (código $LASTEXITCODE)." -ForegroundColor Red
            exit 1
        }
    }
    finally {
        Pop-Location
    }
    Write-Host "  Build do frontend OK." -ForegroundColor Green
}

# --- 4. Imagens Docker -----------------------------------------------------
Write-Host ""
Write-Host "== 4/4  Imagens Docker ==" -ForegroundColor Cyan

$docker = Get-Command docker -ErrorAction SilentlyContinue
if (-not $docker) {
    Write-Host ""
    Write-Host "Docker nao encontrado nesta maquina — etapa de imagens NAO executada." -ForegroundColor Yellow
    Write-Host "As etapas 1-3 passaram. Para fechar o deploy, rode em uma maquina com Docker:" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "  docker build -t jhcomp-planner-backend:$Tag ./backend" -ForegroundColor DarkGray
    Write-Host "  docker build -t jhcomp-planner-frontend:$Tag --build-arg VITE_API_BASE_URL=<url da api> ./frontend" -ForegroundColor DarkGray
    Write-Host ""
    Write-Host "Ou suba a stack inteira com Compose:" -ForegroundColor Yellow
    Write-Host "  docker compose -f docker-compose.prod.yml --env-file .env up -d --build" -ForegroundColor DarkGray
    exit 0
}

$apiUrl = Get-Variavel 'VITE_API_BASE_URL'
if (-not $apiUrl) {
    $apiUrl = '/api'
    Write-Host "  VITE_API_BASE_URL nao definida — usando o padrao '/api' (mesmo dominio com proxy)." -ForegroundColor Yellow
}

Write-Host "  docker build backend -> jhcomp-planner-backend:$Tag" -ForegroundColor DarkGray
& docker build -t "jhcomp-planner-backend:$Tag" (Join-Path $root 'backend')
if ($LASTEXITCODE -ne 0) {
    Write-Host "Deploy interrompido: build da imagem do backend falhou (código $LASTEXITCODE)." -ForegroundColor Red
    exit 1
}

Write-Host "  docker build frontend -> jhcomp-planner-frontend:$Tag (VITE_API_BASE_URL=$apiUrl)" -ForegroundColor DarkGray
& docker build -t "jhcomp-planner-frontend:$Tag" --build-arg "VITE_API_BASE_URL=$apiUrl" (Join-Path $root 'frontend')
if ($LASTEXITCODE -ne 0) {
    Write-Host "Deploy interrompido: build da imagem do frontend falhou (código $LASTEXITCODE)." -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "Imagens construidas: jhcomp-planner-backend:$Tag e jhcomp-planner-frontend:$Tag" -ForegroundColor Green
Write-Host "Suba com: docker compose -f docker-compose.prod.yml --env-file .env up -d" -ForegroundColor DarkGray
