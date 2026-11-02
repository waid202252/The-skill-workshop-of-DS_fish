# ============================================================================
# 跨节点投递验证：确认「A 连在实例 1、B 连在实例 2」时消息还能送达
#
# 前置：两个后端实例都已在跑（各自独立的进程），且都处于多实例模式
#   (Invoke-RestMethod http://127.0.0.1:18081/api/system/info).data.cluster
#
# 用法：
#   ./scripts/cluster-check.ps1                                  # 默认 18081 / 18082
#   ./scripts/cluster-check.ps1 -PortA 18081 -PortB 18082
#   ./scripts/cluster-check.ps1 -SkipPresenceCheck               # 没装 redis-cli 时跳过在线状态检查
#
# 退出码：0 = 通过，1 = 失败（可直接接进 CI）
# ============================================================================
param(
    [int]$PortA = 18081,
    [int]$PortB = 18082,
    [string]$RedisContainer = 'weliao-redis-test',
    [switch]$SkipPresenceCheck
)

$ErrorActionPreference = 'Stop'
$baseA = "http://127.0.0.1:$PortA"
$baseB = "http://127.0.0.1:$PortB"

function Post-Api($base, $path, $body, $token) {
    $headers = @{ 'Content-Type' = 'application/json' }
    if ($token) { $headers['Authorization'] = "Bearer $token" }
    $json = if ($null -eq $body) { '{}' } else { $body | ConvertTo-Json -Compress }
    Invoke-RestMethod -Uri "$base$path" -Method Post -Headers $headers -Body $json -TimeoutSec 20
}
function Get-Api($base, $path, $token) {
    $headers = @{}
    if ($token) { $headers['Authorization'] = "Bearer $token" }
    Invoke-RestMethod -Uri "$base$path" -Headers $headers -TimeoutSec 20
}

# .NET 的 ClientWebSocket 一旦取消 ReceiveAsync 的令牌就会 abort 整条连接，
# 所以这里挂一个「永不取消」的读取任务，超时只是不等它，不动连接。
$script:pending = @{}
function Read-Frame($ws, $key, $timeoutMs) {
    if (-not $script:pending.ContainsKey($key)) {
        $buffer = New-Object byte[] 65536
        $segment = [ArraySegment[byte]]::new($buffer)
        $task = $ws.ReceiveAsync($segment, [Threading.CancellationToken]::None)
        $script:pending[$key] = @{ Task = $task; Buffer = $buffer }
    }
    $entry = $script:pending[$key]
    if (-not $entry.Task.Wait($timeoutMs)) { return $null }
    $script:pending.Remove($key)
    if ($entry.Task.IsFaulted) { throw $entry.Task.Exception.GetBaseException() }
    $result = $entry.Task.Result
    if ($result.Count -gt 0) { return [Text.Encoding]::UTF8.GetString($entry.Buffer, 0, $result.Count) }
    return $null
}
function Read-Until($ws, $key, $pattern, $timeoutMs) {
    $deadline = (Get-Date).AddMilliseconds($timeoutMs)
    $seen = @()
    while ((Get-Date) -lt $deadline) {
        $frame = Read-Frame $ws $key 300
        if ($frame) {
            $seen += $frame
            if ($frame -like $pattern) { return @{ Hit = $frame; All = $seen } }
        }
    }
    return @{ Hit = $null; All = $seen }
}

Write-Host "=== 跨节点投递验证 $baseA  <->  $baseB ==="

# --- 0. 前置检查 -------------------------------------------------------------
foreach ($p in @(@{ Base = $baseA; Name = 'A' }, @{ Base = $baseB; Name = 'B' })) {
    try {
        $cluster = (Get-Api $p.Base '/api/system/info' $null).data.cluster
    } catch {
        Write-Host "❌ $($p.Name) 实例不可达：$($p.Base)" -ForegroundColor Red
        exit 1
    }
    Write-Host "  $($p.Name) -> mode=$($cluster.mode) nodeId=$($cluster.nodeId)"
    if ($cluster.mode -notlike 'multi-node*') {
        Write-Host "❌ $($p.Name) 不是多实例模式。先起 Redis，再以 CLUSTER_MODE=auto/redis 重启实例" -ForegroundColor Red
        exit 1
    }
}
if ($RedisContainer) {
    $nodeIds = @(
        (Get-Api $baseA '/api/system/info' $null).data.cluster.nodeId,
        (Get-Api $baseB '/api/system/info' $null).data.cluster.nodeId
    )
    if ($nodeIds[0] -eq $nodeIds[1]) {
        Write-Host "❌ 两个实例的 nodeId 相同（$($nodeIds[0])），它们会互相覆盖在线记录。请显式设置 CLUSTER_NODE_ID" -ForegroundColor Red
        exit 1
    }
}

# --- 1. 两个用户分别注册在两个实例上 -----------------------------------------
$suffix = Get-Random -Minimum 1000 -Maximum 9999
$userA = Post-Api $baseA '/api/auth/register' @{ username = "cca$suffix"; password = 'pass1234'; nickname = '节点A用户' } $null
$userB = Post-Api $baseB '/api/auth/register' @{ username = "ccb$suffix"; password = 'pass1234'; nickname = '节点B用户' } $null
$tokenA = $userA.data.token; $idA = $userA.data.user.id
$tokenB = $userB.data.token; $idB = $userB.data.user.id
Write-Host "  用户 A=$idA (在 $baseA)，用户 B=$idB (在 $baseB)"

# --- 2. 建立好友关系与会话 ---------------------------------------------------
Post-Api $baseA '/api/friends/requests' @{ toUserId = $idB; message = 'cluster-check' } $tokenA | Out-Null
$requests = Get-Api $baseB '/api/friends/requests' $tokenB
Post-Api $baseB "/api/friends/requests/$($requests.data.incoming[0].id)/accept" $null $tokenB | Out-Null
$conversationId = (Post-Api $baseA '/api/conversations/single' @{ userId = $idB } $tokenA).data.id
Write-Host "  会话 conversationId=$conversationId"

# --- 3. 双方各自连到不同实例 -------------------------------------------------
$wsA = [System.Net.WebSockets.ClientWebSocket]::new()
$wsA.ConnectAsync([Uri]"ws://127.0.0.1:$PortA/ws?token=$tokenA", [Threading.CancellationToken]::None).Wait()
Start-Sleep -Milliseconds 400
$wsB = [System.Net.WebSockets.ClientWebSocket]::new()
$wsB.ConnectAsync([Uri]"ws://127.0.0.1:$PortB/ws?token=$tokenB", [Threading.CancellationToken]::None).Wait()
Start-Sleep -Milliseconds 1200

# 把建连阶段的 ready / presence 帧吃掉，避免后面误判
while (Read-Frame $wsA 'A' 200) { }
while (Read-Frame $wsB 'B' 200) { }
Write-Host "  两条连接已建立，A -> $baseA，B -> $baseB"

$failed = $false

# --- 4. 在线状态是否登记到了正确的节点 ---------------------------------------
if (-not $SkipPresenceCheck) {
    try {
        $presenceA = ((docker exec $RedisContainer redis-cli HGETALL "im:presence:$idA") -join '|')
        $presenceB = ((docker exec $RedisContainer redis-cli HGETALL "im:presence:$idB") -join '|')
        Write-Host "  im:presence:${idA} = $presenceA"
        Write-Host "  im:presence:${idB} = $presenceB"
        if (-not $presenceA -or -not $presenceB) {
            Write-Host "❌ 在线登记表里查不到这两个用户，先确认 REDIS_HOST 指向的是同一个 Redis" -ForegroundColor Red
            $failed = $true
        }
    } catch {
        Write-Host "  (跳过在线状态检查：$($_.Exception.Message))" -ForegroundColor Yellow
    }
}

# --- 5. A 在实例 1 发消息，B 应该在实例 2 收到 -------------------------------
$content = "跨节点投递-$suffix"
$sent = Post-Api $baseA "/api/conversations/$conversationId/messages" @{ clientMsgId = "cc$suffix"; type = 'TEXT'; content = $content } $tokenA
Write-Host "  A 发送 messageId=$($sent.data.id)"

$result = Read-Until $wsB 'B' "*$content*" 8000
if ($result.Hit) {
    Write-Host "✅ B 在 $baseB 收到了 A 从 $baseA 发出的消息" -ForegroundColor Green
} else {
    Write-Host "❌ B 8 秒内没收到消息。收到的帧：" -ForegroundColor Red
    $result.All | ForEach-Object { Write-Host "    $_" }
    $failed = $true
}

# --- 6. 断开后在线记录是否清理 ------------------------------------------------
$wsB.Dispose(); $wsA.Dispose()
if (-not $SkipPresenceCheck) {
    Start-Sleep -Milliseconds 1500
    try {
        $left = ((docker exec $RedisContainer redis-cli HGETALL "im:presence:$idB") -join '|')
        Write-Host "  B 断开后 im:presence:${idB} = '$left'"
        if ($left) {
            Write-Host "⚠️ 在线记录没被清掉（正常关闭应该立刻清理；异常退出要等 TTL）" -ForegroundColor Yellow
        }
    } catch { }
}

if ($failed) { exit 1 }
Write-Host "`n全部通过" -ForegroundColor Green
exit 0
