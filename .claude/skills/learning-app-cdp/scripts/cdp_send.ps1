param(
    [Parameter(Mandatory = $true)]
    [string]$WebSocketUrl,

    [Parameter(Mandatory = $true)]
    [string]$Method,

    [string]$ParamsJson = "{}",

    [int]$Id = 1,

    [int]$TimeoutSeconds = 10
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Receive-CompleteMessage {
    param(
        [Parameter(Mandatory = $true)]
        [System.Net.WebSockets.ClientWebSocket]$Socket,

        [Parameter(Mandatory = $true)]
        [System.Threading.CancellationToken]$Token
    )

    $buffer = New-Object byte[] 8192
    $stream = New-Object System.IO.MemoryStream

    while ($true) {
        $segment = New-Object System.ArraySegment[byte] -ArgumentList @(,$buffer)
        $result = $Socket.ReceiveAsync($segment, $Token).GetAwaiter().GetResult()

        if ($result.MessageType -eq [System.Net.WebSockets.WebSocketMessageType]::Close) {
            throw "WebSocket closed before a matching CDP response was received."
        }

        if ($result.Count -gt 0) {
            $stream.Write($buffer, 0, $result.Count)
        }

        if ($result.EndOfMessage) {
            break
        }
    }

    $payload = [System.Text.Encoding]::UTF8.GetString($stream.ToArray())
    $stream.Dispose()
    return $payload
}

function Close-WebSocketQuietly {
    param(
        [Parameter(Mandatory = $true)]
        [System.Net.WebSockets.ClientWebSocket]$Socket
    )

    try {
        if ($Socket.State -eq [System.Net.WebSockets.WebSocketState]::Open) {
            [void]$Socket.CloseOutputAsync(
                [System.Net.WebSockets.WebSocketCloseStatus]::NormalClosure,
                "done",
                [System.Threading.CancellationToken]::None
            ).GetAwaiter().GetResult()
        }
        elseif ($Socket.State -eq [System.Net.WebSockets.WebSocketState]::CloseReceived) {
            [void]$Socket.CloseAsync(
                [System.Net.WebSockets.WebSocketCloseStatus]::NormalClosure,
                "done",
                [System.Threading.CancellationToken]::None
            ).GetAwaiter().GetResult()
        }
    }
    catch {
        # Ignore shutdown noise after the caller already received its response.
    }
}

$uri = [System.Uri]::new($WebSocketUrl)
$tokenSource = [System.Threading.CancellationTokenSource]::new()
$tokenSource.CancelAfter([TimeSpan]::FromSeconds($TimeoutSeconds))
$token = $tokenSource.Token
$socket = [System.Net.WebSockets.ClientWebSocket]::new()

try {
    [void]$socket.ConnectAsync($uri, $token).GetAwaiter().GetResult()

    $methodJson = ConvertTo-Json $Method -Compress
    $message = '{"id":' + $Id + ',"method":' + $methodJson + ',"params":' + $ParamsJson + '}'
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($message)
    $outSegment = New-Object System.ArraySegment[byte] -ArgumentList @(,$bytes)
    [void]$socket.SendAsync(
        $outSegment,
        [System.Net.WebSockets.WebSocketMessageType]::Text,
        $true,
        $token
    ).GetAwaiter().GetResult()

    while ($true) {
        $incoming = Receive-CompleteMessage -Socket $socket -Token $token
        $parsed = $incoming | ConvertFrom-Json

        $hasId = $null -ne $parsed.PSObject.Properties['id']
        if ($hasId -and [int]$parsed.id -eq $Id) {
            Write-Output $incoming
            break
        }
    }
}
finally {
    Close-WebSocketQuietly -Socket $socket
    $socket.Dispose()
    $tokenSource.Dispose()
}
