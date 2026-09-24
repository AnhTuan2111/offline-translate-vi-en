<#
    Hoc bang xac suat dich tu (lex.bin) tu kho cau song ngu.

        .\scripts\hoc-bang-xac-suat.ps1                  tai kho neu chua co roi hoc
        .\scripts\hoc-bang-xac-suat.ps1 -MaxSentences 400000     ban nhanh de thu

    CHAY MOT LAN, LUC BUILD. Kho song ngu (102 MB) KHONG di kem ung dung: san pham duy nhat
    la file lex.bin ~2 MB nam trong data\build. Nguoi dung cuoi khong bao gio phai tai kho.

    Kho: OpenSubtitles en-vi cua du an OPUS (opus.nlpl.eu) - 3.505.276 cap cau phu de phim.
    Da thu ca kho QED nhung bo: phia tieng Viet cua no la ban dich may, chat luong kem.

    Bang nay chi la mot BANG TRA CUU thong ke (thuat toan IBM Model 1, 1993). Khong co mo
    hinh no-ron nao, luc chay chi co doc file va binary search.
#>
param(
    [int]$MaxSentences = 1200000,
    [string]$CorpusDir = "corpus",
    [switch]$KeepCorpus
)
$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent
Set-Location $root

$dict    = Join-Path $root "anhviet109K.txt"
$outDir  = Join-Path $root "data\build"
$corpus  = Join-Path $root $CorpusDir
$zipPath = Join-Path $corpus "opensubtitles-en-vi.zip"
$enFile  = Join-Path $corpus "OpenSubtitles.en-vi.en"
$viFile  = Join-Path $corpus "OpenSubtitles.en-vi.vi"
$url     = "https://object.pouta.csc.fi/OPUS-OpenSubtitles/v2018/moses/en-vi.txt.zip"

if (-not (Test-Path $dict)) { throw "Khong thay $dict" }
New-Item -ItemType Directory -Force -Path $corpus, $outDir | Out-Null

if (-not (Test-Path $enFile)) {
    if (-not (Test-Path $zipPath)) {
        Write-Host "==> Tai kho song ngu (102 MB, chi tai mot lan)..." -ForegroundColor Cyan
        Invoke-WebRequest -Uri $url -OutFile $zipPath
    }
    Write-Host "==> Giai nen..." -ForegroundColor Cyan
    Expand-Archive -Path $zipPath -DestinationPath $corpus -Force
}

Write-Host "==> Bien dich..." -ForegroundColor Cyan
& mvn -q -DskipTests install
if ($LASTEXITCODE -ne 0) { throw "mvn install that bai" }

Write-Host "==> Hoc bang xac suat ($('{0:N0}' -f $MaxSentences) cap cau)..." -ForegroundColor Cyan
& java -Xmx4g -Dstdout.encoding=UTF-8 `
    -cp "dict-core\target\classes;dict-importer\target\classes" `
    com.anhtuan.dict.importer.cli.ImporterMain lexicon $dict $enFile $viFile $outDir $MaxSentences
if ($LASTEXITCODE -ne 0) { throw "hoc bang that bai" }

if (-not $KeepCorpus) {
    Write-Host "==> Xoa kho song ngu (dung -KeepCorpus neu muon giu lai de hoc lai)" -ForegroundColor DarkGray
    Remove-Item $corpus -Recurse -Force
}

Write-Host "==> Xong. Kiem tra bang:" -ForegroundColor Green
Write-Host "    java -cp `"dict-core\target\classes;dict-importer\target\classes`" ``" -ForegroundColor Green
Write-Host "         com.anhtuan.dict.importer.cli.ImporterMain verify data\build" -ForegroundColor Green
