param(
    [string]$Action = "poll",
    [string]$Source = "Auto",
    [string]$KnownKey = "",
    [long]$PositionMs = 0
)

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

[Console]::OutputEncoding =
    New-Object System.Text.UTF8Encoding($false)

try {
    Add-Type -AssemblyName System.Runtime.WindowsRuntime

    $managerType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager,Windows.Media.Control,ContentType=WindowsRuntime]
    $propsType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties,Windows.Media.Control,ContentType=WindowsRuntime]
    $streamType = [Windows.Storage.Streams.IRandomAccessStreamWithContentType,Windows.Storage.Streams,ContentType=WindowsRuntime]
    $readerType = [Windows.Storage.Streams.DataReader,Windows.Storage.Streams,ContentType=WindowsRuntime]

    $asTask = [System.WindowsRuntimeSystemExtensions].GetMethods() |
        Where-Object {
            $_.Name -eq "AsTask" -and
            $_.IsGenericMethodDefinition -and
            $_.GetGenericArguments().Count -eq 1 -and
            $_.GetParameters().Count -eq 1 -and
            $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
        } |
        Select-Object -First 1

    function Await($operation, [Type]$type) {
        $task = $asTask.MakeGenericMethod($type).Invoke(
            $null,
            @($operation)
        )

        return $task.GetAwaiter().GetResult()
    }

    $manager = Await ($managerType::RequestAsync()) $managerType
    $session = $null

    if ($Source -eq "Spotify Desktop") {
        $session = $manager.GetSessions() |
            Where-Object {
                $_.SourceAppUserModelId -match '(?i)spotify'
            } |
            Select-Object -First 1
    } else {
        $session = $manager.GetCurrentSession()
    }

    if ($null -eq $session) {
        @{
            available = $false
            status = "No media session"
        } | ConvertTo-Json -Compress

        exit
    }

    $info = $session.GetPlaybackInfo()
    $controls = $info.Controls

    if ($Action -ne "poll") {
        $request = $null

        switch ($Action) {
            "PREVIOUS" {
                if ($controls.IsPreviousEnabled) {
                    $request = $session.TrySkipPreviousAsync()
                }
            }

            "NEXT" {
                if ($controls.IsNextEnabled) {
                    $request = $session.TrySkipNextAsync()
                }
            }

            "TOGGLE" {
                if ($controls.IsPlayPauseToggleEnabled) {
                    $request = $session.TryTogglePlayPauseAsync()
                } elseif (
                    $info.PlaybackStatus.ToString() -eq "Playing" -and
                    $controls.IsPauseEnabled
                ) {
                    $request = $session.TryPauseAsync()
                } elseif ($controls.IsPlayEnabled) {
                    $request = $session.TryPlayAsync()
                }
            }

            "SEEK" {
                if (
                    $controls.IsPlaybackPositionEnabled -and
                    $PositionMs -ge 0
                ) {
                    $timeline = $session.GetTimelineProperties()

                    $target = $timeline.StartTime.Ticks +
                        $PositionMs * 10000

                    $target = [Math]::Max(
                        $timeline.MinSeekTime.Ticks,
                        [Math]::Min(
                            $timeline.MaxSeekTime.Ticks,
                            $target
                        )
                    )

                    $request = $session.TryChangePlaybackPositionAsync(
                        [long]$target
                    )
                }
            }
        }

        $ok = $false

        if ($null -ne $request) {
            $ok = Await $request ([bool])
        }

        @{
            success = $ok
        } | ConvertTo-Json -Compress

        exit
    }

    $properties = Await (
        $session.TryGetMediaPropertiesAsync()
    ) $propsType

    if ($null -eq $properties) {
        @{
            available = $false
            status = "Media metadata unavailable"
        } | ConvertTo-Json -Compress

        exit
    }

    $key = "$($session.SourceAppUserModelId)`n" +
           "$($properties.Title)`n" +
           "$($properties.Artist)`n" +
           "$($properties.AlbumTitle)"

    $key = [Convert]::ToBase64String(
        [Text.Encoding]::UTF8.GetBytes($key)
    )

    $art = $null

    if (
        $KnownKey -ne $key -and
        $null -ne $properties.Thumbnail
    ) {
        try {
            $stream = Await (
                $properties.Thumbnail.OpenReadAsync()
            ) $streamType

            try {
                if (
                    $stream.Size -gt 0 -and
                    $stream.Size -le 2097152
                ) {
                    $reader = $readerType::new(
                        $stream.GetInputStreamAt(0)
                    )

                    try {
                        $loaded = Await (
                            $reader.LoadAsync([uint32]$stream.Size)
                        ) ([uint32])

                        if ($loaded -gt 0) {
                            $bytes = New-Object byte[] $loaded
                            $reader.ReadBytes($bytes)

                            $art = [Convert]::ToBase64String(
                                $bytes
                            )
                        }
                    } finally {
                        $reader.Dispose()
                    }
                }
            } finally {
                $stream.Dispose()
            }
        } catch {
            $art = $null
        }
    }

    $timeline = $session.GetTimelineProperties()

    $duration = [long](
        $timeline.EndTime.TotalMilliseconds -
        $timeline.StartTime.TotalMilliseconds
    )

    $position = [long](
        $timeline.Position.TotalMilliseconds -
        $timeline.StartTime.TotalMilliseconds
    )

    if ($duration -le 0) {
        $duration = -1
        $position = -1
    }

    $playing =
        $info.PlaybackStatus.ToString() -eq "Playing"

    if (
        $playing -and
        $position -ge 0 -and
        $timeline.LastUpdatedTime.Year -gt 1970
    ) {
        $age = (
            [DateTimeOffset]::UtcNow -
            $timeline.LastUpdatedTime
        ).TotalMilliseconds

        if ($age -gt 0) {
            $position += [long]$age
        }

        if ($duration -gt 0) {
            $position = [Math]::Min($duration, $position)
        }
    }

    @{
        available = $true
        key = $key
        title = $properties.Title
        artist = $properties.Artist
        album = $properties.AlbumTitle
        art = $art
        playing = $playing
        position = $position
        duration = $duration
        previous = $controls.IsPreviousEnabled
        next = $controls.IsNextEnabled
        toggle = (
            $controls.IsPlayPauseToggleEnabled -or
            ($playing -and $controls.IsPauseEnabled) -or
            (!$playing -and $controls.IsPlayEnabled)
        )
        seek = $controls.IsPlaybackPositionEnabled
        status = $session.SourceAppUserModelId
    } | ConvertTo-Json -Compress

} catch {
    @{
        available = $false
        status = "Windows media unavailable"
    } | ConvertTo-Json -Compress
}
