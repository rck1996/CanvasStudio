param(
    [string]$InputPath = "app/src/main/res/drawable-nodpi/ic_launcher_foreground.png",
    [string]$OutputPath = "app/src/main/res/drawable-nodpi/ic_launcher_monochrome.png"
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing

$sourcePath = (Resolve-Path -LiteralPath $InputPath).Path
$source = [System.Drawing.Bitmap]::FromFile($sourcePath)
$output = New-Object System.Drawing.Bitmap(
    $source.Width,
    $source.Height,
    [System.Drawing.Imaging.PixelFormat]::Format32bppArgb
)

try {
    # Identify only the near-white area connected to the image edges. This removes
    # the exported canvas while preserving the light nib details inside the logo.
    $outside = New-Object "bool[,]" $source.Width, $source.Height
    $queue = [System.Collections.Generic.Queue[int]]::new()

    function Test-CanvasPixel([System.Drawing.Color]$color) {
        $minimum = [Math]::Min($color.R, [Math]::Min($color.G, $color.B))
        $maximum = [Math]::Max($color.R, [Math]::Max($color.G, $color.B))
        return $minimum -ge 235 -and ($maximum - $minimum) -le 16
    }

    function Add-CanvasPixel([int]$x, [int]$y) {
        if ($x -lt 0 -or $x -ge $source.Width -or $y -lt 0 -or $y -ge $source.Height) {
            return
        }
        if ($outside[$x, $y] -or -not (Test-CanvasPixel $source.GetPixel($x, $y))) {
            return
        }
        $outside[$x, $y] = $true
        $queue.Enqueue($y * $source.Width + $x)
    }

    for ($x = 0; $x -lt $source.Width; $x++) {
        Add-CanvasPixel $x 0
        Add-CanvasPixel $x ($source.Height - 1)
    }
    for ($y = 0; $y -lt $source.Height; $y++) {
        Add-CanvasPixel 0 $y
        Add-CanvasPixel ($source.Width - 1) $y
    }

    while ($queue.Count -gt 0) {
        $index = $queue.Dequeue()
        $x = $index % $source.Width
        $y = [Math]::Floor($index / $source.Width)
        Add-CanvasPixel ($x - 1) $y
        Add-CanvasPixel ($x + 1) $y
        Add-CanvasPixel $x ($y - 1)
        Add-CanvasPixel $x ($y + 1)
    }

    for ($y = 0; $y -lt $source.Height; $y++) {
        for ($x = 0; $x -lt $source.Width; $x++) {
            if ($outside[$x, $y]) {
                $output.SetPixel($x, $y, [System.Drawing.Color]::Transparent)
                continue
            }

            $color = $source.GetPixel($x, $y)
            $luminance = (0.2126 * $color.R) + (0.7152 * $color.G) + (0.0722 * $color.B)
            $alpha = [Math]::Min(255, [Math]::Max(48, [Math]::Round(
                48 + (207 * [Math]::Pow($luminance / 255.0, 0.85))
            )))
            $output.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($alpha, 255, 255, 255))
        }
    }

    $destination = [System.IO.Path]::GetFullPath($OutputPath)
    $output.Save($destination, [System.Drawing.Imaging.ImageFormat]::Png)
    Write-Output "Generated $destination from the exact final icon artwork."
}
finally {
    $output.Dispose()
    $source.Dispose()
}
