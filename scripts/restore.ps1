<#
    Restaura um backup gerado pelo scripts\backup.ps1: recria o banco a partir do
    banco.dump e repõe a pasta backend/uploads.

    Uso:
      .\scripts\restore.ps1                                  # usa o .zip mais recente do destino padrão
      .\scripts\restore.ps1 -Arquivo C:\Backups\jhcomp-planner-backup-2026-10-19-101500.zip
      .\scripts\restore.ps1 -Arquivo ... -SomenteBanco
      .\scripts\restore.ps1 -Arquivo ... -SomenteUploads

    ATENÇÃO: isto SOBRESCREVE dados. O script lista o que vai acontecer e pede
    confirmação explícita (digitar "restaurar") antes de tocar em qualquer coisa.
    Use -Force para pular a confirmação em automação.
#>
param(
    [string]$Arquivo,

    [string]$Destino = (Join-Path $env:USERPROFILE 'Backups'),

    [string]$DbHost = 'localhost',
    [int]$DbPort = 5432,
    [string]$DbName = 'jhcomp_planner',
    [string]$DbUser = 'jhcomp',
    # Sem o parametro, vale a variavel de ambiente DB_PASSWORD.
    [string]$DbPassword = $env:DB_PASSWORD,

    [string]$BinDir = 'C:\Program Files\PostgreSQL\18\bin',

    [switch]$SomenteBanco,
    [switch]$SomenteUploads,
    [switch]$Force
)

$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$uploadsDir = Join-Path $root 'backend\uploads'
$pgRestore = Join-Path $BinDir 'pg_restore.exe'

if (-not $SomenteUploads -and -not (Test-Path $pgRestore)) {
    throw "pg_restore não encontrado em '$BinDir'. Passe -BinDir com o caminho dos binários do PostgreSQL."
}

# --- Qual backup -----------------------------------------------------------
if (-not $Arquivo) {
    $ultimo = Get-ChildItem -Path $Destino -Filter 'jhcomp-planner-backup-*.zip' -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (-not $ultimo) {
        throw "Nenhum backup encontrado em '$Destino'. Passe -Arquivo com o caminho do .zip."
    }
    $Arquivo = $ultimo.FullName
    Write-Host "Usando o backup mais recente: $Arquivo" -ForegroundColor DarkGray
}

if (-not (Test-Path $Arquivo)) {
    throw "Arquivo de backup não encontrado: '$Arquivo'."
}

$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$staging = Join-Path ([System.IO.Path]::GetTempPath()) "jhcomp-restore-$stamp"

try {
    Write-Host "Abrindo $Arquivo ..." -ForegroundColor Cyan
    Expand-Archive -Path $Arquivo -DestinationPath $staging -Force

    $dumpFile = Join-Path $staging 'banco.dump'
    $srcUploads = Join-Path $staging 'uploads'
    $manifesto = Join-Path $staging 'backup.txt'

    if (-not $SomenteUploads -and -not (Test-Path $dumpFile)) {
        throw "O .zip não contém 'banco.dump' — não parece ter vindo do scripts\backup.ps1."
    }

    if (Test-Path $manifesto) {
        Write-Host ""
        Get-Content $manifesto | ForEach-Object { Write-Host "  $_" -ForegroundColor DarkGray }
    }

    # --- O que vai ser sobrescrito ----------------------------------------
    $acoes = @()
    if (-not $SomenteUploads) {
        $acoes += "APAGAR e recriar todos os objetos do banco '$DbName' em ${DbHost}:${DbPort} (pg_restore --clean)"
    }
    if (-not $SomenteBanco) {
        $atuais = 0
        if (Test-Path $uploadsDir) {
            $atuais = @(Get-ChildItem -Path $uploadsDir -Recurse -File).Count
        }
        $novos = 0
        if (Test-Path $srcUploads) {
            $novos = @(Get-ChildItem -Path $srcUploads -Recurse -File).Count
        }
        $acoes += "SUBSTITUIR '$uploadsDir' ($atuais arquivos hoje) pelos $novos arquivos do backup"
    }

    Write-Host ""
    Write-Host "O restore vai:" -ForegroundColor Yellow
    foreach ($a in $acoes) { Write-Host "  - $a" -ForegroundColor Yellow }
    Write-Host ""

    if (-not $Force) {
        Write-Host "Isto nao pode ser desfeito. Pare o backend antes de continuar." -ForegroundColor Red
        $resposta = Read-Host "Digite 'restaurar' para confirmar (qualquer outra coisa cancela)"
        if ($resposta -ne 'restaurar') {
            Write-Host "Cancelado — nada foi alterado." -ForegroundColor Yellow
            return
        }
    }
    else {
        Write-Host "-Force: confirmacao pulada." -ForegroundColor DarkGray
    }

    # --- Banco -------------------------------------------------------------
    if (-not $SomenteUploads) {
        Write-Host "Restaurando o banco '$DbName' ..." -ForegroundColor Cyan
        $env:PGPASSWORD = $DbPassword
        try {
            # --clean --if-exists derruba os objetos antigos antes de recriar.
            # pg_restore devolve código 1 para avisos (objetos inexistentes), que são esperados
            # num banco vazio; só código > 1 é falha real.
            & $pgRestore -h $DbHost -p $DbPort -U $DbUser -d $DbName --clean --if-exists --no-owner --no-privileges $dumpFile
            if ($LASTEXITCODE -gt 1) {
                throw "pg_restore terminou com código $LASTEXITCODE."
            }
            if ($LASTEXITCODE -eq 1) {
                Write-Host "  pg_restore emitiu avisos (normal num banco vazio)." -ForegroundColor DarkGray
            }
        }
        finally {
            Remove-Item Env:\PGPASSWORD -ErrorAction SilentlyContinue
        }
    }

    # --- Uploads -----------------------------------------------------------
    if (-not $SomenteBanco) {
        if (Test-Path $uploadsDir) {
            # A pasta atual vai para uploads.antes-<data>, ao lado, em vez de ser apagada.
            $backupLocal = "$uploadsDir.antes-$stamp"
            Move-Item -Path $uploadsDir -Destination $backupLocal
            Write-Host "Pasta anterior guardada em $backupLocal" -ForegroundColor DarkGray
        }
        New-Item -ItemType Directory -Path $uploadsDir -Force | Out-Null
        if ((Test-Path $srcUploads) -and @(Get-ChildItem -Path $srcUploads -Force).Count -gt 0) {
            Copy-Item -Path (Join-Path $srcUploads '*') -Destination $uploadsDir -Recurse -Force
        }
        Write-Host "Uploads restaurados em $uploadsDir" -ForegroundColor Cyan
    }
}
finally {
    Remove-Item -Path $staging -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Host ""
Write-Host "Restore concluído. Suba o backend e confira o planner." -ForegroundColor Green
