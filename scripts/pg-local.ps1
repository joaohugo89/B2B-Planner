<#
    Sobe um PostgreSQL descartável para o JH.COMP Planner, sem Docker e sem mexer
    nas instâncias já instaladas na máquina.

    Uso:
      .\scripts\pg-local.ps1 start    # cria (na primeira vez) e inicia o cluster
      .\scripts\pg-local.ps1 stop     # para o cluster
      .\scripts\pg-local.ps1 status   # mostra o estado

    Dados: .pgdata/ na raiz do projeto (ignorado pelo git).
    Conexão: 127.0.0.1:5440, banco jhcomp_planner, usuário/senha postgres/postgres.
#>
param(
    [ValidateSet('start', 'stop', 'status')]
    [string]$Action = 'start',

    [string]$BinDir = 'C:\Program Files\PostgreSQL\18\bin',
    [int]$Port = 5440
)

$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$dataDir = Join-Path $root '.pgdata'
$logFile = Join-Path $root '.pgdata.log'
$initdb = Join-Path $BinDir 'initdb.exe'
$pgCtl = Join-Path $BinDir 'pg_ctl.exe'
$psql = Join-Path $BinDir 'psql.exe'

if (-not (Test-Path $pgCtl)) {
    throw "Binários do PostgreSQL não encontrados em '$BinDir'. Passe -BinDir com o caminho correto."
}

switch ($Action) {
    'status' {
        & $pgCtl -D $dataDir status
    }

    'stop' {
        & $pgCtl -D $dataDir -m fast stop
        Write-Host "Cluster parado." -ForegroundColor Yellow
    }

    'start' {
        if (-not (Test-Path $dataDir)) {
            Write-Host "Criando cluster em $dataDir ..." -ForegroundColor Cyan
            $pwFile = Join-Path $env:TEMP 'jhcomp-pgpass.txt'
            'postgres' | Out-File -FilePath $pwFile -Encoding ascii -NoNewline
            try {
                & $initdb -D $dataDir -U postgres --pwfile=$pwFile -E UTF8 --locale=C
            }
            finally {
                Remove-Item $pwFile -ErrorAction SilentlyContinue
            }
        }

        & $pgCtl -D $dataDir -l $logFile -o "-p $Port -h 127.0.0.1" start
        Start-Sleep -Seconds 2

        $env:PGPASSWORD = 'postgres'
        $exists = & $psql -h 127.0.0.1 -p $Port -U postgres -d postgres -At `
            -c "select 1 from pg_database where datname = 'jhcomp_planner'"
        if (-not $exists) {
            & $psql -h 127.0.0.1 -p $Port -U postgres -d postgres -c "create database jhcomp_planner;"
            Write-Host "Banco jhcomp_planner criado." -ForegroundColor Green
        }

        Write-Host ""
        Write-Host "PostgreSQL pronto em 127.0.0.1:$Port (banco jhcomp_planner)." -ForegroundColor Green
        Write-Host "DB_URL = jdbc:postgresql://127.0.0.1:$Port/jhcomp_planner"
    }
}
