$ErrorActionPreference = "Stop"

$root = "C:\\Users\\trist\\Desktop\\Hopper\\FilterFunnelPlus"
$jdk = "C:\\Users\\trist\\Desktop\\Athena\\jdk21\\jdk-21.0.7+6\\bin"
$javac = Join-Path $jdk "javac.exe"
$jar = Join-Path $jdk "jar.exe"
$serverJar = "C:\\Users\\trist\\Desktop\\Hopper\\Hytaleserver.jar"
$localServerJar = Join-Path $root "build\\Hytaleserver.jar"

$src = Join-Path $root "src\\main\\java"
$resources = Join-Path $root "src\\main\\resources"
$classes = Join-Path $root "build\\classes"
$jarRoot = Join-Path $root "build\\jarroot"
$outJar = Join-Path $root "build\\AthenaHopper.jar"

if (Test-Path $classes) { Remove-Item -Recurse -Force $classes }
if (Test-Path $jarRoot) { Remove-Item -Recurse -Force $jarRoot }

New-Item -ItemType Directory -Force -Path $classes | Out-Null
New-Item -ItemType Directory -Force -Path $jarRoot | Out-Null

Copy-Item -Force $serverJar $localServerJar

$javaFiles = Get-ChildItem -Recurse $src -Filter *.java | ForEach-Object { $_.FullName }
& $javac -encoding UTF8 -cp $localServerJar -d $classes @javaFiles

Copy-Item -Recurse -Force $resources\\* $jarRoot
Copy-Item -Recurse -Force $classes\\* $jarRoot

if (Test-Path $outJar) { Remove-Item -Force $outJar }
& $jar --create --file $outJar -C $jarRoot .

Write-Output "Built: $outJar"
