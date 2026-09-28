Add-Type -AssemblyName System.Drawing

# ============================================================
# Glimmer APK icon generator (1024x1024 PNG)
# A glowing warm-gold firefly over deep indigo night
# ============================================================
$size = 1024
$bmp = New-Object System.Drawing.Bitmap($size, $size)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality

function C([int]$a, [int]$cr, [int]$cg, [int]$cb) { [System.Drawing.Color]::FromArgb($a, $cr, $cg, $cb) }

# ---------- 1. Background: deep indigo vertical gradient ----------
$bgRect = New-Object System.Drawing.Rectangle(0, 0, $size, $size)
$bgBrush = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
    $bgRect, (C 255 20 22 52), (C 255 46 30 74), 90.0)
$g.FillRectangle($bgBrush, $bgRect)
$bgBrush.Dispose()

# ---------- 2. Center radial halo ----------
function Draw-RadialGlow($cx, $cy, $radius, $a, $cr, $cg, $cb) {
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $path.AddEllipse($cx - $radius, $cy - $radius, $radius * 2, $radius * 2)
    $brush = New-Object System.Drawing.Drawing2D.PathGradientBrush($path)
    $brush.CenterColor = C $a $cr $cg $cb
    $brush.SurroundColors = @(C 0 $cr $cg $cb)
    $g.FillPath($brush, $path)
    $brush.Dispose()
    $path.Dispose()
}
Draw-RadialGlow 512 590 430 60 255 180 60
Draw-RadialGlow 512 620 280 110 255 196 70
Draw-RadialGlow 512 648 150 150 255 214 100

# ---------- 3. Wings ----------
$wingBrush = New-Object System.Drawing.SolidBrush(C 160 255 244 216)
$wingPen = New-Object System.Drawing.Pen((C 130 255 230 178), 4)
$g.TranslateTransform(408, 470)
$g.RotateTransform(-24)
$g.FillEllipse($wingBrush, -180, -58, 320, 116)
$g.DrawEllipse($wingPen, -180, -58, 320, 116)
$g.ResetTransform()
$g.TranslateTransform(616, 470)
$g.RotateTransform(24)
$g.FillEllipse($wingBrush, -140, -58, 320, 116)
$g.DrawEllipse($wingPen, -140, -58, 320, 116)
$g.ResetTransform()
$wingBrush.Dispose(); $wingPen.Dispose()

# ---------- 4. Body: dark capsule ----------
$bodyRect = New-Object System.Drawing.Rectangle(442, 384, 140, 300)
$bodyBrush = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
    $bodyRect, (C 255 74 63 116), (C 255 34 29 64), 90.0)
$gp = New-Object System.Drawing.Drawing2D.GraphicsPath
$gp.AddArc(442, 384, 140, 140, 180, 180)
$gp.AddArc(442, 544, 140, 140, 0, 180)
$gp.CloseFigure()
$g.FillPath($bodyBrush, $gp)
$bodyBrush.Dispose(); $gp.Dispose()

# ---------- 5. Glowing abdomen ----------
Draw-RadialGlow 512 636 150 200 255 210 90
$abdPath = New-Object System.Drawing.Drawing2D.GraphicsPath
$abdPath.AddEllipse(446, 550, 132, 176)
$abdBrush = New-Object System.Drawing.Drawing2D.PathGradientBrush($abdPath)
$abdBrush.CenterColor = C 255 255 240 178
$abdBrush.SurroundColors = @(C 255 255 168 42)
$g.FillPath($abdBrush, $abdPath)
$abdBrush.Dispose(); $abdPath.Dispose()
Draw-RadialGlow 488 606 34 190 255 248 214

# ---------- 6. Head ----------
$headBrush = New-Object System.Drawing.SolidBrush(C 255 58 49 96)
$g.FillEllipse($headBrush, 434, 308, 156, 156)
$headBrush.Dispose()
$eyeBrush = New-Object System.Drawing.SolidBrush(C 255 255 222 140)
Draw-RadialGlow 478 372 26 150 255 214 110
Draw-RadialGlow 546 372 26 150 255 214 110
$g.FillEllipse($eyeBrush, 468, 362, 20, 20)
$g.FillEllipse($eyeBrush, 536, 362, 20, 20)
$eyeBrush.Dispose()

# ---------- 7. Antennae with glowing tips ----------
$antPen = New-Object System.Drawing.Pen((C 210 255 236 190), 9)
$antPen.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
$antPen.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
$g.DrawBezier($antPen, 488, 318, 470, 262, 424, 246, 402, 200)
$g.DrawBezier($antPen, 536, 318, 554, 262, 600, 246, 622, 200)
$antPen.Dispose()
Draw-RadialGlow 402 200 24 200 255 224 130
Draw-RadialGlow 622 200 24 200 255 224 130

# ---------- 8. Sparkles and small dots ----------
function Draw-Spark($cx, $cy, $sr, $a, $cr, $cg, $cb) {
    $pts = New-Object 'System.Drawing.PointF[]' 8
    for ($i = 0; $i -lt 8; $i++) {
        $ang = [Math]::PI / 4 * $i - [Math]::PI / 2
        if ($i % 2 -eq 0) { $rad = $sr } else { $rad = $sr * 0.26 }
        $px = $cx + [Math]::Cos($ang) * $rad
        $py = $cy + [Math]::Sin($ang) * $rad
        $pts[$i] = New-Object System.Drawing.PointF($px, $py)
    }
    $sbr = New-Object System.Drawing.SolidBrush(C $a $cr $cg $cb)
    $g.FillPolygon($sbr, $pts)
    $sbr.Dispose()
}
Draw-Spark 236 268 30 220 255 220 130
Draw-Spark 800 244 38 200 255 214 110
Draw-Spark 818 672 22 180 255 224 140
Draw-Spark 208 712 32 180 255 214 110
Draw-Spark 712 838 20 160 255 226 150
Draw-Spark 312 846 16 150 255 230 160

$dotBrush = New-Object System.Drawing.SolidBrush(C 170 255 232 170)
$g.FillEllipse($dotBrush, 330, 210, 7, 7)
$g.FillEllipse($dotBrush, 690, 180, 6, 6)
$g.FillEllipse($dotBrush, 848, 420, 8, 8)
$g.FillEllipse($dotBrush, 168, 470, 6, 6)
$g.FillEllipse($dotBrush, 760, 760, 6, 6)
$g.FillEllipse($dotBrush, 260, 560, 5, 5)
$g.FillEllipse($dotBrush, 600, 140, 5, 5)
$dotBrush.Dispose()

# ---------- 9. Save PNG ----------
$out = "d:\ideaspace\glimmer\frontend\public\assets\app-icon.png"
$bmp.Save($out, [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $bmp.Dispose()
Write-Host ("saved: " + $out)
