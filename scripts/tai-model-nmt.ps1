<#
    Tai mo hinh dich may no-ron chay cuc bo (~98 MB).

        .\scripts\tai-model-nmt.ps1           tai va cai vao data\build\nmt-en-vi
        .\scripts\tai-model-nmt.ps1 -Remove   go mo hinh di

    NOI RO: day la mot MO HINH AI chay tren may ban. Khac han phan con lai cua ung dung -
    tu dien, bang xac suat, luat ngu phap deu khong co mo hinh nao. Sau khi tai ve thi no
    chay hoan toan offline, nhung no van la AI.

    Mo hinh: Helsinki-NLP/opus-mt-en-vi (giay phep Apache-2.0), ban da xuat sang ONNX va
    luong tu hoa 8 bit boi Xenova. 6 lop encoder + 6 lop decoder, 512 chieu.
    Nguon: https://huggingface.co/Xenova/opus-mt-en-vi

    App van chay binh thuong khi KHONG co mo hinh - luc do o "dung mo hinh AI" khong hien ra.
#>
param(
    [string]$DataDir = "data\build",
    [switch]$Remove
)
$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent
Set-Location $root

$modelDir = Join-Path $root (Join-Path $DataDir "nmt-en-vi")

if ($Remove) {
    if (Test-Path $modelDir) { Remove-Item $modelDir -Recurse -Force; Write-Host "Da go mo hinh." }
    else { Write-Host "Khong co gi de go." }
    return
}

$base = "https://huggingface.co/Xenova/opus-mt-en-vi/resolve/main"
New-Item -ItemType Directory -Force -Path $modelDir, (Join-Path $modelDir "onnx") | Out-Null

$files = @(
    @{ Url = "$base/config.json";                          Path = "config.json" },
    @{ Url = "$base/tokenizer.json";                       Path = "tokenizer.json" },
    @{ Url = "$base/onnx/encoder_model_quantized.onnx";    Path = "onnx\encoder_model_quantized.onnx" },
    @{ Url = "$base/onnx/decoder_model_quantized.onnx";    Path = "onnx\decoder_model_quantized.onnx" }
)

foreach ($f in $files) {
    $target = Join-Path $modelDir $f.Path
    if (Test-Path $target) { Write-Host "  da co $($f.Path)" -ForegroundColor DarkGray; continue }
    Write-Host "==> Tai $($f.Path) ..." -ForegroundColor Cyan
    Invoke-WebRequest -Uri $f.Url -OutFile $target
}

# tokenizer.json -> vocab.tsv
#
# Thu vien tokenizers cua HuggingFace (qua DJL) doc duoc file nay, nhung da thu va no lam
# SUP CA JVM voi model nay. MarianTokenizer.java tu lam lay viec tach tu, chi can tu vung
# dang bang phang: moi dong "manh<TAB>diem", so thu tu dong chinh la id.
$vocabTsv = Join-Path $modelDir "vocab.tsv"
if (-not (Test-Path $vocabTsv)) {
    Write-Host "==> Chuyen tokenizer.json -> vocab.tsv ..." -ForegroundColor Cyan
    $json = Get-Content (Join-Path $modelDir "tokenizer.json") -Raw -Encoding UTF8 | ConvertFrom-Json
    $sb = [System.Text.StringBuilder]::new()
    foreach ($entry in $json.model.vocab) {
        [void]$sb.Append($entry[0]).Append("`t").Append($entry[1]).Append("`n")
    }
    [System.IO.File]::WriteAllText($vocabTsv, $sb.ToString(), [System.Text.UTF8Encoding]::new($false))
    Write-Host "    $($json.model.vocab.Count) muc tu vung"
}

$size = (Get-ChildItem $modelDir -Recurse -File | Measure-Object Length -Sum).Sum / 1MB
Write-Host ("==> Xong: {0} ({1:N1} MB)" -f $modelDir, $size) -ForegroundColor Green
Write-Host "    Mo app roi tick o 'Dung mo hinh AI tren may' o goc tren ben phai." -ForegroundColor Green
Write-Host "    Thu nhanh: .\scripts\run.ps1 -Nmt -Mode sentence -Query 'She went to the market yesterday'" -ForegroundColor Green
