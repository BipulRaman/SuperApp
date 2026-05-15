# Generates SuperApp launcher icons.
# Output:
#   assets/icon.png            1024x1024  full bleed icon (rounded gradient bg + S monogram)
#   assets/adaptive-icon.png   1024x1024  Android adaptive foreground (transparent bg)
#   assets/splash.png          1242x2436  splash screen (dark bg + centered logo)
#   assets/favicon.png         196x196    web favicon
#
# Design: original SuperApp mark — three layered/fanned cards in white,
# with a pink+violet accent on the front card and two sparkle highlights.
# Conveys the "super app" concept (many apps stacked into one).
# White on a vibrant indigo -> violet -> pink diagonal gradient, on a 22%-radius
# rounded square. Inner content lives in the center 40% of the canvas
# (~30% padding all around) so it stays well inside Android's adaptive-icon
# safe zone.

Add-Type -AssemblyName System.Drawing

$ErrorActionPreference = 'Stop'

$root      = Split-Path -Parent $PSScriptRoot
$assetsDir = Join-Path $root 'assets'
New-Item -ItemType Directory -Force -Path $assetsDir | Out-Null

# ----- helpers ------------------------------------------------------------

function New-RoundedRectPath {
    param([float]$x, [float]$y, [float]$w, [float]$h, [float]$r)
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $d = $r * 2
    $path.AddArc($x, $y, $d, $d, 180, 90)
    $path.AddArc($x + $w - $d, $y, $d, $d, 270, 90)
    $path.AddArc($x + $w - $d, $y + $h - $d, $d, $d, 0, 90)
    $path.AddArc($x, $y + $h - $d, $d, $d, 90, 90)
    $path.CloseFigure()
    return $path
}

function New-Gfx {
    param($bitmap)
    $g = [System.Drawing.Graphics]::FromImage($bitmap)
    $g.SmoothingMode      = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.InterpolationMode  = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.PixelOffsetMode    = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g.TextRenderingHint  = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
    $g.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
    return $g
}

# A 4-pointed sparkle (star-like diamond) centered at (cx, cy).
# `r` is the long-axis radius; `t` controls the waist (smaller = pointier).
function New-SparklePath {
    param([float]$cx, [float]$cy, [float]$r, [float]$t = 0.22)
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $w = $r * $t
    $pts = New-Object 'System.Drawing.PointF[]' 8
    $pts[0] = New-Object System.Drawing.PointF([float]$cx,        [float]($cy - $r))
    $pts[1] = New-Object System.Drawing.PointF([float]($cx + $w), [float]($cy - $w))
    $pts[2] = New-Object System.Drawing.PointF([float]($cx + $r), [float]$cy)
    $pts[3] = New-Object System.Drawing.PointF([float]($cx + $w), [float]($cy + $w))
    $pts[4] = New-Object System.Drawing.PointF([float]$cx,        [float]($cy + $r))
    $pts[5] = New-Object System.Drawing.PointF([float]($cx - $w), [float]($cy + $w))
    $pts[6] = New-Object System.Drawing.PointF([float]($cx - $r), [float]$cy)
    $pts[7] = New-Object System.Drawing.PointF([float]($cx - $w), [float]($cy - $w))
    $path.AddPolygon($pts)
    $path.CloseFigure()
    return $path
}

# Draw the original SuperApp mark (three fanned cards + sparkles) inside
# (x, y, w, h). Conveys the "super app" concept — many apps stacked into one.
function Draw-SuperMark {
    param(
        [System.Drawing.Graphics]$g,
        [float]$x, [float]$y, [float]$w, [float]$h
    )

    # Cards are sized to ~78% of the available width and slightly portrait.
    $cardW = $w * 0.78
    $cardH = $h * 0.86
    $cx    = $x + $w / 2.0
    $cy    = $y + $h / 2.0

    # Card spec: angle (deg), x-offset, y-offset, fill alpha.
    # Drawn back-to-front so the front card sits on top.
    $cards = @(
        @{ angle = -18.0; dx = -$w * 0.10; dy = -$h * 0.04; alpha = 110 },
        @{ angle =  -6.0; dx = -$w * 0.03; dy = -$h * 0.02; alpha = 180 },
        @{ angle =   8.0; dx =  $w * 0.06; dy =  $h * 0.02; alpha = 255 }
    )

    foreach ($c in $cards) {
        $state = $g.Save()
        $g.TranslateTransform($cx + [float]$c.dx, $cy + [float]$c.dy)
        $g.RotateTransform([float]$c.angle)

        # Card geometry (centered on the current origin)
        $left = -$cardW / 2.0
        $top  = -$cardH / 2.0
        $card = New-RoundedRectPath $left $top $cardW $cardH ($cardW * 0.18)

        # Soft drop shadow for the front card to lift it off the gradient.
        if ([int]$c.alpha -ge 255) {
            $shadow = New-RoundedRectPath ($left + $w * 0.012) ($top + $h * 0.018) $cardW $cardH ($cardW * 0.18)
            $sb = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(70, 0, 0, 0))
            $g.FillPath($sb, $shadow)
            $sb.Dispose()
            $shadow.Dispose()
        }

        # Card fill — white with the requested alpha.
        $brush = New-Object System.Drawing.SolidBrush(
            [System.Drawing.Color]::FromArgb([int]$c.alpha, 255, 255, 255))
        $g.FillPath($brush, $card)
        $brush.Dispose()

        # Inner accent line on the front card only — gives it an "app screen" feel.
        if ([int]$c.alpha -ge 255) {
            $accentY = $top + $cardH * 0.30
            $accentH = $cardH * 0.10
            $accentW = $cardW * 0.55
            $accent  = New-RoundedRectPath ($left + ($cardW - $accentW) / 2.0) $accentY $accentW $accentH ($accentH / 2.0)
            $ab = New-Object System.Drawing.SolidBrush(
                [System.Drawing.Color]::FromArgb(255, 139, 92, 246))   # violet
            $g.FillPath($ab, $accent)
            $ab.Dispose()
            $accent.Dispose()

            # Two small dots beneath the accent bar
            $dotR = $cardH * 0.045
            $dotY = $top + $cardH * 0.55
            $dot1X = $left + $cardW * 0.32 - $dotR
            $dot2X = $left + $cardW * 0.62 - $dotR
            $db = New-Object System.Drawing.SolidBrush(
                [System.Drawing.Color]::FromArgb(255, 236, 72, 153))    # pink
            $g.FillEllipse($db, $dot1X, $dotY, $dotR * 2, $dotR * 2)
            $g.FillEllipse($db, $dot2X, $dotY, $dotR * 2, $dotR * 2)
            $db.Dispose()
        }

        $card.Dispose()
        $g.Restore($state)
    }

    # Sparkle accent at upper-right — gives the "super" personality.
    $sparkleR  = $w * 0.11
    $sparkleCx = $x + $w * 0.96
    $sparkleCy = $y + $h * 0.06
    $sparkle   = New-SparklePath -cx $sparkleCx -cy $sparkleCy -r $sparkleR
    $whiteB    = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::White)
    $g.FillPath($whiteB, $sparkle)
    $whiteB.Dispose()
    $sparkle.Dispose()

    # Tiny secondary sparkle at lower-left for balance.
    $sparkle2 = New-SparklePath -cx ([float]($x + $w * 0.04)) -cy ([float]($y + $h * 0.96)) -r ([float]($w * 0.06))
    $whiteB2  = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(220, 255, 255, 255))
    $g.FillPath($whiteB2, $sparkle2)
    $whiteB2.Dispose()
    $sparkle2.Dispose()
}

# ----- core renderer ------------------------------------------------------

function New-IconBitmap {
    param(
        [int]$size,                # canvas px
        [bool]$drawBackground,     # false => transparent background (for adaptive foreground)
        [float]$outerPaddingPct,   # transparent margin around the icon shape, per side
        [float]$innerPaddingPct    # padding from icon edge to the foreground mark, per side
    )

    $bmp = New-Object System.Drawing.Bitmap($size, $size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g   = New-Gfx $bmp
    $g.Clear([System.Drawing.Color]::Transparent)

    # The icon shape (rounded gradient square) lives inside this inset rect.
    $outer  = $size * $outerPaddingPct
    $iconX  = $outer
    $iconY  = $outer
    $iconSz = $size - 2 * $outer

    if ($drawBackground) {
        $bgPath  = New-RoundedRectPath $iconX $iconY $iconSz $iconSz ($iconSz * 0.22)
        $bgBrush = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
            (New-Object System.Drawing.PointF([float]$iconX, [float]$iconY)),
            (New-Object System.Drawing.PointF([float]($iconX + $iconSz), [float]($iconY + $iconSz))),
            [System.Drawing.Color]::FromArgb(255, 79, 70, 229),
            [System.Drawing.Color]::FromArgb(255, 236, 72, 153))
        $cb = New-Object System.Drawing.Drawing2D.ColorBlend(3)
        $cb.Colors = @(
            [System.Drawing.Color]::FromArgb(255, 79, 70, 229),
            [System.Drawing.Color]::FromArgb(255, 139, 92, 246),
            [System.Drawing.Color]::FromArgb(255, 236, 72, 153))
        $cb.Positions = @(0.0, 0.55, 1.0)
        $bgBrush.InterpolationColors = $cb
        $g.FillPath($bgBrush, $bgPath)
        $bgBrush.Dispose()

        $sheen = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
            (New-Object System.Drawing.PointF([float]$iconX, [float]$iconY)),
            (New-Object System.Drawing.PointF([float]($iconX + $iconSz), [float]($iconY + $iconSz))),
            [System.Drawing.Color]::FromArgb(60, 255, 255, 255),
            [System.Drawing.Color]::FromArgb(0, 255, 255, 255))
        $g.FillPath($sheen, $bgPath)
        $sheen.Dispose()
        $bgPath.Dispose()
    }

    # Foreground mark, inset further inside the icon shape.
    $inner   = $iconSz * $innerPaddingPct
    $markX   = $iconX + $inner
    $markY   = $iconY + $inner
    $markSz  = $iconSz - 2 * $inner
    Draw-SuperMark $g $markX $markY $markSz $markSz

    $g.Dispose()
    return $bmp
}

function Save-Png {
    param([System.Drawing.Bitmap]$bmp, [string]$path)
    $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    Write-Host ("Wrote {0}  ({1}x{2})" -f $path, $bmp.Width, $bmp.Height)
    $bmp.Dispose()
}

# ----- outputs ------------------------------------------------------------
# OUTER_PADDING = 0.15 → 15% transparent margin on every side
#                       (= 30% of the canvas dimension reserved as space around the icon)
# INNER_PADDING = 0.12 → small breathing room between the icon edge and the cards

$OUTER_PADDING = 0.15
$INNER_PADDING = 0.12

$icon = New-IconBitmap -size 1024 -drawBackground $true -outerPaddingPct $OUTER_PADDING -innerPaddingPct $INNER_PADDING
Save-Png $icon (Join-Path $assetsDir 'icon.png')

# Android adaptive foreground: transparent background, no extra outer margin
# (Android applies its own mask). Inner padding leaves room inside the safe zone.
$adaptive = New-IconBitmap -size 1024 -drawBackground $false -outerPaddingPct 0.0 -innerPaddingPct 0.27
Save-Png $adaptive (Join-Path $assetsDir 'adaptive-icon.png')

$splashW = 1242; $splashH = 2436
$splash  = New-Object System.Drawing.Bitmap($splashW, $splashH, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$gs = New-Gfx $splash
$gs.Clear([System.Drawing.Color]::FromArgb(255, 11, 16, 32))
$logo = New-IconBitmap -size 560 -drawBackground $true -outerPaddingPct 0.05 -innerPaddingPct 0.12
$gs.DrawImage($logo, ($splashW - $logo.Width) / 2, ($splashH - $logo.Height) / 2)
$gs.Dispose()
$logo.Dispose()
Save-Png $splash (Join-Path $assetsDir 'splash.png')

# Favicon stays edge-to-edge so it reads at 16px in browser tabs.
$fav = New-IconBitmap -size 196 -drawBackground $true -outerPaddingPct 0.0 -innerPaddingPct 0.12
Save-Png $fav (Join-Path $assetsDir 'favicon.png')

Write-Host "`nDone." -ForegroundColor Green
