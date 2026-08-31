param(
  [string] $OutputDirectory = (Join-Path (Split-Path -Parent $PSScriptRoot) 'src/main/resources/assets/siliconic/textures/block')
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

function Get-Color([string] $Hex) {
  return [System.Drawing.ColorTranslator]::FromHtml($Hex)
}

function New-Texture {
  $bitmap = [System.Drawing.Bitmap]::new(
    16,
    16,
    [System.Drawing.Imaging.PixelFormat]::Format32bppArgb
  )
  $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
  $graphics.Clear([System.Drawing.Color]::Transparent)
  $graphics.Dispose()
  return $bitmap
}

function Set-Texel($Bitmap, [int] $X, [int] $Y, $Color) {
  if ($X -ge 0 -and $X -lt 16 -and $Y -ge 0 -and $Y -lt 16) {
    $Bitmap.SetPixel($X, $Y, $Color)
  }
}

function Fill-Rect($Bitmap, [int] $X, [int] $Y, [int] $Width, [int] $Height, $Color) {
  for ($py = $Y; $py -lt $Y + $Height; $py++) {
    for ($px = $X; $px -lt $X + $Width; $px++) {
      Set-Texel $Bitmap $px $py $Color
    }
  }
}

function Draw-Rect($Bitmap, [int] $X, [int] $Y, [int] $Width, [int] $Height, $Color) {
  Fill-Rect $Bitmap $X $Y $Width 1 $Color
  Fill-Rect $Bitmap $X ($Y + $Height - 1) $Width 1 $Color
  Fill-Rect $Bitmap $X $Y 1 $Height $Color
  Fill-Rect $Bitmap ($X + $Width - 1) $Y 1 $Height $Color
}

function Save-Texture($Bitmap, [string] $Name) {
  $path = Join-Path $OutputDirectory "$Name.png"
  $Bitmap.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
  $Bitmap.Dispose()
}

function Draw-Bolts($Bitmap, $Color) {
  foreach ($point in @(@(1, 1), @(14, 1), @(1, 14), @(14, 14))) {
    Set-Texel $Bitmap $point[0] $point[1] $Color
  }
}

$c = @{
  Ink = Get-Color '#111418'
  Deep = Get-Color '#1d2227'
  Shadow = Get-Color '#292f35'
  Iron = Get-Color '#3b4249'
  LightIron = Get-Color '#59626b'
  Bolt = Get-Color '#929aa1'
  Rust = Get-Color '#765137'
  Copper = Get-Color '#a96432'
  BrightCopper = Get-Color '#db8a49'
  FireDark = Get-Color '#651d0b'
  Fire = Get-Color '#e64b0b'
  FireBright = Get-Color '#ffbe22'
  AcidDark = Get-Color '#4d560d'
  Acid = Get-Color '#9caa1c'
  AcidBright = Get-Color '#e1f139'
  CyanDark = Get-Color '#174b56'
  Cyan = Get-Color '#36a6b5'
  CyanBright = Get-Color '#8ff3f3'
  Quartz = Get-Color '#c4c6ce'
  VioletDark = Get-Color '#4e285f'
  Violet = Get-Color '#9a50be'
  VioletBright = Get-Color '#e5a3ff'
  GreenDark = Get-Color '#285015'
  Green = Get-Color '#65a928'
  GreenBright = Get-Color '#b1f24d'
}

New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null

# Shared rough frame: dirty plate seams, exposed fasteners, and restrained rust.
$texture = New-Texture
Fill-Rect $texture 0 0 16 16 $c.Ink
Fill-Rect $texture 1 1 14 14 $c.Iron
Fill-Rect $texture 2 2 12 12 $c.Shadow
Fill-Rect $texture 3 3 10 10 $c.Iron
Fill-Rect $texture 7 2 2 12 $c.Deep
Fill-Rect $texture 2 7 12 2 $c.Deep
Fill-Rect $texture 3 3 4 1 $c.LightIron
Fill-Rect $texture 9 9 4 1 $c.LightIron
Set-Texel $texture 4 11 $c.Rust
Set-Texel $texture 11 4 $c.Rust
Set-Texel $texture 12 12 $c.LightIron
Draw-Bolts $texture $c.Bolt
Save-Texture $texture 'raw_machine_frame_side'

$texture = New-Texture
Fill-Rect $texture 0 0 16 16 $c.Ink
Fill-Rect $texture 1 1 14 14 $c.Iron
Fill-Rect $texture 2 2 12 12 $c.Shadow
Draw-Rect $texture 4 4 8 8 $c.Deep
Draw-Rect $texture 5 5 6 6 $c.LightIron
Fill-Rect $texture 7 2 2 3 $c.Deep
Fill-Rect $texture 7 11 2 3 $c.Deep
Fill-Rect $texture 2 7 3 2 $c.Deep
Fill-Rect $texture 11 7 3 2 $c.Deep
Set-Texel $texture 3 4 $c.Rust
Set-Texel $texture 12 10 $c.Rust
Draw-Bolts $texture $c.Bolt
Save-Texture $texture 'raw_machine_frame_top'

$texture = New-Texture
Fill-Rect $texture 0 0 16 16 $c.Ink
Fill-Rect $texture 1 1 14 14 $c.Shadow
Fill-Rect $texture 2 2 12 12 $c.Deep
for ($i = 3; $i -le 12; $i++) {
  Set-Texel $texture $i $i $c.Iron
  Set-Texel $texture (15 - $i) $i $c.Iron
}
Draw-Rect $texture 5 5 6 6 $c.LightIron
Fill-Rect $texture 6 6 4 4 $c.Ink
Draw-Bolts $texture $c.Bolt
Save-Texture $texture 'raw_machine_frame_bottom'

function Write-ArcFurnaceFront([bool] $Active) {
  $texture = New-Texture
  Fill-Rect $texture 5 1 2 5 $c.Copper
  Fill-Rect $texture 9 1 2 5 $c.Copper
  Set-Texel $texture 5 1 $c.BrightCopper
  Set-Texel $texture 9 1 $c.BrightCopper
  Fill-Rect $texture 5 5 6 1 $c.LightIron
  Fill-Rect $texture 4 6 8 1 $c.LightIron
  Fill-Rect $texture 3 7 10 7 $c.LightIron
  Fill-Rect $texture 4 8 8 6 $c.Ink
  Fill-Rect $texture 5 7 6 1 $c.Ink
  if ($Active) {
    Fill-Rect $texture 5 10 6 4 $c.FireDark
    Fill-Rect $texture 6 11 4 3 $c.Fire
    Fill-Rect $texture 7 12 2 2 $c.FireBright
  } else {
    Fill-Rect $texture 5 11 6 3 $c.Deep
  }
  Save-Texture $texture ($(if ($Active) { 'silicon_arc_furnace_front_active' } else { 'silicon_arc_furnace_front' }))
}

Write-ArcFurnaceFront $false
Write-ArcFurnaceFront $true
$texture = New-Texture
Fill-Rect $texture 4 2 3 10 $c.Copper
Fill-Rect $texture 9 2 3 10 $c.Copper
Fill-Rect $texture 5 3 1 8 $c.BrightCopper
Fill-Rect $texture 10 3 1 8 $c.BrightCopper
Draw-Rect $texture 3 12 10 2 $c.LightIron
Save-Texture $texture 'silicon_arc_furnace_top'

function Write-ChlorinationFront([bool] $Active) {
  $texture = New-Texture
  Draw-Rect $texture 3 2 7 12 $c.LightIron
  Fill-Rect $texture 4 3 5 10 $c.Deep
  Fill-Rect $texture 5 4 3 8 ($(if ($Active) { $c.AcidBright } else { $c.AcidDark }))
  Fill-Rect $texture 1 5 2 2 $c.Acid
  Fill-Rect $texture 9 9 5 2 $c.Acid
  Draw-Rect $texture 10 4 4 4 $c.LightIron
  Fill-Rect $texture 11 5 2 2 ($(if ($Active) { $c.AcidBright } else { $c.Acid }))
  Set-Texel $texture 12 4 $c.Bolt
  Save-Texture $texture ($(if ($Active) { 'chlorination_reactor_front_active' } else { 'chlorination_reactor_front' }))
}

Write-ChlorinationFront $false
Write-ChlorinationFront $true
$texture = New-Texture
Fill-Rect $texture 7 1 2 14 $c.Acid
Fill-Rect $texture 1 7 14 2 $c.Acid
Draw-Rect $texture 4 4 8 8 $c.LightIron
Draw-Rect $texture 6 6 4 4 $c.AcidBright
Set-Texel $texture 7 7 $c.Deep
Set-Texel $texture 8 8 $c.Deep
Save-Texture $texture 'chlorination_reactor_top'

function Write-DistillationFront([bool] $Active) {
  $texture = New-Texture
  Fill-Rect $texture 3 1 6 14 $c.LightIron
  Fill-Rect $texture 4 2 4 12 $c.Iron
  foreach ($y in @(3, 6, 9, 12)) {
    Fill-Rect $texture 2 $y 8 2 $c.Deep
    Fill-Rect $texture 3 $y 6 1 $c.Bolt
  }
  Draw-Rect $texture 11 3 3 10 $c.LightIron
  Fill-Rect $texture 12 4 1 8 ($(if ($Active) { $c.CyanBright } else { $c.CyanDark }))
  Fill-Rect $texture 9 5 2 1 $c.Copper
  Fill-Rect $texture 9 10 2 1 $c.Copper
  Save-Texture $texture ($(if ($Active) { 'distillation_tower_front_active' } else { 'distillation_tower_front' }))
}

Write-DistillationFront $false
Write-DistillationFront $true
$texture = New-Texture
Draw-Rect $texture 3 3 10 10 $c.LightIron
Draw-Rect $texture 5 5 6 6 $c.Iron
Draw-Rect $texture 7 7 2 2 $c.Cyan
Fill-Rect $texture 12 7 3 2 $c.Copper
Save-Texture $texture 'distillation_tower_top'

function Write-SiemensFront([bool] $Active) {
  $texture = New-Texture
  Fill-Rect $texture 2 2 12 2 $c.Deep
  Fill-Rect $texture 2 12 12 2 $c.Deep
  foreach ($x in @(4, 7, 10)) {
    Fill-Rect $texture $x 3 2 10 ($(if ($Active) { $c.VioletBright } else { $c.Quartz }))
    Set-Texel $texture $x 3 $c.Violet
    Set-Texel $texture ($x + 1) 12 $c.VioletDark
  }
  if ($Active) {
    Set-Texel $texture 3 7 $c.Violet
    Set-Texel $texture 12 7 $c.Violet
  }
  Save-Texture $texture ($(if ($Active) { 'siemens_reactor_front_active' } else { 'siemens_reactor_front' }))
}

Write-SiemensFront $false
Write-SiemensFront $true
$texture = New-Texture
Draw-Rect $texture 3 3 10 10 $c.LightIron
foreach ($point in @(@(5, 5), @(10, 5), @(5, 10), @(10, 10))) {
  Set-Texel $texture $point[0] $point[1] $c.Violet
  Set-Texel $texture ($point[0] - 1) $point[1] $c.VioletDark
}
Draw-Rect $texture 6 6 4 4 $c.Quartz
Save-Texture $texture 'siemens_reactor_top'

function Write-RecyclerFront([bool] $Active) {
  $texture = New-Texture
  $accent = $(if ($Active) { $c.GreenBright } else { $c.Green })
  Draw-Rect $texture 3 3 10 10 $c.LightIron
  Fill-Rect $texture 5 4 5 2 $accent
  Fill-Rect $texture 4 5 2 4 $accent
  Set-Texel $texture 4 4 $accent
  Set-Texel $texture 10 5 $accent
  Fill-Rect $texture 6 10 5 2 $accent
  Fill-Rect $texture 10 7 2 4 $accent
  Set-Texel $texture 11 11 $accent
  Set-Texel $texture 5 10 $accent
  Fill-Rect $texture 1 7 2 2 $c.GreenDark
  Fill-Rect $texture 13 7 2 2 $c.GreenDark
  Save-Texture $texture ($(if ($Active) { 'chemical_recycler_front_active' } else { 'chemical_recycler_front' }))
}

Write-RecyclerFront $false
Write-RecyclerFront $true
$texture = New-Texture
Draw-Rect $texture 3 3 10 10 $c.LightIron
Fill-Rect $texture 5 4 5 2 $c.Green
Fill-Rect $texture 4 5 2 5 $c.Green
Set-Texel $texture 10 5 $c.Green
Fill-Rect $texture 6 10 5 2 $c.Green
Fill-Rect $texture 10 7 2 4 $c.Green
Set-Texel $texture 5 10 $c.Green
Save-Texture $texture 'chemical_recycler_top'

Write-Output "Generated front-process textures in $OutputDirectory"
