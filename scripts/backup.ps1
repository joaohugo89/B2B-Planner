<#
    Backup do JH.COMP Planner num comando: dump do banco (pg_dump -F c) + a pasta
    backend/uploads, tudo num único .zip nomeado com a data.

    Uso:
      .\scripts\backup.ps1
      .\scripts\backup.ps1 -Destino D:\Backups
      .\scripts\backup.ps1 -DbName jhcomp_planner -DbUser jhcomp -DbPassword jhcomp

    Destino padrão: %USERPROFILE%\Backups
    Nome do arquivo: jhcomp-planner-backup-AAAA-MM-DD-HHmmss.zip

    Dentro do .zip:
      banco.dump        dump no formato custom do pg_dump (restaura com pg_restore)
      uploads/          cópia de backend/uploads
      backup.txt        data, versão do servidor e parâmetros usados

    Restaurar: .\scripts\restore.ps1 -Arquivo <caminho do .zip>
#>
param(
    [string]$Destino = (Join-Path $env:USERPROFILE 'Backups'),

    [string]$DbHost = 'localhost',
    [int]$DbPort = 5432,
    [string]$DbName = 'jhcomp_planner',
    [string]$DbUser = 'jhcomp',
    [string]$DbPassword = 'jhcomp',

    [string]$BinDir = 'C:\Program Files\PostgreSQL\18\bin',

    # Mantém no destino apenas os N backups mais recentes (0 = não apaga nada).
    [int]$Manter = 0
)

$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$uploadsDir = Join-Path $root 'backend\uploads'
$pgDump = Join-Path $BinDir 'pg_dump.exe'

if (-not (Test-Path $pgDump)) {
    throw "pg_dump não encontrado em '$BinDir'. Passe -BinDir com o caminho dos binários do PostgreSQL."
}

if (-not (Test-Path $Destino)) {
    New-Item -ItemType Directory -Path $Destino -Force | Out-Null
}

$stamp = Get-Date -Format 'yyyy-MM-dd-HHmmss'
$zipPath = Join-Path $Destino "jhcomp-planner-backup-$stamp.zip"
$staging = Join-Path ([System.IO.Path]::GetTempPath()) "jhcomp-backup-$stamp"
New-Item -ItemType Directory -Path $staging -Force | Out-Null

try {
    # --- Banco -----------------------------------------------------------
    Write-Host "Dump do banco '$DbName' em ${DbHost}:${DbPort} ..." -ForegroundColor Cyan
    $dumpFile = Join-Path $staging 'banco.dump'
    $env:PGPASSWORD = $DbPassword
    try {
        & $pgDump -h $DbHost -p $DbPort -U $DbUser -d $DbName -F c -f $dumpFile
        if ($LASTEXITCODE -ne 0) {
            throw "pg_dump terminou com código $LASTEXITCODE."
        }
    }
    finally {
        Remove-Item Env:\PGPASSWORD -ErrorAction SilentlyContinue
    }
    $dumpMb = [math]::Round((Get-Item $dumpFile).Length / 1MB, 2)
    Write-Host "  banco.dump: $dumpMb MB" -ForegroundColor DarkGray

    # --- Uploads ---------------------------------------------------------
    $destUploads = Join-Path $staging 'uploads'
    New-Item -ItemType Directory -Path $destUploads -Force | Out-Null
    if (Test-Path $uploadsDir) {
        $arquivos = @(Get-ChildItem -Path $uploadsDir -Recurse -File)
        Write-Host "Copiando backend\uploads ($($arquivos.Count) arquivos) ..." -ForegroundColor Cyan
        if ($arquivos.Count -gt 0) {
            Copy-Item -Path (Join-Path $uploadsDir '*') -Destination $destUploads -Recurse -Force
        }
    }
    else {
        Write-Host "backend\uploads não existe — backup vai sem uploads." -ForegroundColor Yellow
        $arquivos = @()
    }

    # --- Manifesto -------------------------------------------------------
    @(
        "Backup do JH.COMP Planner"
        "Data: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
        "Maquina: $env:COMPUTERNAME"
        "Banco: $DbName em ${DbHost}:${DbPort} (usuario $DbUser)"
        "Formato do dump: pg_dump -F c (custom) — restaure com pg_restore"
        "Arquivos em uploads: $($arquivos.Count)"
    ) | Set-Content -Path (Join-Path $staging 'backup.txt') -Encoding UTF8

    # --- Zip -------------------------------------------------------------
    Write-Host "Compactando em $zipPath ..." -ForegroundColor Cyan
    Compress-Archive -Path (Join-Path $staging '*') -DestinationPath $zipPath -CompressionLevel Optimal -Force
}
finally {
    Remove-Item -Path $staging -Recurse -Force -ErrorAction SilentlyContinue
}

$zipMb = [math]::Round((Get-Item $zipPath).Length / 1MB, 2)
Write-Host ""
Write-Host "Backup pronto: $zipPath ($zipMb MB)" -ForegroundColor Green

if ($Manter -gt 0) {
    $antigos = @(Get-ChildItem -Path $Destino -Filter 'jhcomp-planner-backup-*.zip' |
        Sort-Object LastWriteTime -Descending | Select-Object -Skip $Manter)
    foreach ($a in $antigos) {
        Remove-Item $a.FullName -Force
        Write-Host "Removido backup antigo: $($a.Name)" -ForegroundColor DarkGray
    }
}
