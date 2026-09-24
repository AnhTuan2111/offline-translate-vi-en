<#
    Dong goi thanh ban chay duoc: mot thu muc co san file .exe, khong can cai Java.

        .\scripts\dong-goi.ps1            tao dist\TuDienOffline\TuDienOffline.exe
        .\scripts\dong-goi.ps1 -Zip       tao them file .zip de gui cho nguoi khac

    Ghi chu ve kieu dong goi (PLAN.md AD-11, M8):
      --type app-image  -> mot THU MUC chua san .exe + JRE. Khong can cai dat gi them,
                           chay ngay. Day la thu script nay tao.
      --type msi / exe  -> bo cai dat that su. Can WiX Toolset v3.11 cai san tren may;
                           chua co thi jpackage bao loi. Cai WiX xong thi doi -Type.
#>
param(
    [ValidateSet("app-image", "msi", "exe")][string]$Type = "app-image",
    [switch]$Zip
)
$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent
Set-Location $root

$appName = "TuDienOffline"
$dist    = Join-Path $root "dist"
$stage   = Join-Path $dist "input"
$dataDir = Join-Path $root "data\build"

if (-not (Test-Path (Join-Path $dataDir "dict.pack"))) {
    throw "Chua co du lieu. Chay truoc:  .\scripts\run.ps1 -Rebuild"
}

Write-Host "==> Bien dich va dong goi jar..." -ForegroundColor Cyan
& mvn -q -DskipTests package
if ($LASTEXITCODE -ne 0) { throw "mvn package that bai" }

# --- dung thu muc nguyen lieu ---
if (Test-Path $stage) { Remove-Item $stage -Recurse -Force }
New-Item -ItemType Directory -Force -Path $stage, "$stage\javafx", "$stage\data" | Out-Null

Copy-Item "app-desktop\target\app-desktop-*.jar" "$stage\app-desktop.jar"
Copy-Item "dict-core\target\dict-core-*.jar"     "$stage\dict-core.jar"
Copy-Item "$dataDir\*"                            "$stage\data\"

# JavaFX: jpackage nem MOI jar trong thu muc dau vao (ke ca thu muc con) vao classpath,
# nen khong the vua de day vua khai bao module path - trung module thi app chet im lang.
# Vi vay ban dong goi chay JavaFX o che do CLASSPATH, voi diem vao la Launcher
# (xem javadoc cua Launcher.java). Ban chay tu ma nguon van dung module path chuan.
$m2 = Join-Path $env:USERPROFILE ".m2\repository\org\openjfx"
$fx = "25.0.4"
foreach ($mod in @("javafx-base", "javafx-graphics", "javafx-controls")) {
    foreach ($suffix in @("", "-win")) {
        $jar = "$m2\$mod\$fx\$mod-$fx$suffix.jar"
        if (-not (Test-Path $jar)) { throw "Thieu $jar - chay `mvn -pl app-desktop compile` truoc." }
        Copy-Item $jar "$stage\javafx\"
    }
}

# --- jpackage ---
$out = Join-Path $dist $Type
if (Test-Path $out) { Remove-Item $out -Recurse -Force }
New-Item -ItemType Directory -Force -Path $out | Out-Null

Write-Host "==> jpackage --type $Type ..." -ForegroundColor Cyan
$jpArgs = @(
    "--type", $Type,
    "--name", $appName,
    "--app-version", "0.1.0",
    "--vendor", "AnhTuan2111",
    "--description", "Tu dien va dich offline Anh - Viet",
    "--input", $stage,
    "--dest", $out,
    "--main-jar", "app-desktop.jar",
    "--main-class", "com.anhtuan.dict.desktop.Launcher",
    "--java-options", "--enable-native-access=ALL-UNNAMED",
    "--java-options", "-Xmx512m",
    "--add-modules", "java.base,java.desktop,java.logging,java.management,jdk.unsupported"
)
if ($Type -ne "app-image") { $jpArgs += @("--win-dir-chooser", "--win-menu", "--win-shortcut") }

& jpackage @jpArgs
if ($LASTEXITCODE -ne 0) {
    if ($Type -ne "app-image") {
        throw "jpackage that bai. Kieu $Type can WiX Toolset v3.11; chua cai thi dung -Type app-image."
    }
    throw "jpackage that bai."
}

$size = (Get-ChildItem $out -Recurse -File | Measure-Object Length -Sum).Sum / 1MB
Write-Host ("==> Xong: {0}  ({1:N1} MB)" -f $out, $size) -ForegroundColor Green
$exe = Get-ChildItem $out -Recurse -Filter "$appName.exe" | Select-Object -First 1
if ($exe) { Write-Host "    Chay bang: $($exe.FullName)" -ForegroundColor Green }

if ($Zip -and $Type -eq "app-image") {
    $zipPath = Join-Path $dist "$appName-0.1.0-windows.zip"
    if (Test-Path $zipPath) { Remove-Item $zipPath -Force }
    Compress-Archive -Path (Join-Path $out $appName) -DestinationPath $zipPath
    $zipMb = (Get-Item $zipPath).Length / 1MB
    Write-Host ("==> Da nen: {0}  ({1:N1} MB)" -f $zipPath, $zipMb) -ForegroundColor Green
}
