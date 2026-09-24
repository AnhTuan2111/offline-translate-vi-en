<#
    Chay app tu ma nguon.

        .\scripts\run.ps1                      mo app
        .\scripts\run.ps1 -Query "give up"     mo san mot truy van
        .\scripts\run.ps1 -Query "cham soc" -Mode reverse
        .\scripts\run.ps1 -Rebuild             build lai dict.pack + index truoc khi chay

    Script tu lam ba viec: bien dich neu can, sinh du lieu neu chua co, roi chay app.
    Khong dung `mvn javafx:run` vi no cham hon va nuot mat stdout.
#>
param(
    [string]$Query,
    [ValidateSet("word", "sentence", "reverse")][string]$Mode = "word",
    [switch]$Rebuild
)
$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent
Set-Location $root

$source  = Join-Path $root "anhviet109K.txt"
$dataDir = Join-Path $root "data\build"
$pack    = Join-Path $dataDir "dict.pack"

# 1. Bien dich (bo qua test cho nhanh; chay test bang `mvn test`)
Write-Host "==> Bien dich..." -ForegroundColor Cyan
& mvn -q -DskipTests install
if ($LASTEXITCODE -ne 0) { throw "mvn install that bai" }

# 2. Sinh du lieu neu chua co
if ($Rebuild -or -not (Test-Path $pack)) {
    if (-not (Test-Path $source)) { throw "Khong thay $source" }
    Write-Host "==> Sinh dict.pack + index (mat khoang 5 giay)..." -ForegroundColor Cyan
    & java -Xmx3g -Dstdout.encoding=UTF-8 `
        -cp "dict-core\target\classes;dict-importer\target\classes" `
        com.anhtuan.dict.importer.cli.ImporterMain build $source $dataDir
    if ($LASTEXITCODE -ne 0) { throw "build du lieu that bai" }
}

# 3. Duong dan module cua JavaFX, lay thang tu kho Maven cuc bo
$m2 = Join-Path $env:USERPROFILE ".m2\repository\org\openjfx"
$fx = "25.0.4"
$jars = @("javafx-base", "javafx-graphics", "javafx-controls") | ForEach-Object {
    @("$m2\$_\$fx\$_-$fx.jar", "$m2\$_\$fx\$_-$fx-win.jar")
}
$missing = $jars | Where-Object { -not (Test-Path $_) }
if ($missing) { throw "Thieu jar JavaFX: $missing`nChay `mvn -pl app-desktop compile` truoc." }
$modulePath = $jars -join ";"

# 4. Chay
$jvm = @(
    "--module-path", $modulePath,
    "--add-modules", "javafx.controls",
    "--enable-native-access=javafx.graphics",
    "-cp", "app-desktop\target\classes;dict-core\target\classes"
)
if ($Query) { $jvm += "-Ddict.query=$Query"; $jvm += "-Ddict.mode=$Mode" }
$jvm += "com.anhtuan.dict.desktop.DictApp"

Write-Host "==> Chay app..." -ForegroundColor Cyan
& java @jvm
