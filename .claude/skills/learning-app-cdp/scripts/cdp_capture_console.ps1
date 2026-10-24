param(
    [Parameter(Mandatory = $true)]
    [string]$WebSocketUrl,

    [int]$TimeoutSeconds = 5
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
            throw "WebSocket closed before capture completed."
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

function Send-Cdp {
    param(
        [Parameter(Mandatory = $true)]
        [System.Net.WebSockets.ClientWebSocket]$Socket,

        [Parameter(Mandatory = $true)]
        [int]$Id,

        [Parameter(Mandatory = $true)]
        [string]$Method,

        [string]$ParamsJson = "{}",

        [Parameter(Mandatory = $true)]
        [System.Threading.CancellationToken]$Token
    )

    $methodJson = ConvertTo-Json $Method -Compress
    $message = '{"id":' + $Id + ',"method":' + $methodJson + ',"params":' + $ParamsJson + '}'
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($message)
    $segment = New-Object System.ArraySegment[byte] -ArgumentList @(,$bytes)
    [void]$Socket.SendAsync(
        $segment,
        [System.Net.WebSockets.WebSocketMessageType]::Text,
        $true,
        $Token
    ).GetAwaiter().GetResult()
}

function Normalize-RemoteObject {
    param([object]$Obj)

    if ($null -eq $Obj) { return $null }
    if ($null -ne $Obj.PSObject.Properties['value']) { return $Obj.value }
    if ($null -ne $Obj.PSObject.Properties['description']) { return $Obj.description }
    if ($null -ne $Obj.PSObject.Properties['unserializableValue']) { return $Obj.unserializableValue }

    return [ordered]@{
        type = if ($null -ne $Obj.PSObject.Properties['type']) { $Obj.type } else { $null }
        subtype = if ($null -ne $Obj.PSObject.Properties['subtype']) { $Obj.subtype } else { $null }
        className = if ($null -ne $Obj.PSObject.Properties['className']) { $Obj.className } else { $null }
    }
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
        # Ignore shutdown noise after the capture window is already complete.
    }
}

$uri = [System.Uri]::new($WebSocketUrl)
$socket = [System.Net.WebSockets.ClientWebSocket]::new()
$connectSource = [System.Threading.CancellationTokenSource]::new()
$connectSource.CancelAfter([TimeSpan]::FromSeconds([Math]::Max(5, $TimeoutSeconds)))
$result = [ordered]@{
    console = @()
    exceptions = @()
}
$deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)

try {
    [void]$socket.ConnectAsync($uri, $connectSource.Token).GetAwaiter().GetResult()

    Send-Cdp -Socket $socket -Id 1 -Method "Runtime.enable" -Token $connectSource.Token
    Send-Cdp -Socket $socket -Id 2 -Method "Log.enable" -Token $connectSource.Token
    Send-Cdp -Socket $socket -Id 3 -Method "Page.enable" -Token $connectSource.Token
    Send-Cdp -Socket $socket -Id 4 -Method "Page.reload" -ParamsJson '{"ignoreCache":true}' -Token $connectSource.Token

    while ([DateTime]::UtcNow -lt $deadline) {
        $remaining = [Math]::Max(1, [int][Math]::Ceiling(($deadline - [DateTime]::UtcNow).TotalMilliseconds))
        $loopSource = [System.Threading.CancellationTokenSource]::new()
        $loopSource.CancelAfter($remaining)

        try {
            $incoming = Receive-CompleteMessage -Socket $socket -Token $loopSource.Token
        }
        catch [System.OperationCanceledException] {
            break
        }
        finally {
            $loopSource.Dispose()
        }

        $parsed = $incoming | ConvertFrom-Json

        if ($null -ne $parsed.PSObject.Properties['method'] -and $parsed.method -eq 'Runtime.consoleAPICalled') {
            $type = $parsed.params.type
            if ($type -eq 'warning' -or $type -eq 'error') {
                $args = @()
                foreach ($arg in $parsed.params.args) {
                    $args += ,(Normalize-RemoteObject $arg)
                }

                $frame = $null
                if ($parsed.params.stackTrace -and $parsed.params.stackTrace.callFrames.Count -gt 0) {
                    $frame = $parsed.params.stackTrace.callFrames[0]
                }

                $result.console += [ordered]@{
                    source = 'Runtime.consoleAPICalled'
                    level = $type
                    args = $args
                    url = if ($null -ne $frame) { $frame.url } else { $null }
                    lineNumber = if ($null -ne $frame) { $frame.lineNumber } else { $null }
                    columnNumber = if ($null -ne $frame) { $frame.columnNumber } else { $null }
                }
            }
            continue
        }

        if ($null -ne $parsed.PSObject.Properties['method'] -and $parsed.method -eq 'Log.entryAdded') {
            $entry = $parsed.params.entry
            if ($entry.level -eq 'warning' -or $entry.level -eq 'error') {
                $result.console += [ordered]@{
                    source = 'Log.entryAdded'
                    level = $entry.level
                    text = $entry.text
                    url = $entry.url
                    lineNumber = if ($null -ne $entry.PSObject.Properties['lineNumber']) { $entry.lineNumber } else { $null }
                }
            }
            continue
        }

        if ($null -ne $parsed.PSObject.Properties['method'] -and $parsed.method -eq 'Runtime.exceptionThrown') {
            $details = $parsed.params.exceptionDetails
            $result.exceptions += [ordered]@{
                text = $details.text
                url = $details.url
                lineNumber = $details.lineNumber
                columnNumber = $details.columnNumber
                exception = Normalize-RemoteObject $details.exception
            }
        }
    }

    $result | ConvertTo-Json -Depth 10
}
finally {
    Close-WebSocketQuietly -Socket $socket
    $socket.Dispose()
    $connectSource.Dispose()
}
