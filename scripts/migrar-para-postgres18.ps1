<#
    Move os dados do cluster descartável (.pgdata, porta 5440) para a instância
    PostgreSQL 18 instalada na máquina (porta 5432), usando o usuário do app.

    Antes de rodar, crie o usuário e o banco na instância 18 (uma vez só):

        psql -U postgres -h 127.0.0.1 -p 5432
        CREATE ROLE jhcomp LOGIN PASSWORD 'sua-senha';
        CREATE DATABASE jhcomp_planner OWNER jhcomp;
        \q

    Uso:
        .\scripts\migrar-para-postgres18.ps1 -SenhaApp 'sua-senha'

    O que faz: dump do banco antigo -> restaura no novo -> confere a contagem das tabelas.
    Não apaga nada do cluster antigo; ele continua lá até você decidir removê-lo.
#>
param(
    [Parameter(Mandatory = $true)]
    [string]$SenhaApp,

    [string]$BinDir = 'C:\Program Files\PostgreSQL\18\bin',
    [int]$PortaOrigem = 5440,
    [int]$PortaDestino = 5432,
    [string]$Usuario = 'jhcomp',
    [string]$Banco = 'jhcomp_planner'
)

$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$dumpFile = Join-Path $env:TEMP "jhcomp-migracao-$(Get-Date -Format yyyyMMdd-HHmmss).dump"

$pgDump = Join-Path $BinDir 'pg_dump.exe'
$pgRestore = Join-Path $BinDir 'pg_restore.exe'
$psql = Join-Path $BinDir 'psql.exe'

foreach ($exe in @($pgDump, $pgRestore, $psql)) {
    if (-not (Test-Path $exe)) { throw "Não encontrei $exe. Ajuste -BinDir." }
}

# 1) O cluster antigo precisa estar no ar para o dump.
Write-Host "1/4  Garantindo que o cluster antigo esteja rodando..." -ForegroundColor Cyan
& (Join-Path $PSScriptRoot 'pg-local.ps1') start | Out-Null

# 2) Dump completo (formato custom, comprimido).
Write-Host "2/4  Copiando os dados da porta $PortaOrigem ..." -ForegroundColor Cyan
$env:PGPASSWORD = 'postgres'
& $pgDump -h 127.0.0.1 -p $PortaOrigem -U postgres -d $Banco -F c -f $dumpFile
if ($LASTEXITCODE -ne 0) { throw "Falha no pg_dump." }

# 3) Restaura na instância 18 com o usuário do app.
Write-Host "3/4  Restaurando na porta $PortaDestino como '$Usuario' ..." -ForegroundColor Cyan
$env:PGPASSWORD = $SenhaApp
# --no-owner: tudo passa a pertencer ao usuário do app, não ao postgres do cluster antigo.
& $pgRestore -h 127.0.0.1 -p $PortaDestino -U $Usuario -d $Banco --no-owner --clean --if-exists $dumpFile
if ($LASTEXITCODE -gt 1) { throw "Falha no pg_restore." }  # 1 = avisos, aceitável

# 4) Confere o que chegou do outro lado.
Write-Host "4/4  Conferindo..." -ForegroundColor Cyan
$consulta = @"
select 'posts' as tabela, count(*) from posts
union all select 'template_assets', count(*) from template_assets
union all select 'copy_entries', count(*) from copy_entries
union all select 'copy_tones', count(*) from copy_tones
union all select 'app_users', count(*) from app_users
union all select 'app_settings', count(*) from app_settings
union all select 'instagram_accounts', count(*) from instagram_accounts
order by 1;
"@
& $psql -h 127.0.0.1 -p $PortaDestino -U $Usuario -d $Banco -c $consulta

Remove-Item $dumpFile -ErrorAction SilentlyContinue
Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue

Write-Host ""
Write-Host "Pronto. Para o backend usar a instância 18:" -ForegroundColor Green
Write-Host "  `$env:DB_URL = 'jdbc:postgresql://127.0.0.1:$PortaDestino/$Banco'"
Write-Host "  `$env:DB_USER = '$Usuario'"
Write-Host "  `$env:DB_PASSWORD = '<a senha que você definiu>'"
Write-Host ""
Write-Host "Os uploads continuam em $root\backend\uploads — o banco só guarda o caminho deles." -ForegroundColor Yellow
