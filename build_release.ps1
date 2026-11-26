<#
.SYNOPSIS
  Dong goi ban phat hanh BotBanVatPham cho khach (Windows PowerShell 5.1+).

.DESCRIPTION
  Tao release\BotBanVatPham_<Version>\ va release\BotBanVatPham_<Version>.zip gom:
    - BanVatPham\ : ninja_ban_new.jar, NinjaBan.bat, mau tu release_template\BanVatPham\,
                    JRE Temurin 17.0.18+8 (x64, hotspot) trong BanVatPham\jre\
    - NinjaLite\GomItem\ : env.json, map\, DataDecode\, logs\ (rong) lay tu C:\NinjaLite\GomItem\
    - cai_dat.bat, HUONG_DAN.txt
  JRE duoc ghim (pin) theo URL + SHA-256, cache trong .cache\ va kiem tra hash moi lan chay.
  Truoc khi nen zip, quet bi mat (tai khoan/mat khau/khoa tu cau hinh that) - trung la huy goi.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File build_release.ps1
  powershell -ExecutionPolicy Bypass -File build_release.ps1 -Version 2026-11-25b -Force
#>
[CmdletBinding()]
param(
    [string]$Version = (Get-Date -Format 'yyyy-MM-dd'),
    [switch]$Force
)

Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'   # Invoke-WebRequest trong PS 5.1 rat cham neu hien progress

# ---------------- cau hinh co dinh ----------------
# Lay tu https://api.adoptium.net/v3/assets/version/jdk-17.0.18%2B8?architecture=x64&image_type=jre&os=windows&jvm_impl=hotspot&vendor=eclipse
# (binaries[0].package), doi chieu voi file .sha256.txt tren GitHub release.
$JreName   = 'OpenJDK17U-jre_x64_windows_hotspot_17.0.18_8.zip'
$JreUrl    = 'https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.18%2B8/OpenJDK17U-jre_x64_windows_hotspot_17.0.18_8.zip'
$JreSha256 = '95c9ebe3ee16baab7239531757513d9a03799ca06483ef2f3b530e81e93e7b5b'
$JreTopDir = 'jdk-17.0.18+8-jre'

$GameDataDir = 'C:\NinjaLite\GomItem'

# Duoi file dang text se duoc quet bi mat (file khong duoi: chi quet neu < 1MB)
$TextExtensions = @('.txt', '.json', '.bat', '.ps1', '.md', '.log', '.cfg', '.properties')
$MinSecretLength = 4

# ---------------- tien ich ----------------
function Write-Step([string]$msg) { Write-Host "==> $msg" -ForegroundColor Cyan }

function Get-RelPath([string]$base, [string]$full) {
    $b = $base.TrimEnd('\') + '\'
    if ($full.StartsWith($b, [StringComparison]::OrdinalIgnoreCase)) { return $full.Substring($b.Length) }
    return $full
}

function Get-Sha256([string]$path) {
    return (Get-FileHash -Algorithm SHA256 -LiteralPath $path).Hash.ToLowerInvariant()
}

function Read-TextUtf8([string]$path) {
    return [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)
}

# ---------------- kiem tra dau vao ----------------
if ($Version -notmatch '^[A-Za-z0-9._-]+$') {
    throw "Version '$Version' khong hop le (chi cho phep chu, so, '.', '_', '-')."
}

$RepoRoot = $PSScriptRoot
if ([string]::IsNullOrEmpty($RepoRoot)) { $RepoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path }
$RepoRoot = (Resolve-Path -LiteralPath $RepoRoot).Path

$SrcBan      = Join-Path $RepoRoot 'BanVatPham'
$TemplateDir = Join-Path $RepoRoot 'release_template'
$CacheDir    = Join-Path $RepoRoot '.cache'
$ReleaseDir  = Join-Path $RepoRoot 'release'
$PkgName     = "BotBanVatPham_$Version"
$OutDir      = Join-Path $ReleaseDir $PkgName
$ZipPath     = Join-Path $ReleaseDir "$PkgName.zip"

$requiredInputs = @(
    (Join-Path $SrcBan 'ninja_ban_new.jar'),
    (Join-Path $SrcBan 'NinjaBan.bat'),
    (Join-Path $TemplateDir 'BanVatPham'),
    (Join-Path $TemplateDir 'BanVatPham\cauhinh.txt'),
    (Join-Path $TemplateDir 'cai_dat.bat'),
    (Join-Path $TemplateDir 'HUONG_DAN.txt')
)
foreach ($p in $requiredInputs) {
    if (-not (Test-Path -LiteralPath $p)) { throw "Thieu dau vao: $p" }
}
if (-not (Test-Path -LiteralPath (Join-Path $GameDataDir 'env.json') -PathType Leaf)) {
    throw "Khong tim thay $GameDataDir\env.json - can du lieu game NinjaLite tren may build de dong goi."
}
foreach ($d in @('map', 'DataDecode')) {
    if (-not (Test-Path -LiteralPath (Join-Path $GameDataDir $d) -PathType Container)) {
        throw "Khong tim thay thu muc $GameDataDir\$d"
    }
}

if ((Test-Path -LiteralPath $OutDir) -or (Test-Path -LiteralPath $ZipPath)) {
    if (-not $Force) {
        throw "Da ton tai $OutDir hoac $ZipPath. Dung -Force de xoa va build lai, hoac doi -Version."
    }
    Write-Step "Xoa ban build cu (-Force)"
    if (Test-Path -LiteralPath $OutDir) { Remove-Item -LiteralPath $OutDir -Recurse -Force }
    if (Test-Path -LiteralPath $ZipPath) { Remove-Item -LiteralPath $ZipPath -Force }
}

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

$buildOk = $false
try {
    # ---------------- JRE (cache + SHA-256) ----------------
    Write-Step "JRE Temurin $JreTopDir"
    New-Item -ItemType Directory -Force -Path $CacheDir | Out-Null
    $jreZip = Join-Path $CacheDir $JreName
    if (-not (Test-Path -LiteralPath $jreZip)) {
        Write-Host "    Tai $JreUrl"
        [Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12
        $part = "$jreZip.part"
        if (Test-Path -LiteralPath $part) { Remove-Item -LiteralPath $part -Force }
        Invoke-WebRequest -Uri $JreUrl -OutFile $part -UseBasicParsing
        Move-Item -LiteralPath $part -Destination $jreZip
    } else {
        Write-Host "    Dung cache $jreZip"
    }
    $actual = Get-Sha256 $jreZip
    if ($actual -ne $JreSha256) {
        Remove-Item -LiteralPath $jreZip -Force
        throw "SHA-256 cua JRE KHONG KHOP (mong doi $JreSha256, thuc te $actual). Da xoa cache; kiem tra mang/nguon tai roi chay lai."
    }
    Write-Host "    SHA-256 OK ($JreSha256)"

    # ---------------- dung cay thu muc goi ----------------
    Write-Step "Tao goi $OutDir"
    $PkgBan = Join-Path $OutDir 'BanVatPham'
    New-Item -ItemType Directory -Force -Path $PkgBan | Out-Null

    # Giai nen JRE vao thu muc tam CUNG o dia voi goi (Move-Item thu muc khong duoc qua o dia khac)
    $extractDir = Join-Path $OutDir '_jre_extract'
    New-Item -ItemType Directory -Force -Path $extractDir | Out-Null
    [System.IO.Compression.ZipFile]::ExtractToDirectory($jreZip, $extractDir)
    $top = @(Get-ChildItem -LiteralPath $extractDir -Force)
    if ($top.Count -ne 1 -or -not $top[0].PSIsContainer -or $top[0].Name -ne $JreTopDir) {
        throw "Cau truc zip JRE khong nhu mong doi (can 1 thu muc goc '$JreTopDir', thuc te: $(($top | ForEach-Object { $_.Name }) -join ', '))"
    }
    Move-Item -LiteralPath $top[0].FullName -Destination (Join-Path $PkgBan 'jre')
    Remove-Item -LiteralPath $extractDir -Recurse -Force
    $javaExe = Join-Path $PkgBan 'jre\bin\java.exe'
    foreach ($exe in @($javaExe, (Join-Path $PkgBan 'jre\bin\keytool.exe'))) {
        if (-not (Test-Path -LiteralPath $exe -PathType Leaf)) { throw "JRE thieu $exe" }
    }

    # BanVatPham: CHI jar + NinjaBan.bat tu repo, con lai tu release_template\BanVatPham
    Copy-Item -LiteralPath (Join-Path $SrcBan 'ninja_ban_new.jar') -Destination $PkgBan
    Copy-Item -LiteralPath (Join-Path $SrcBan 'NinjaBan.bat') -Destination $PkgBan
    Get-ChildItem -LiteralPath (Join-Path $TemplateDir 'BanVatPham') -Force | ForEach-Object {
        Copy-Item -LiteralPath $_.FullName -Destination $PkgBan -Recurse -Force
    }
    # cauhinh.txt trong goi PHAI la ban mau (chong lo cau hinh that: SePay, mat khau admin, STK)
    $tplCfg = Join-Path $TemplateDir 'BanVatPham\cauhinh.txt'
    $pkgCfg = Join-Path $PkgBan 'cauhinh.txt'
    if (-not (Test-Path -LiteralPath $pkgCfg -PathType Leaf)) { throw "Goi thieu BanVatPham\cauhinh.txt" }
    if ((Get-Sha256 $pkgCfg) -ne (Get-Sha256 $tplCfg)) {
        throw "BanVatPham\cauhinh.txt trong goi KHAC ban mau release_template\BanVatPham\cauhinh.txt - huy goi."
    }
    $tplCfgText = Read-TextUtf8 $tplCfg
    foreach ($t in @('SEPAY_SECRET', 'ADMIN_PASS', 'ADMIN_CERT_PASS', 'BANK_STK')) {
        $rx = '\[' + [regex]::Escape($t) + '\](.*?)\[/' + [regex]::Escape($t) + '\]'
        foreach ($m in [regex]::Matches($tplCfgText, $rx, [System.Text.RegularExpressions.RegexOptions]::Singleline)) {
            if ($m.Groups[1].Value.Trim().Length -gt 0) { throw "Ban mau cauhinh.txt co gia tri $t khong trong - huy goi (khong in gia tri)." }
        }
    }

    # NinjaLite\GomItem: env.json, map\, DataDecode\, logs\ rong (khong rms_data, bandebug, logs cu)
    $PkgGame = Join-Path $OutDir 'NinjaLite\GomItem'
    New-Item -ItemType Directory -Force -Path $PkgGame | Out-Null
    Copy-Item -LiteralPath (Join-Path $GameDataDir 'env.json') -Destination $PkgGame
    Copy-Item -LiteralPath (Join-Path $GameDataDir 'map') -Destination $PkgGame -Recurse
    Copy-Item -LiteralPath (Join-Path $GameDataDir 'DataDecode') -Destination $PkgGame -Recurse
    New-Item -ItemType Directory -Force -Path (Join-Path $PkgGame 'logs') | Out-Null

    # Goc goi
    Copy-Item -LiteralPath (Join-Path $TemplateDir 'cai_dat.bat') -Destination $OutDir
    Copy-Item -LiteralPath (Join-Path $TemplateDir 'HUONG_DAN.txt') -Destination $OutDir

    # ---------------- quet bi mat ----------------
    Write-Step "Quet bi mat trong goi"
    # Danh sach gia tri nhay cam: { Value; Label } - KHONG bao gio in Value ra man hinh
    $secrets = New-Object System.Collections.Generic.List[object]
    function Add-Secret([string]$value, [string]$label) {
        if ($null -eq $value) { return }
        $v = $value.Trim().TrimStart([char]0xFEFF)
        if ($v.Length -lt $MinSecretLength) { return }
        foreach ($s in $secrets) { if ($s.Value -ceq $v) { return } }
        $secrets.Add([pscustomobject]@{ Value = $v; Label = $label })
    }
    function Add-TagSecrets([string]$content, [string]$tag, [string]$source) {
        $rx = '\[' + [regex]::Escape($tag) + '\](.*?)\[/' + [regex]::Escape($tag) + '\]'
        foreach ($m in [regex]::Matches($content, $rx, [System.Text.RegularExpressions.RegexOptions]::Singleline)) {
            Add-Secret $m.Groups[1].Value "$source $tag"
        }
    }

    # banvatpham.txt that co the chua duoc tach (cai dat cu) -> van lay tag chung tu day.
    $liveCfg = Join-Path $SrcBan 'banvatpham.txt'
    if (Test-Path -LiteralPath $liveCfg) {
        $c = Read-TextUtf8 $liveCfg
        foreach ($t in @('SEPAY_SECRET', 'ADMIN_PASS', 'ADMIN_CERT_PASS', 'BANK_STK', 'TK', 'MK')) { Add-TagSecrets $c $t 'banvatpham.txt' }
    } else { Write-Warning "Khong co $liveCfg - bo qua nguon bi mat nay" }

    $liveCauHinh = Join-Path $SrcBan 'cauhinh.txt'
    if (Test-Path -LiteralPath $liveCauHinh) {
        $c = Read-TextUtf8 $liveCauHinh
        foreach ($t in @('SEPAY_SECRET', 'ADMIN_PASS', 'ADMIN_CERT_PASS', 'BANK_STK')) { Add-TagSecrets $c $t 'cauhinh.txt' }
    } else { Write-Host "    (chua co $liveCauHinh - bo qua nguon nay)" }

    $liveMuado = Join-Path $SrcBan 'muado.txt'
    if (Test-Path -LiteralPath $liveMuado) {
        $c = Read-TextUtf8 $liveMuado
        foreach ($t in @('TK', 'MK')) { Add-TagSecrets $c $t 'muado.txt' }
    } else { Write-Warning "Khong co $liveMuado - bo qua nguon bi mat nay" }

    $liveNik = Join-Path $SrcBan 'nik_ban.txt'
    if (Test-Path -LiteralPath $liveNik) {
        # Dinh dang: TK:MK:VatPham:GiaVND:TrangThai:NguoiMua
        foreach ($line in [System.IO.File]::ReadAllLines($liveNik, [System.Text.Encoding]::UTF8)) {
            $f = $line.TrimStart([char]0xFEFF).Split(':')
            if ($f.Count -ge 1) { Add-Secret $f[0] 'nik_ban.txt TK' }
            if ($f.Count -ge 2) { Add-Secret $f[1] 'nik_ban.txt MK' }
        }
    } else { Write-Warning "Khong co $liveNik - bo qua nguon bi mat nay" }

    if ($secrets.Count -eq 0) { Write-Warning "Khong thu thap duoc gia tri nhay cam nao - quet noi dung khong co tac dung." }
    Write-Host "    So gia tri nhay cam dem duoc: $($secrets.Count)"

    $hits = New-Object System.Collections.Generic.List[string]
    $jreRoot = (Join-Path $PkgBan 'jre').TrimEnd('\') + '\'
    $allFiles = @(Get-ChildItem -LiteralPath $OutDir -Recurse -Force -File)

    # File/thu muc cam tuyet doi
    foreach ($f in $allFiles) {
        $rel = Get-RelPath $OutDir $f.FullName
        if ($f.Name -ieq 'wallets.txt' -or $f.Name -ieq 'atm_seen.log' -or $f.Name -like 'admin_keystore.*' -or $f.Name -like 'banvatpham.txt.bak*') {
            $hits.Add("file cam trong goi: $rel")
        }
        if ($rel -match '(?i)(^|\\)(rms_data|bandebug)(\\|$)') { $hits.Add("thu muc cam trong goi: $rel") }
    }
    foreach ($d in @(Get-ChildItem -LiteralPath $OutDir -Recurse -Force -Directory)) {
        if ($d.Name -ieq 'rms_data' -or $d.Name -ieq 'bandebug') { $hits.Add("thu muc cam trong goi: $(Get-RelPath $OutDir $d.FullName)") }
    }

    # Quet noi dung file text (bo qua JRE va file nhi phan)
    $scanned = 0
    foreach ($f in $allFiles) {
        if ($f.FullName.StartsWith($jreRoot, [StringComparison]::OrdinalIgnoreCase)) { continue }
        $ext = $f.Extension.ToLowerInvariant()
        $isText = ($TextExtensions -contains $ext) -or ($ext -eq '' -and $f.Length -lt 1MB)
        if (-not $isText) { continue }
        $scanned++
        $content = Read-TextUtf8 $f.FullName
        $rel = Get-RelPath $OutDir $f.FullName
        foreach ($s in $secrets) {
            if ($content.IndexOf($s.Value, [StringComparison]::OrdinalIgnoreCase) -ge 0) {
                $hits.Add("hit in $rel (tag $($s.Label))")
            }
        }
    }
    Write-Host "    Da quet $scanned file text"

    if ($hits.Count -gt 0) {
        foreach ($h in $hits) { Write-Host "    !! $h" -ForegroundColor Red }
        throw "QUET BI MAT THAT BAI ($($hits.Count) loi) - goi da bi huy."
    }
    Write-Host "    Quet bi mat: SACH" -ForegroundColor Green

    # ---------------- nen zip ----------------
    # Tu ghi ZipArchive thay Compress-Archive / ZipFile.CreateFromDirectory: trong PS 5.1 ca hai deu ghi
    # ten entry bang '\' (sai chuan zip), va Compress-Archive bo qua thu muc rong (NinjaLite\GomItem\logs\).
    # O day: separator '/', thu muc goc BotBanVatPham_<Version>/, va them entry cho moi thu muc rong.
    Write-Step "Nen $ZipPath"
    $zipStream = [System.IO.File]::Open($ZipPath, [System.IO.FileMode]::CreateNew)
    try {
        $archive = New-Object System.IO.Compression.ZipArchive($zipStream, [System.IO.Compression.ZipArchiveMode]::Create)
        try {
            foreach ($f in @(Get-ChildItem -LiteralPath $OutDir -Recurse -Force -File)) {
                $entryName = $PkgName + '/' + (Get-RelPath $OutDir $f.FullName).Replace('\', '/')
                [void][System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, $f.FullName, $entryName, [System.IO.Compression.CompressionLevel]::Optimal)
            }
            foreach ($d in @(Get-ChildItem -LiteralPath $OutDir -Recurse -Force -Directory)) {
                if (@(Get-ChildItem -LiteralPath $d.FullName -Force).Count -eq 0) {
                    [void]$archive.CreateEntry($PkgName + '/' + (Get-RelPath $OutDir $d.FullName).Replace('\', '/') + '/')
                }
            }
        } finally { $archive.Dispose() }
    } finally { $zipStream.Dispose() }

    $buildOk = $true
}
finally {
    if (-not $buildOk) {
        Write-Host "Build THAT BAI - xoa ket qua dang do" -ForegroundColor Red
        if (Test-Path -LiteralPath $OutDir) { Remove-Item -LiteralPath $OutDir -Recurse -Force -ErrorAction SilentlyContinue }
        if (Test-Path -LiteralPath $ZipPath) { Remove-Item -LiteralPath $ZipPath -Force -ErrorAction SilentlyContinue }
    }
}

# ---------------- tong ket ----------------
$zipInfo = Get-Item -LiteralPath $ZipPath
Write-Host ""
Write-Host "===== BUILD XONG =====" -ForegroundColor Green
Write-Host "Goi : $OutDir"
Write-Host ("Zip : {0} ({1:N1} MB)" -f $zipInfo.FullName, ($zipInfo.Length / 1MB))
Write-Host "Java trong goi:"
$javaExe = Join-Path $OutDir 'BanVatPham\jre\bin\java.exe'
$prevEap = $ErrorActionPreference
$ErrorActionPreference = 'Continue'   # java -version in ra stderr
& $javaExe -version 2>&1 | ForEach-Object { Write-Host "    $_" }
$ErrorActionPreference = $prevEap
