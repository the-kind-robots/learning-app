param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("start", "run")]
    [string]$Mode,

    [Parameter(Mandatory = $true)]
    [string]$WebSocketUrl,

    [Parameter(Mandatory = $true)]
    [string]$BufferPath,

    [Parameter(Mandatory = $true)]
    [string]$StatePath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Ensure-ParentDirectory {
    param([Parameter(Mandatory = $true)][string]$Path)
    $parent = Split-Path -Parent $Path
    if ($parent -and -not (Test-Path -LiteralPath $parent)) {
        New-Item -ItemType Directory -Path $parent -Force | Out-Null
    }
}

function Append-JsonLine {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path,

        [Parameter(Mandatory = $true)]
        [object]$Object
    )

    $json = $Object | ConvertTo-Json -Depth 20 -Compress
    Add-Content -LiteralPath $Path -Value $json -Encoding UTF8
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
            throw "WebSocket closed."
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

function Write-StateFile {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path,

        [Parameter(Mandatory = $true)]
        [int]$ProcessId,

        [Parameter(Mandatory = $true)]
        [string]$WsUrl,

        [Parameter(Mandatory = $true)]
        [string]$Buffer
    )

    $state = [ordered]@{
        pid = $ProcessId
        webSocketUrl = $WsUrl
        bufferPath = $Buffer
        startedAt = [DateTime]::UtcNow.ToString("o")
    }

    $state | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $Path -Encoding UTF8
}

function Start-Monitor {
    Ensure-ParentDirectory -Path $BufferPath
    Ensure-ParentDirectory -Path $StatePath
    if (-not (Test-Path -LiteralPath $BufferPath)) {
        New-Item -ItemType File -Path $BufferPath -Force | Out-Null
    }

    $child = Start-Process powershell.exe -WindowStyle Hidden -PassThru -ArgumentList @(
        '-NoProfile',
        '-ExecutionPolicy', 'Bypass',
        '-File', $PSCommandPath,
        '-Mode', 'run',
        '-WebSocketUrl', $WebSocketUrl,
        '-BufferPath', $BufferPath,
        '-StatePath', $StatePath
    )

    for ($i = 0; $i -lt 40; $i++) {
        Start-Sleep -Milliseconds 250

        if (-not (Get-Process -Id $child.Id -ErrorAction SilentlyContinue)) {
            throw "Monitor process exited before it connected."
        }

        if (Test-Path -LiteralPath $StatePath) {
            try {
                $state = Get-Content -LiteralPath $StatePath -Raw | ConvertFrom-Json
                if ($state.pid -eq $child.Id -and $state.startedAt) {
                    $connected = $false
                    if (Test-Path -LiteralPath $BufferPath) {
                        $lines = Get-Content -LiteralPath $BufferPath -ErrorAction SilentlyContinue
                        foreach ($line in $lines) {
                            if (-not [string]::IsNullOrWhiteSpace($line)) {
                                $entry = $line | ConvertFrom-Json
                                if ($entry.kind -eq 'lifecycle' -and $entry.event -eq 'connected') {
                                    $connected = $true
                                    break
                                }
                            }
                        }
                    }

                    if ($connected) {
                        Get-Content -LiteralPath $StatePath
                        return
                    }
                }
            }
            catch {
                # Keep waiting while the child process is starting up.
            }
        }
    }

    throw "Monitor did not confirm websocket connection in time."
}

function Run-Monitor {
    Ensure-ParentDirectory -Path $BufferPath
    Ensure-ParentDirectory -Path $StatePath

    $socket = [System.Net.WebSockets.ClientWebSocket]::new()
    $cts = [System.Threading.CancellationTokenSource]::new()
    $cts.CancelAfter([TimeSpan]::FromSeconds(30))

    try {
        [void]$socket.ConnectAsync([System.Uri]::new($WebSocketUrl), $cts.Token).GetAwaiter().GetResult()
        Write-StateFile -Path $StatePath -ProcessId $PID -WsUrl $WebSocketUrl -Buffer $BufferPath
        Append-JsonLine -Path $BufferPath -Object ([ordered]@{
            ts = [DateTime]::UtcNow.ToString("o")
            kind = "lifecycle"
            event = "connected"
        })

        Send-Cdp -Socket $socket -Id 1 -Method "Runtime.enable" -Token $cts.Token
        Send-Cdp -Socket $socket -Id 2 -Method "Log.enable" -Token $cts.Token
        Send-Cdp -Socket $socket -Id 3 -Method "Network.enable" -Token $cts.Token
        Send-Cdp -Socket $socket -Id 4 -Method "Page.enable" -Token $cts.Token
        $cts.CancelAfter([TimeSpan]::FromDays(1))

        while ($true) {
            $incoming = Receive-CompleteMessage -Socket $socket -Token $cts.Token
            $parsed = $incoming | ConvertFrom-Json

            if ($null -eq $parsed.PSObject.Properties['method']) {
                continue
            }

            $ts = [DateTime]::UtcNow.ToString("o")
            switch ($parsed.method) {
                'Runtime.consoleAPICalled' {
                    $args = @()
                    foreach ($arg in $parsed.params.args) {
                        $args += ,(Normalize-RemoteObject $arg)
                    }
                    $frame = $null
                    if ($parsed.params.stackTrace -and $parsed.params.stackTrace.callFrames.Count -gt 0) {
                        $frame = $parsed.params.stackTrace.callFrames[0]
                    }
                    Append-JsonLine -Path $BufferPath -Object ([ordered]@{
                        ts = $ts
                        kind = "console"
                        method = $parsed.method
                        level = $parsed.params.type
                        args = $args
                        url = if ($null -ne $frame) { $frame.url } else { $null }
                        lineNumber = if ($null -ne $frame) { $frame.lineNumber } else { $null }
                        columnNumber = if ($null -ne $frame) { $frame.columnNumber } else { $null }
                    })
                }
                'Log.entryAdded' {
                    $entry = $parsed.params.entry
                    Append-JsonLine -Path $BufferPath -Object ([ordered]@{
                        ts = $ts
                        kind = "log"
                        method = $parsed.method
                        level = $entry.level
                        source = $entry.source
                        text = $entry.text
                        url = $entry.url
                        lineNumber = if ($null -ne $entry.PSObject.Properties['lineNumber']) { $entry.lineNumber } else { $null }
                    })
                }
                'Runtime.exceptionThrown' {
                    $details = $parsed.params.exceptionDetails
                    Append-JsonLine -Path $BufferPath -Object ([ordered]@{
                        ts = $ts
                        kind = "exception"
                        method = $parsed.method
                        text = $details.text
                        url = $details.url
                        lineNumber = $details.lineNumber
                        columnNumber = $details.columnNumber
                        exception = Normalize-RemoteObject $details.exception
                    })
                }
                'Network.requestWillBeSent' {
                    $request = $parsed.params.request
                    Append-JsonLine -Path $BufferPath -Object ([ordered]@{
                        ts = $ts
                        kind = "network"
                        method = $parsed.method
                        requestId = $parsed.params.requestId
                        url = $request.url
                        httpMethod = $request.method
                        type = $parsed.params.type
                    })
                }
                'Network.responseReceived' {
                    $response = $parsed.params.response
                    Append-JsonLine -Path $BufferPath -Object ([ordered]@{
                        ts = $ts
                        kind = "network"
                        method = $parsed.method
                        requestId = $parsed.params.requestId
                        url = $response.url
                        status = $response.status
                        statusText = $response.statusText
                        mimeType = $response.mimeType
                        type = $parsed.params.type
                    })
                }
                'Network.loadingFailed' {
                    Append-JsonLine -Path $BufferPath -Object ([ordered]@{
                        ts = $ts
                        kind = "network"
                        method = $parsed.method
                        requestId = $parsed.params.requestId
                        errorText = $parsed.params.errorText
                        canceled = $parsed.params.canceled
                        type = $parsed.params.type
                    })
                }
            }
        }
    }
    catch {
        Append-JsonLine -Path $BufferPath -Object ([ordered]@{
            ts = [DateTime]::UtcNow.ToString("o")
            kind = "lifecycle"
            event = "closed"
            message = $_.Exception.Message
        })
        throw
    }
    finally {
        if ($socket.State -eq [System.Net.WebSockets.WebSocketState]::Open) {
            [void]$socket.CloseAsync(
                [System.Net.WebSockets.WebSocketCloseStatus]::NormalClosure,
                "done",
                [System.Threading.CancellationToken]::None
            ).GetAwaiter().GetResult()
        }

        $socket.Dispose()
        $cts.Dispose()
    }
}

switch ($Mode) {
    "start" { Start-Monitor }
    "run" { Run-Monitor }
}
