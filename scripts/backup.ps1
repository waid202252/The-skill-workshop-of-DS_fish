<#
.SYNOPSIS
    备份数据：MySQL 全库 SQL + 上传文件打包。

.DESCRIPTION
    在没有正式备份策略之前，这是最低成本的兜底。产物落在 backups\（已 gitignore）。

    做了什么:
      1. docker compose exec mysql mysqldump  → backups\mysql-<时间>.sql
      2. docker compose exec backend tar      → backups\uploads-<时间>.tar.gz

    为什么用 cmd /c 重定向而不是 PowerShell 管道:
      · PowerShell 5.1 没有 -AsByteStream，用 Set-Content 会把二进制写坏
      · Out-File -Encoding utf8 会给 SQL 加 BOM、把换行改成 CRLF
      cmd 的重定向是字节级的，5.1 和 7 行为一致。

    恢复（手工）:
      数据库: cmd /c "docker compose -f deploy\docker-compose.yml exec -T mysql mysql -uroot -p<pw> < backups\mysql-xxx.sql"
      上传:   先 docker compose -f deploy\docker-compose.yml cp backups\uploads-xxx.tar.gz backend:/tmp/
              再 docker compose -f deploy\docker-compose.yml exec -T backend tar xzf /tmp/uploads-xxx.tar.gz -C /app

.EXAMPLE
    .\scripts\backup.ps1
#>

[CmdletBinding()]
param(
    [string]$OutputDir
)

$ErrorActionPreference = 'Stop'
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch { }
try { $null = chcp 65001 } catch { }

$RepoRoot = Split-Path -Parent $PSScriptRoot
$ComposeFile = Join-Path $RepoRoot 'deploy\docker-compose.yml'
$EnvFile = Join-Path $RepoRoot 'deploy\.env'

if (-not (Test-Path $ComposeFile)) {
    Write-Host "找不到 compose 文件：$ComposeFile" -ForegroundColor Red
    exit 1
}
if (-not $OutputDir) { $OutputDir = Join-Path $RepoRoot 'backups' }
New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null

# 从 deploy/.env 读库名与密码
$envMap = @{}
if (Test-Path $EnvFile) {
    Get-Content $EnvFile -Encoding UTF8 | ForEach-Object {
        if ($_ -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$') {
            $envMap[$Matches[1]] = $Matches[2].Trim()
        }
    }
}
$dbName = if ($envMap['MYSQL_DB']) { $envMap['MYSQL_DB'] } else { 'weliao' }
$dbPass = $envMap['MYSQL_ROOT_PASSWORD']
if (-not $dbPass) {
    Write-Host 'deploy\.env 里没有 MYSQL_ROOT_PASSWORD，无法备份数据库' -ForegroundColor Red
    exit 1
}

$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$sqlFile = Join-Path $OutputDir "mysql-$stamp.sql"
$tarFile = Join-Path $OutputDir "uploads-$stamp.tar.gz"

Write-Host ''
Write-Host "[1/2] 导出数据库 $dbName ..." -ForegroundColor Cyan
$sqlCmd = 'docker compose -f "{0}" exec -T mysql mysqldump -uroot -p{1} --single-transaction --routines --databases {2} > "{3}"' -f `
    $ComposeFile, $dbPass, $dbName, $sqlFile
cmd /c $sqlCmd
if ($LASTEXITCODE -ne 0) {
    Write-Host 'mysqldump 失败（检查容器是否在运行）' -ForegroundColor Red
    exit 1
}

Write-Host "[2/2] 打包上传文件 ..." -ForegroundColor Cyan
$tarCmd = 'docker compose -f "{0}" exec -T backend tar czf - -C /app uploads > "{1}"' -f `
    $ComposeFile, $tarFile
cmd /c $tarCmd
if ($LASTEXITCODE -ne 0) {
    Write-Host '  （backend 未运行或没有 uploads 目录，跳过文件备份）' -ForegroundColor Yellow
    if (Test-Path $tarFile) { Remove-Item $tarFile -Force }
}

Write-Host ''
Write-Host '备份完成:' -ForegroundColor Green
Get-ChildItem $OutputDir -Filter "*$stamp*" -ErrorAction SilentlyContinue | ForEach-Object {
    Write-Host ('  {0}  {1:N1} KB' -f $_.Name, ($_.Length / 1KB))
}
Write-Host ''
Write-Host "目录: $OutputDir" -ForegroundColor DarkGray
Write-Host '提醒: backups\ 已在 .gitignore 中，不要把备份提交进仓库。' -ForegroundColor DarkGray
