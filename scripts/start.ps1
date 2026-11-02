<#
.SYNOPSIS
    微聊 WeLiao 一键启动 / 停止脚本。

.DESCRIPTION
    自动完成：检查 Docker 引擎 → 启动容器 → 等待服务就绪 → 探测局域网地址 → 打印访问网址。

.PARAMETER Stop
    停止服务（保留数据卷）。

.PARAMETER Rebuild
    重新构建镜像后再启动。改了前后端代码时用。

.PARAMETER Status
    只查看当前状态，不做任何变更。

.EXAMPLE
    .\start.cmd
    .\start.cmd -Rebuild
    .\start.cmd -Stop
    .\start.cmd -Status
#>

[CmdletBinding()]
param(
    [switch]$Stop,
    [switch]$Rebuild,
    [switch]$Status,
    [switch]$Url
)

$ErrorActionPreference = 'Stop'

# 控制台用 UTF-8，避免中文乱码（配合脚本文件的 BOM）
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch { }
try { $null = chcp 65001 } catch { }

$ScriptDir = Split-Path -Parent $PSCommandPath
$RepoRoot = Split-Path -Parent $ScriptDir
$ComposeFile = Join-Path $RepoRoot 'deploy\docker-compose.yml'
$WebPort = 8080
$LocalUrl = "http://localhost:$WebPort"
$HealthPath = '/actuator/health'

# 访问地址落盘：关掉启动窗口后仍能查到地址
$RuntimeDir = Join-Path $RepoRoot 'runtime'
$AddressFile = Join-Path $RuntimeDir 'last-url.txt'

# ---------------------------------------------------------------- 输出工具

function Write-Title {
    Write-Host ''
    Write-Host '========================================' -ForegroundColor Cyan
    Write-Host '   微聊 WeLiao · 一键启动' -ForegroundColor Cyan
    Write-Host '========================================' -ForegroundColor Cyan
    Write-Host ''
}

function Write-Step {
    param([string]$Text)
    Write-Host "[$Text] " -NoNewline -ForegroundColor Yellow
}

function Write-Ok {
    param([string]$Text = 'OK')
    Write-Host $Text -ForegroundColor Green
}

function Write-Bad {
    param([string]$Text)
    Write-Host $Text -ForegroundColor Red
}

function Write-Hint {
    param([string]$Text)
    Write-Host "    $Text" -ForegroundColor DarkGray
}

# ---------------------------------------------------------------- 环境探测

function Get-DockerExe {
    $cmd = Get-Command docker -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    # 用户级安装时 PATH 可能还没刷新，兜底找一下
    $fallback = Join-Path $env:LOCALAPPDATA 'Programs\DockerDesktop\resources\bin\docker.exe'
    if (Test-Path $fallback) { return $fallback }
    return $null
}

function Test-IsPrivateIp {
    param([string]$Ip)
    if ($Ip -match '^10\.') { return $true }
    if ($Ip -match '^192\.168\.') { return $true }
    if ($Ip -match '^172\.(1[6-9]|2[0-9]|3[01])\.') { return $true }
    return $false
}

<#
探测局域网地址。优先级：
  1. 默认路由所在网卡（最接近"真正在用"的那块网卡）
  2. 排除虚拟网卡（WSL / Hyper-V / VPN / VMware / TAP）后的私网地址
  3. 任何非回环 IPv4
排除 169.254.x.x（链路本地，通常意味着没连上网络）。
#>
function Get-LanAddress {
    $excludeAlias = 'vEthernet|WSL|Hyper-V|VPN|Radmin|VMware|VirtualBox|Loopback|TAP|Tailscale|ZeroTier'

    try {
        $route = Get-NetRoute -DestinationPrefix '0.0.0.0/0' -ErrorAction SilentlyContinue |
            Sort-Object RouteMetric, InterfaceMetric | Select-Object -First 1
        if ($route) {
            $addr = Get-NetIPAddress -AddressFamily IPv4 -InterfaceIndex $route.InterfaceIndex -ErrorAction SilentlyContinue |
                Where-Object { $_.IPAddress -notmatch '^(127\.|169\.254\.)' } |
                Select-Object -First 1
            if ($addr -and $addr.InterfaceAlias -notmatch $excludeAlias) {
                return [pscustomobject]@{ Ip = $addr.IPAddress; Alias = $addr.InterfaceAlias }
            }
        }
    } catch { }

    try {
        $candidates = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
            Where-Object {
                $_.IPAddress -notmatch '^(127\.|169\.254\.)' -and
                $_.InterfaceAlias -notmatch $excludeAlias
            } |
            Sort-Object @{ Expression = { if ($_.IPAddress -match '^192\.168\.') { 0 } elseif ($_.IPAddress -match '^10\.') { 1 } else { 2 } } }
        if ($candidates) {
            $c = $candidates | Select-Object -First 1
            return [pscustomobject]@{ Ip = $c.IPAddress; Alias = $c.InterfaceAlias }
        }
        # 最后的兜底：允许虚拟网卡，总比没有强
        $any = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
            Where-Object { $_.IPAddress -notmatch '^(127\.|169\.254\.)' } |
            Select-Object -First 1
        if ($any) {
            return [pscustomobject]@{ Ip = $any.IPAddress; Alias = $any.InterfaceAlias }
        }
    } catch { }

    return $null
}

function Test-Health {
    param([string]$Url, [int]$TimeoutSec = 3)
    try {
        $r = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec $TimeoutSec
        if ($r.StatusCode -ne 200) { return $false }

        # PowerShell 5.1 的 -UseBasicParsing 有时把 Content 返回成 byte[]，
        # 直接 -match 会永远为假（曾导致健康检查一直超时）。这里统一解码。
        $body = $r.Content
        if ($body -is [byte[]]) {
            $body = [System.Text.Encoding]::UTF8.GetString($body)
        }

        # 只认 actuator 的 JSON，避免 nginx 的 SPA 回退（也返回 200）造成误判
        return ("$body" -match '"status"\s*:\s*"UP"')
    } catch {
        return $false
    }
}

<#
取后端运行环境自检信息：数据库类型、版本、位置、表结构版本、行数。
数据来自 GET /api/system/info —— 由 JDBC 元数据直接生成，不会和真实连接不一致。

为什么不用 /h2-console 或看配置文件来判断：
  · nginx 的 SPA 兜底会让任何不存在的路径都返回 200，状态码不可信
  · 配置文件只说明"打算连什么"，不说明"此刻真的连上了什么"
#>
function Get-SystemInfo {
    param([string]$BaseUrl = 'http://127.0.0.1:8080')
    try {
        $r = Invoke-WebRequest -Uri "$BaseUrl/api/system/info" -UseBasicParsing -TimeoutSec 5
        if ($r.StatusCode -ne 200) { return $null }
        $body = $r.Content
        if ($body -is [byte[]]) { $body = [System.Text.Encoding]::UTF8.GetString($body) }
        $json = "$body" | ConvertFrom-Json
        if ($json.code -ne 0) { return $null }
        return $json.data
    } catch {
        return $null
    }
}

function Show-SystemInfo {
    $info = Get-SystemInfo
    if (-not $info) {
        Write-Host ''
        Write-Host '  运行环境' -ForegroundColor Cyan
        Write-Host '  ------------------------------------------------------' -ForegroundColor DarkGray
        Write-Host '    后端未响应，无法获取数据库信息' -ForegroundColor Yellow
        Write-Host '  ------------------------------------------------------' -ForegroundColor DarkGray
        return
    }

    $db = $info.database
    $pool = $info.pool
    $counts = $info.counts

    if ($db.ok) {
        $dbLine = "$($db.product) $($db.productVersion)"
    } else {
        $dbLine = "读取失败：$($db.error)"
    }
    $hostLine = if ($db.host) { "$($db.host)  →  库 $($db.catalog)" } else { "库 $($db.catalog)" }
    $poolLine = if ($null -ne $pool.active) { "活跃 $($pool.active) / 空闲 $($pool.idle) / 共 $($pool.total)" } else { '—' }
    $countLine = "用户 $($counts.users) · 会话 $($counts.conversations) · 消息 $($counts.messages) · 技能 $($counts.skills)"

    Write-Host ''
    Write-Host '  运行环境' -ForegroundColor Cyan
    Write-Host '  ------------------------------------------------------' -ForegroundColor DarkGray
    Write-Host '    配置 profile :  ' -NoNewline
    Write-Host $info.profile -ForegroundColor Green
    Write-Host '    数据库       :  ' -NoNewline
    Write-Host $dbLine -ForegroundColor Green
    Write-Host '    位置         :  ' -NoNewline
    Write-Host $hostLine -ForegroundColor DarkGray
    Write-Host '    驱动         :  ' -NoNewline
    Write-Host "$($db.driver) $($db.driverVersion)" -ForegroundColor DarkGray
    Write-Host '    连接池       :  ' -NoNewline
    Write-Host $poolLine -ForegroundColor DarkGray
    Write-Host '    表结构版本   :  ' -NoNewline
    Write-Host "v$($info.schemaVersion)" -ForegroundColor DarkGray
    Write-Host '    数据量       :  ' -NoNewline
    Write-Host $countLine -ForegroundColor DarkGray
    Write-Host '  ------------------------------------------------------' -ForegroundColor DarkGray
}

function Show-FirewallHint {
    Write-Host ''    Write-Host '  手机打不开？多半是 Windows 防火墙拦了。用【管理员】PowerShell 执行：' -ForegroundColor Yellow
    Write-Host '    netsh advfirewall firewall add rule name="WeLiao-8080" dir=in action=allow protocol=TCP localport=8080' -ForegroundColor White
    Write-Host ''
    Write-Hint '注意：手机必须和这台电脑连同一个 WiFi；公司网络若开了 AP 隔离，设备之间无法互访。'
}

function Show-Addresses {
    param($Lan)

    Write-Host ''
    Write-Host '  网站地址' -ForegroundColor Cyan
    Write-Host '  ------------------------------------------------------' -ForegroundColor DarkGray
    Write-Host '    本机访问 :  ' -NoNewline
    Write-Host $LocalUrl -ForegroundColor Green

    if ($Lan) {
        $lanUrl = "http://$($Lan.Ip):$WebPort"
        Write-Host '    局域网   :  ' -NoNewline
        Write-Host $lanUrl -ForegroundColor Green -NoNewline
        Write-Host '   ← 手机／同事用这个' -ForegroundColor DarkGray
        Write-Host '    网卡     :  ' -NoNewline
        Write-Host "$($Lan.Alias)  ($($Lan.Ip))" -ForegroundColor DarkGray
        Write-Host ''
        Write-Host '    健康检查 :  ' -NoNewline
        Write-Host "$lanUrl$HealthPath" -ForegroundColor DarkGray
    } else {
        Write-Host '    局域网   :  ' -NoNewline
        Write-Bad '未探测到（可能没连网络，或没有可用网卡）'
    }

    Write-Host '  ------------------------------------------------------' -ForegroundColor DarkGray

    # 落盘，关掉窗口后还能查
    if (Save-AddressFile $Lan) {
        Write-Host ''
        Write-Host '    已写入 :  ' -NoNewline
        Write-Host $AddressFile -ForegroundColor DarkGray
        Write-Host '    查地址 :  ' -NoNewline
        Write-Host '.\start.cmd -Url' -ForegroundColor DarkGray -NoNewline
        Write-Host '   （不需要 Docker 在运行）' -ForegroundColor DarkGray
    }
}

<#
把访问信息写进 runtime\last-url.txt，每次启动/查询都会覆盖更新。
同时给出「人能直接读」的说明行和「机器能解析」的 key=value 行。
#>
function Save-AddressFile {
    param($Lan)

    try {
        if (-not (Test-Path $RuntimeDir)) {
            New-Item -ItemType Directory -Path $RuntimeDir -Force | Out-Null
        }

        $lanIp = ''
        $lanUrl = ''
        $alias = ''
        if ($Lan) {
            $lanIp = $Lan.Ip
            $lanUrl = "http://$($Lan.Ip):$WebPort"
            $alias = $Lan.Alias
        }

        $lines = @(
            '# 微聊 WeLiao 访问信息（每次启动自动更新，可随时删掉）'
            "# 更新时间: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
            ''
            "port=$WebPort"
            "local_url=$LocalUrl"
            "lan_ip=$lanIp"
            "lan_url=$lanUrl"
            "adapter=$alias"
            "health_url=$lanUrl$HealthPath"
        )

        # 顺带把当前数据库信息也记下来，事后不用再连容器就能知道连的是哪个库
        $info = Get-SystemInfo
        if ($info) {
            $lines += ''
            $lines += '# 当前数据库（来自 /api/system/info）'
            $lines += "db_profile=$($info.profile)"
            $lines += "db_product=$($info.database.product)"
            $lines += "db_version=$($info.database.productVersion)"
            $lines += "db_host=$($info.database.host)"
            $lines += "db_name=$($info.database.catalog)"
            $lines += "db_schema_version=$($info.schemaVersion)"
            $lines += "db_counts=users:$($info.counts.users),conversations:$($info.counts.conversations),messages:$($info.counts.messages),skills:$($info.counts.skills)"
        }

        $lines += ''
        $lines += '# 手机/同事用 lan_url，本机用 local_url。'
        $lines += '# 手机打不开先看防火墙，参考 README「一键启动」一节。'

        Set-Content -Path $AddressFile -Value $lines -Encoding UTF8
        return $true
    } catch {
        return $false
    }
}

# ---------------------------------------------------------------- 主流程

Write-Title

# ---- 只读上次写入的地址（不需要 Docker）
if ($Url) {
    if (Test-Path $AddressFile) {
        Write-Host '  上次启动记录的访问信息' -ForegroundColor Cyan
        Write-Host '  ------------------------------------------------------' -ForegroundColor DarkGray
        Get-Content -Path $AddressFile -Encoding UTF8 | ForEach-Object {
            Write-Host "  $_" -ForegroundColor DarkGray
        }
        Write-Host '  ------------------------------------------------------' -ForegroundColor DarkGray
        Write-Host ''
        Write-Host '    文件位置 :  ' -NoNewline
        Write-Host $AddressFile -ForegroundColor DarkGray
        Write-Host '    刷新状态 :  ' -NoNewline
        Write-Host '.\start.cmd -Status' -ForegroundColor DarkGray -NoNewline
        Write-Host '   （需要 Docker 在运行）' -ForegroundColor DarkGray
    } else {
        Write-Bad '还没有启动记录。'
        Write-Host ''
        Write-Hint '先运行一次 .\start.cmd，地址会自动写入：'
        Write-Hint "  $AddressFile"
    }
    Write-Host ''
    exit 0
}

if (-not (Test-Path $ComposeFile)) {
    Write-Bad "找不到 compose 文件：$ComposeFile"
    exit 1
}

$docker = Get-DockerExe
if (-not $docker) {
    Write-Bad '未找到 docker 命令。请确认已安装 Docker Desktop。'
    exit 1
}

# ---- 只查状态
if ($Status) {
    Write-Step '1/1 当前状态'
    try { & $docker compose -f $ComposeFile ps } catch { Write-Bad "查询失败：$($_.Exception.Message)" }
    Write-Host ''
    Show-SystemInfo
    Show-Addresses (Get-LanAddress)
    exit 0
}

# ---- 停止
if ($Stop) {
    Write-Step '1/2 停止容器'
    & $docker compose -f $ComposeFile down
    Write-Ok '已停止'
    Write-Hint '数据保留在 Docker 卷中，下次启动数据还在。'
    Write-Hint '（不要用 docker compose down -v，那会删掉数据库和上传的文件）'
    exit 0
}

# ---- 1. 检查引擎
Write-Step '1/4 检查 Docker 引擎'
$serverVersion = $null
try {
    $serverVersion = & $docker info --format '{{.ServerVersion}}' 2>$null
} catch { }
if (-not $serverVersion -or "$serverVersion" -match 'error|Cannot|failed') {
    Write-Bad '引擎未运行'
    Write-Host ''
    Write-Hint '请先启动 Docker Desktop（开始菜单搜索），等托盘鲸鱼图标变绿、显示 Engine running 后重试。'
    exit 1
}
Write-Ok "OK (v$serverVersion)"

# ---- 2. 启动容器
if ($Rebuild) {
    Write-Step '2/4 启动容器（重建镜像，可能需要几分钟）'
    & $docker compose -f $ComposeFile up -d --build
} else {
    Write-Step '2/4 启动容器'
    & $docker compose -f $ComposeFile up -d
}
if ($LASTEXITCODE -ne 0) {
    Write-Bad '启动失败，请检查上方日志'
    exit 1
}
Write-Ok 'OK'

# ---- 3. 等待就绪
Write-Step '3/4 等待服务就绪'
$ready = $false
for ($i = 1; $i -le 60; $i++) {
    if (Test-Health "$LocalUrl$HealthPath") { $ready = $true; break }
    Start-Sleep -Seconds 2
}
if (-not $ready) {
    Write-Bad '超时'
    Write-Host ''
    Write-Hint '看看后端日志排查：'
    Write-Hint "  docker compose -f `"$ComposeFile`" logs --tail=50 backend"
    exit 1
}
Write-Ok 'OK'

# ---- 4. 探测地址
Write-Step '4/4 探测访问地址'
$lan = Get-LanAddress
Write-Ok 'OK'

Show-Addresses $lan
Show-SystemInfo

# 局域网地址自测：能通说明端口绑定没问题，剩下就是防火墙
if ($lan) {
    $lanOk = Test-Health "http://$($lan.Ip):8080$HealthPath"
    if (-not $lanOk) { Show-FirewallHint }
}

Write-Host ''
Write-Host '  常用命令' -ForegroundColor Cyan
Write-Host '    停止服务 :  .\start.cmd -Stop' -ForegroundColor DarkGray
Write-Host '    查看状态 :  .\start.cmd -Status' -ForegroundColor DarkGray
Write-Host '    重建镜像 :  .\start.cmd -Rebuild   （改了代码后）' -ForegroundColor DarkGray
Write-Host '    查看日志 :  docker compose -f deploy\docker-compose.yml logs -f backend' -ForegroundColor DarkGray
Write-Host '    查看数据库 : .\scripts\db.ps1          （网页控制台 http://localhost:8082）' -ForegroundColor DarkGray
Write-Host '    查询数据   : .\scripts\db.ps1 -Sql "SELECT * FROM t_user LIMIT 5"' -ForegroundColor DarkGray
Write-Host ''
