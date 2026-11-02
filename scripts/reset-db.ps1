<#
.SYNOPSIS
    重置数据库（只删库，默认保留上传文件）。

.DESCRIPTION
    解决「Flyway 报 Found non-empty schema」或「Hibernate validate 失败」的最干净做法：
    把数据库卷删掉，让 Flyway 从 V1 重新建表。

    为什么不直接用 docker compose down -v:
      down -v 会删掉 compose 里【所有】命名卷，包括 uploads。
      而本脚本承诺"只清数据库、默认保留上传文件"，所以改成：
      先 down（不删卷）→ 精确删除数据库卷 → up。

    会丢失全部业务数据：用户、会话、消息、技能缓存。

.PARAMETER Force
    跳过确认提示。

.PARAMETER WithUploads
    连 uploads 卷一起删（用户上传的图片和文件也会丢）。

.EXAMPLE
    .\scripts\reset-db.ps1
    .\scripts\reset-db.ps1 -Force
    .\scripts\reset-db.ps1 -Force -WithUploads
#>

[CmdletBinding()]
param(
    [switch]$Force,
    [switch]$WithUploads
)

$ErrorActionPreference = 'Stop'
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch { }
try { $null = chcp 65001 } catch { }

$RepoRoot = Split-Path -Parent $PSScriptRoot
$ComposeFile = Join-Path $RepoRoot 'deploy\docker-compose.yml'

if (-not (Test-Path $ComposeFile)) {
    Write-Host "找不到 compose 文件：$ComposeFile" -ForegroundColor Red
    exit 1
}

# 卷名 = <compose 项目名>_<卷名>，项目名取自 compose 里的 name: weliao
$DbVolume = 'weliao_mysql-data'
$UploadVolume = 'weliao_uploads'

Write-Host ''
Write-Host '=== 重置数据库 ===' -ForegroundColor Yellow
Write-Host ''
Write-Host '将删除的卷:' -ForegroundColor DarkGray
Write-Host "  $DbVolume    （数据库：用户/会话/消息/技能缓存 全部清空）" -ForegroundColor Red
if ($WithUploads) {
    Write-Host "  $UploadVolume  （用户上传的图片和文件）" -ForegroundColor Red
} else {
    Write-Host "  $UploadVolume  保留，不动" -ForegroundColor Green
}

if (-not $Force) {
    Write-Host ''
    $answer = Read-Host '确认继续？输入 YES 回车'
    if ($answer -ne 'YES') {
        Write-Host '已取消，未做任何改动。' -ForegroundColor DarkGray
        exit 0
    }
}

Push-Location (Join-Path $RepoRoot 'deploy')
try {
    Write-Host ''
    Write-Host '[1/3] 停止容器（不删卷）...' -ForegroundColor Cyan
    docker compose -f $ComposeFile down
    if ($LASTEXITCODE -ne 0) { throw 'docker compose down 失败' }

    Write-Host '[2/3] 删除数据卷 ...' -ForegroundColor Cyan
    $volumes = @($DbVolume)
    if ($WithUploads) { $volumes += $UploadVolume }
    foreach ($v in $volumes) {
        $exists = docker volume ls --format '{{.Name}}' | Select-String -SimpleMatch $v
        if ($exists) {
            docker volume rm $v | Out-Null
            Write-Host "  已删除 $v" -ForegroundColor DarkGray
        } else {
            Write-Host "  $v 不存在，跳过" -ForegroundColor DarkGray
        }
    }

    Write-Host '[3/3] 重新启动（Flyway 从 V1 建表）...' -ForegroundColor Cyan
    docker compose -f $ComposeFile up -d
    if ($LASTEXITCODE -ne 0) { throw 'docker compose up 失败' }
} finally {
    Pop-Location
}

Write-Host ''
Write-Host '完成。约 20 秒后用 .\start.cmd -Status 检查。' -ForegroundColor Green
Write-Host '确认迁移日志: docker compose -f deploy\docker-compose.yml logs backend | Select-String Migrating'
