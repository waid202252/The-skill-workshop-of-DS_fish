<#
.SYNOPSIS
    数据库快捷工具：随时查看 / 查询 MySQL。

.DESCRIPTION
    本项目的库跑在 Docker 容器里，平时想看数据有三种方式，这个脚本把它们包成一条命令：

      .\scripts\db.ps1                 打开网页版控制台 Adminer（默认浏览器）
      .\scripts\db.ps1 -Tables         列出所有表 + 行数
      .\scripts\db.ps1 -Sql "SELECT * FROM t_user LIMIT 5"
                                       执行任意 SQL 并直接打印结果
      .\scripts\db.ps1 -Info           打印连接信息（地址/账号/密码）
      .\scripts\db.ps1 -Shell          进入交互式 mysql 终端（输入 exit 退出）

    密码从 deploy\.env 读取，不写死在这里。

.EXAMPLE
    .\scripts\db.ps1 -Sql "SELECT id,username,nickname,created_at FROM t_user ORDER BY id DESC LIMIT 10"
    .\scripts\db.ps1 -Tables
    .\scripts\db.ps1
#>

[CmdletBinding()]
param(
    [string]$Sql,
    [string]$User,
    [string]$NewPassword,
    [switch]$Tables,
    [switch]$Info,
    [switch]$Shell
)

$ErrorActionPreference = 'Continue'
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch { }
try { $null = chcp 65001 } catch { }

$RepoRoot = Split-Path -Parent $PSScriptRoot
$ComposeFile = Join-Path $RepoRoot 'deploy\docker-compose.yml'
$EnvFile = Join-Path $RepoRoot 'deploy\.env'

if (-not (Test-Path $ComposeFile)) {
    Write-Host "找不到 compose 文件：$ComposeFile" -ForegroundColor Red
    exit 1
}

# 读取连接信息
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
$webPort = if ($envMap['WEB_PORT']) { $envMap['WEB_PORT'] } else { '8080' }
$adminerPort = if ($envMap['ADMINER_PORT']) { $envMap['ADMINER_PORT'] } else { '8082' }

if (-not $dbPass) {
    Write-Host 'deploy\.env 里没有 MYSQL_ROOT_PASSWORD' -ForegroundColor Red
    exit 1
}

# 在容器里执行 mysql 客户端。
# 用 MYSQL_PWD 传密码而不是 -p：既不在命令行暴露密码，
# 也不会产生 "Using a password on the command line" 警告
# （那个警告走 stderr，会被 PowerShell 当成错误抛出来）。
function Invoke-Sql {
    param([string]$Query)
    # SQL 从 stdin 传进去：PowerShell 5.1 把带引号的参数交给原生程序时会吃掉引号，
    # 所以绝不要用 -e "QUERY" 这种写法
    $cmd = 'MYSQL_PWD=' + $dbPass + ' mysql --default-character-set=utf8mb4 -h127.0.0.1 -uroot ' + $dbName
    $Query | docker compose -f $ComposeFile exec -T mysql sh -c $cmd
}

if ($User -and $NewPassword) {
    # 改密码思路：后端没有「重置密码」接口，而密码是 BCrypt 加盐哈希，
    # 不能直接写明文。所以先注册一个临时账号（由后端生成正确的哈希），
    # 把它的 password 列复制给目标用户，再删掉临时账号。
    $base = "http://127.0.0.1:$webPort"
    $tmp = 'tmpreset' + (Get-Random -Minimum 100000 -Maximum 999999)

    Write-Host "正在把 $User 的密码重置为 $NewPassword ..." -ForegroundColor Cyan

    $body = @{ username = $tmp; password = $NewPassword; nickname = 'tmpreset' } | ConvertTo-Json
    try {
        $reg = Invoke-RestMethod -Uri "$base/api/auth/register" -Method Post `
            -ContentType 'application/json' -Headers @{ Origin = $base } -Body $body -TimeoutSec 10
    } catch {
        Write-Host '无法连接后端，请先启动服务（.\start.cmd）' -ForegroundColor Red
        exit 1
    }
    if ($reg.code -ne 0) {
        Write-Host "临时账号创建失败: $($reg.message)" -ForegroundColor Red
        exit 1
    }

    Invoke-Sql "UPDATE t_user SET password = (SELECT p FROM (SELECT password AS p FROM t_user WHERE username = '$tmp') t) WHERE username = '$User';" | Out-Null
    Invoke-Sql "SELECT id, username FROM t_user WHERE username = '$User';"
    Invoke-Sql "DELETE FROM t_user WHERE username = '$tmp';" | Out-Null

    # 立刻验证一次
    try {
        $check = Invoke-RestMethod -Uri "$base/api/auth/login" -Method Post `
            -ContentType 'application/json' -Headers @{ Origin = $base } `
            -Body (@{ username = $User; password = $NewPassword } | ConvertTo-Json) -TimeoutSec 10
        if ($check.code -eq 0) {
            Write-Host "完成：现在可以用 $User / $NewPassword 登录" -ForegroundColor Green
        } else {
            Write-Host "已改密码但验证失败: $($check.message)" -ForegroundColor Yellow
        }
    } catch {
        Write-Host '已改密码，但登录验证请求失败（服务可能在重启）' -ForegroundColor Yellow
    }
    exit 0
}

if ($Info) {
    Write-Host ''
    Write-Host '  数据库连接信息' -ForegroundColor Cyan
    Write-Host '  ------------------------------------------------------' -ForegroundColor DarkGray
    Write-Host "    网页控制台 :  http://localhost:$adminerPort"
    Write-Host "    应用地址   :  http://localhost:$webPort"
    Write-Host "    容器内地址 :  mysql:3306"
    Write-Host "    库名       :  $dbName"
    Write-Host "    用户名     :  root"
    Write-Host "    密码       :  $dbPass"
    Write-Host '  ------------------------------------------------------' -ForegroundColor DarkGray
    Write-Host '    Adminer 登录：服务器填 mysql，其余按上面填' -ForegroundColor DarkGray
    exit 0
}

if ($Tables) {
    Invoke-Sql "SELECT TABLE_NAME AS tbl, TABLE_ROWS AS est_rows FROM information_schema.TABLES WHERE TABLE_SCHEMA='$dbName' ORDER BY TABLE_NAME;"
    exit 0
}

if ($Sql) {
    Invoke-Sql $Sql
    exit 0
}

if ($Shell) {
    Write-Host "进入 $dbName，输入 exit 退出" -ForegroundColor Cyan
    $inner = 'MYSQL_PWD=' + $dbPass + ' mysql -h127.0.0.1 -uroot ' + $dbName
    docker compose -f $ComposeFile exec -T mysql sh -c $inner
    exit 0
}

# 默认：打开网页控制台
$url = "http://localhost:$adminerPort"
Write-Host ''
Write-Host '  打开数据库控制台 Adminer' -ForegroundColor Cyan
Write-Host "  $url" -ForegroundColor Green
Write-Host ''
Write-Host '  登录信息（也可用 -Info 随时查看）：' -ForegroundColor DarkGray
Write-Host "    服务器: mysql      用户名: root      密码: $dbPass" -ForegroundColor DarkGray
Write-Host "    数据库: $dbName" -ForegroundColor DarkGray
Write-Host ''
Write-Host '  常用命令：' -ForegroundColor DarkGray
Write-Host '    .\scripts\db.ps1 -Tables' -ForegroundColor DarkGray
Write-Host '    .\scripts\db.ps1 -Sql "SELECT * FROM t_user LIMIT 5"' -ForegroundColor DarkGray
Write-Host ''

try {
    Start-Process $url
    Write-Host '  已在浏览器打开。' -ForegroundColor Green
} catch {
    Write-Host '  打开浏览器失败，请手动复制上面的地址。' -ForegroundColor Yellow
}
