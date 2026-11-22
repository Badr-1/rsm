<#
.SYNOPSIS
  Installs (or uninstalls) rsm on Windows.

.DESCRIPTION
  Downloads the rsm JAR from GitHub releases into %LOCALAPPDATA%\Programs\rsm,
  creates an rsm.cmd wrapper, adds that folder to your user PATH, and checks
  for Java 17+ and the LaTeX packages needed by 'rsm compile'.

.EXAMPLE
  .\install.ps1                  # choose from the available releases
  .\install.ps1 -Latest          # latest release, no questions
  .\install.ps1 -Version 2.0.2
  .\install.ps1 -Jar .\build\libs\rsm-2.0.2.jar   # install a local build
  .\install.ps1 -Yes             # install MiKTeX without asking if LaTeX is missing
  .\install.ps1 -Uninstall

  One-liner (shows the release menu):
  irm https://raw.githubusercontent.com/Badr-1/rsm/main/install.ps1 | iex

  One-liner, latest release without asking (iex can't take parameters, so use
  the RSM_VERSION environment variable; it also accepts a version like 2.0.2):
  $env:RSM_VERSION = 'latest'; irm https://raw.githubusercontent.com/Badr-1/rsm/main/install.ps1 | iex
#>
[CmdletBinding()]
param(
    [string]$Version = $env:RSM_VERSION,
    [switch]$Latest,
    [string]$Jar,
    [switch]$Yes,
    [switch]$SkipDeps,
    [switch]$Uninstall
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue' # Invoke-WebRequest is very slow with the progress bar in PS 5.1

$ToolName = 'rsm'
$Repo = 'Badr-1/rsm'
$InstallDir = Join-Path $env:LOCALAPPDATA "Programs\$ToolName"
$RequiredJava = 17

# PowerShell 5.1 defaults to TLS 1.0, which GitHub rejects
[Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12

# Use throw, not exit: when run via `irm ... | iex`, exit would close the
# user's PowerShell window.
function Fail([string]$Message) {
    throw "Error: $Message"
}

function Confirm-Action([string]$Question) {
    if ($Yes) { return $true }
    try {
        $reply = Read-Host "$Question [y/N]"
    } catch {
        return $false # non-interactive host
    }
    return $reply -match '^(y|yes)$'
}

function Get-UserPath {
    $p = [Environment]::GetEnvironmentVariable('Path', 'User')
    if ($null -eq $p) { return '' }
    return $p
}

function Test-PathEntry([string]$PathValue, [string]$Dir) {
    $target = $Dir.TrimEnd('\')
    foreach ($entry in ($PathValue -split ';')) {
        if ($entry.TrimEnd('\') -ieq $target) { return $true }
    }
    return $false
}

# ---------------------------------------------------------------- uninstall
if ($Uninstall) {
    if (Test-Path $InstallDir) {
        Remove-Item -Recurse -Force $InstallDir
        Write-Host "Removed $InstallDir"
    }
    $userPath = Get-UserPath
    if (Test-PathEntry $userPath $InstallDir) {
        $kept = ($userPath -split ';') | Where-Object { $_ -and ($_.TrimEnd('\') -ine $InstallDir.TrimEnd('\')) }
        [Environment]::SetEnvironmentVariable('Path', ($kept -join ';'), 'User')
        Write-Host "Removed $InstallDir from your user PATH"
    }
    Write-Host "$ToolName uninstalled. Java, LaTeX and your resume repositories were not touched."
    return
}

# ---------------------------------------------------------------- version
if ($Jar) {
    if (-not (Test-Path -PathType Leaf $Jar)) { Fail "JAR file not found: $Jar" }
    $Jar = (Resolve-Path $Jar).Path
}
if ($Version -ieq 'latest') {
    $Version = ''
    $Latest = $true
}
if ($Latest -and -not [string]::IsNullOrWhiteSpace($Version)) { Fail 'use either -Version or -Latest, not both' }
if ($Latest -and $Jar) { Fail "-Latest can't be combined with -Jar" }

# Installable releases, newest first. Drafts, versions not shaped X.Y.Z and
# releases without an rsm-<version>.jar asset are skipped.
function Get-Releases {
    $all = Invoke-RestMethod -UseBasicParsing -ErrorAction Stop "https://api.github.com/repos/$Repo/releases?per_page=30"
    foreach ($r in $all) {
        $v = $r.tag_name -replace '^v', ''
        if ($r.draft -or $v -notmatch '^\d+\.\d+\.\d+$') { continue }
        if (-not ($r.assets | Where-Object { $_.name -eq "$ToolName-$v.jar" })) { continue }
        $date = ''
        if ($r.published_at) { $date = ([datetime]$r.published_at).ToString('yyyy-MM-dd') }
        [pscustomobject]@{ Version = $v; PreRelease = [bool]$r.prerelease; Date = $date }
    }
}

# Show the releases and return the chosen version. Without an interactive
# host, returns the latest stable release.
function Select-Version {
    Write-Host 'Fetching available releases...'
    try {
        $releases = @(Get-Releases)
    } catch {
        $releases = @()
    }
    if ($releases.Count -eq 0) {
        Fail "could not fetch releases from GitHub. Pass a version, e.g. .\install.ps1 -Version 2.0.2"
    }
    $default = 1
    $stable = @($releases | Where-Object { -not $_.PreRelease })
    if ($stable.Count -gt 0) { $default = [array]::IndexOf($releases, $stable[0]) + 1 }

    Write-Host ''
    Write-Host "Available $ToolName releases:"
    for ($i = 0; $i -lt $releases.Count; $i++) {
        $r = $releases[$i]
        $label = "v$($r.Version)"
        if ($r.Date) { $label += "  ($($r.Date))" }
        if ($r.PreRelease) { $label += '  [pre-release]' } elseif ($i + 1 -eq $default) { $label += '  [latest]' }
        Write-Host ('  {0,2}) {1}' -f ($i + 1), $label)
    }
    Write-Host ''

    while ($true) {
        try {
            $reply = Read-Host "Choose a release [$default]"
        } catch {
            $v = $releases[$default - 1].Version
            Write-Host "No interactive host to choose a version; installing v$v (use -Version or -Latest to be explicit)."
            return $v
        }
        if ([string]::IsNullOrWhiteSpace($reply)) { $reply = "$default" }
        $reply = $reply.Trim()
        $n = 0
        if ([int]::TryParse($reply, [ref]$n) -and $n -ge 1 -and $n -le $releases.Count) {
            return $releases[$n - 1].Version
        }
        $match = $releases | Where-Object { $_.Version -eq ($reply -replace '^v', '') } | Select-Object -First 1
        if ($match) { return $match.Version }
        Write-Host "Invalid choice '$reply'. Enter a number from 1 to $($releases.Count)." -ForegroundColor Yellow
    }
}

if ([string]::IsNullOrWhiteSpace($Version) -and $Jar) {
    if ((Split-Path -Leaf $Jar) -match "^$ToolName-(\d+\.\d+\.\d+)\.jar$") {
        $Version = $Matches[1]
    } else {
        Fail "can't tell the version from '$(Split-Path -Leaf $Jar)'. Pass it too, e.g. -Version 2.0.2 -Jar FILE"
    }
} elseif ([string]::IsNullOrWhiteSpace($Version) -and -not $Latest) {
    $Version = Select-Version
} elseif ([string]::IsNullOrWhiteSpace($Version)) {
    Write-Host 'Looking up the latest release...'
    try {
        $release = Invoke-RestMethod -UseBasicParsing -ErrorAction Stop "https://api.github.com/repos/$Repo/releases/latest"
        $Version = $release.tag_name
    } catch {
        Fail "could not determine the latest release. Pass one, e.g. .\install.ps1 -Version 2.0.2"
    }
}
$Version = $Version.Trim() -replace '^v', ''
if ($Version -notmatch '^\d+\.\d+\.\d+$') {
    Fail "invalid version '$Version'. Expected format X.Y.Z (e.g. 2.0.2)."
}
$JarName = "$ToolName-$Version.jar"
Write-Host "Installing $ToolName v$Version..."

# ---------------------------------------------------------------- Java 17+
$javaHint = "  winget install EclipseAdoptium.Temurin.$RequiredJava.JRE`n  or download from https://adoptium.net/"
if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    Fail "Java $RequiredJava or newer is required but 'java' was not found. Install it with:`n$javaHint"
}
# 'java -version' writes to stderr; temporarily relax error handling to capture it
$prevEap = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
$javaOut = (& java -version 2>&1 | ForEach-Object { "$_" }) -join "`n"
$ErrorActionPreference = $prevEap
$javaMajor = $null
if ($javaOut -match 'version "([^"]+)"') {
    $javaVersion = $Matches[1]
    $parts = $javaVersion -split '[.\-+_]'
    $javaMajor = [int]$parts[0]
    if ($javaMajor -eq 1 -and $parts.Count -gt 1) { $javaMajor = [int]$parts[1] }
}
if ($null -eq $javaMajor) {
    Write-Host "Warning: could not determine Java version; make sure Java $RequiredJava+ is installed." -ForegroundColor Yellow
} elseif ($javaMajor -lt $RequiredJava) {
    Fail "Java $RequiredJava or newer is required, found Java $javaVersion. Install a newer one with:`n$javaHint"
}

# ---------------------------------------------------------------- LaTeX
# Files used by the resume template (see src/main/kotlin/LaTeXGenerator.kt)
$TexFiles = @(
    'latexsym.sty', 'fullpage.sty', 'titlesec.sty', 'marvosym.sty', 'color.sty', 'verbatim.sty',
    'enumitem.sty', 'hyperref.sty', 'fancyhdr.sty', 'babel.sty', 'english.ldf', 'tabularx.sty',
    'glyphtounicode.tex', 'mathptmx.sty'
)
# MiKTeX package names for the files above
$MiktexPackages = @(
    'latexsym', 'preprint', 'titlesec', 'marvosym', 'graphics', 'tools', 'enumitem',
    'hyperref', 'fancyhdr', 'babel', 'babel-english', 'pdftex', 'psnfss'
)

function Add-MiktexToSessionPath {
    foreach ($dir in @(
            (Join-Path $env:LOCALAPPDATA 'Programs\MiKTeX\miktex\bin\x64'),
            (Join-Path $env:ProgramFiles 'MiKTeX\miktex\bin\x64'))) {
        if ((Test-Path $dir) -and -not (Test-PathEntry $env:Path $dir)) { $env:Path = "$env:Path;$dir" }
    }
}

# Native commands below may write to stderr, which throws under
# ErrorActionPreference=Stop in PowerShell 5.1, so relax it in these functions.
function Get-MissingTex {
    $ErrorActionPreference = 'Continue'
    if (-not (Get-Command pdflatex -ErrorAction SilentlyContinue) -or
        -not (Get-Command kpsewhich -ErrorAction SilentlyContinue)) {
        return @('pdflatex')
    }
    $missing = @()
    foreach ($f in $TexFiles) {
        $found = & kpsewhich $f 2>$null
        if ([string]::IsNullOrWhiteSpace($found)) { $missing += $f }
    }
    return $missing
}

function Install-Tex {
    $ErrorActionPreference = 'Continue'
    if (-not (Get-Command pdflatex -ErrorAction SilentlyContinue)) {
        if (-not (Get-Command winget -ErrorAction SilentlyContinue)) { return $false }
        & winget install --id MiKTeX.MiKTeX -e --accept-source-agreements --accept-package-agreements
        if ($LASTEXITCODE -ne 0) { return $false }
        Add-MiktexToSessionPath
    }
    if (Get-Command miktex -ErrorAction SilentlyContinue) {
        & miktex packages update-package-database
        & miktex packages install $MiktexPackages
        # Also let MiKTeX fetch anything else on first compile
        & initexmf --set-config-value '[MPM]AutoInstall=1' 2>$null
        return $true
    }
    if (Get-Command mpm -ErrorAction SilentlyContinue) {
        foreach ($pkg in $MiktexPackages) { & mpm "--install=$pkg" }
        return $true
    }
    if (Get-Command tlmgr -ErrorAction SilentlyContinue) {
        & tlmgr install $MiktexPackages
        return ($LASTEXITCODE -eq 0)
    }
    return $false
}

Add-MiktexToSessionPath
$missingTex = @(Get-MissingTex)
if ($missingTex.Count -gt 0) {
    Write-Host "Missing LaTeX components needed by 'rsm compile': $($missingTex -join ' ')"
    $manual = "Install MiKTeX (https://miktex.org/download) or TeX Live with: $($MiktexPackages -join ' ')"
    if ($SkipDeps) {
        Write-Host "Skipping LaTeX install (-SkipDeps). $manual"
    } elseif (-not (Confirm-Action 'Install them now (MiKTeX via winget)?')) {
        Write-Host "Skipping LaTeX install. Re-run with -Yes to install automatically. $manual"
    } elseif (-not (Install-Tex)) {
        Fail "could not install LaTeX automatically. $manual"
    } else {
        $missingTex = @(Get-MissingTex)
        if ($missingTex.Count -gt 0) {
            Write-Host "Warning: still missing after install: $($missingTex -join ' ')" -ForegroundColor Yellow
            Write-Host "'rsm compile' may fail until these are installed (or open a new terminal and retry)."
        }
    }
}

# ---------------------------------------------------------------- download
# Downloads are verified against the SHA-256 digest GitHub publishes for each
# release asset. Without a digest (API unreachable, old assets, or -Jar), only
# the ZIP signature is checked, which catches HTML error pages and empty files.

# Non-empty and starts with the ZIP signature "PK\x03\x04"?
function Test-JarSignature([string]$Path) {
    if (-not (Test-Path -PathType Leaf $Path)) { return $false }
    $fs = [IO.File]::OpenRead($Path)
    try {
        $buf = New-Object byte[] 4
        if ($fs.Read($buf, 0, 4) -ne 4) { return $false }
        return ($buf[0] -eq 0x50 -and $buf[1] -eq 0x4B -and $buf[2] -eq 0x03 -and $buf[3] -eq 0x04)
    } finally {
        $fs.Dispose()
    }
}

function Get-JarDigest {
    try {
        $rel = Invoke-RestMethod -UseBasicParsing -ErrorAction Stop "https://api.github.com/repos/$Repo/releases/tags/v$Version"
    } catch {
        return ''
    }
    $asset = $rel.assets | Where-Object { $_.name -eq $JarName } | Select-Object -First 1
    if ($asset -and "$($asset.digest)" -match '^sha256:([0-9a-f]+)$') { return $Matches[1] }
    return ''
}

function Test-Jar([string]$Path) {
    if ($ExpectedSha256) {
        if (-not (Test-Path -PathType Leaf $Path)) { return $false }
        return ((Get-FileHash -Algorithm SHA256 $Path).Hash -ieq $ExpectedSha256)
    }
    return (Test-JarSignature $Path)
}

New-Item -ItemType Directory -Force -Path $InstallDir | Out-Null
$JarPath = Join-Path $InstallDir $JarName
$ExpectedSha256 = ''
if (-not $Jar) {
    $ExpectedSha256 = Get-JarDigest
    if (-not $ExpectedSha256) { Write-Host "Note: no checksum available for $JarName; only a basic file check will be done." }
}
if ($Jar) {
    if (-not (Test-JarSignature $Jar)) { Fail "$Jar is not a JAR file." }
    # Always copy: a local build can change without its version changing
    if ($Jar -ine $JarPath) {
        Write-Host "Copying $Jar to $JarPath..."
        Copy-Item -Force $Jar $JarPath
    }
} elseif (Test-Jar $JarPath) {
    Write-Host "$JarName already exists in $InstallDir. Skipping download."
} else {
    if (Test-Path $JarPath) { Write-Host "Existing $JarName doesn't match the release. Re-downloading..." }
    $url = "https://github.com/$Repo/releases/download/v$Version/$JarName"
    $tmp = "$JarPath.download"
    Write-Host "Downloading $url..."
    try {
        Invoke-WebRequest -UseBasicParsing -ErrorAction Stop -Uri $url -OutFile $tmp
    } catch {
        Remove-Item -Force $tmp -ErrorAction SilentlyContinue
        Fail "download failed. Check that release v$Version exists: https://github.com/$Repo/releases"
    }
    if (-not (Test-Jar $tmp)) {
        Remove-Item -Force $tmp -ErrorAction SilentlyContinue
        if ($ExpectedSha256) {
            Fail "checksum mismatch for $JarName (expected sha256 $ExpectedSha256). Aborting."
        }
        Fail 'downloaded file is not a JAR. Aborting.'
    }
    if ($ExpectedSha256) { Write-Host 'Checksum verified (sha256).' }
    Move-Item -Force $tmp $JarPath
}

# ---------------------------------------------------------------- wrapper
$wrapper = Join-Path $InstallDir "$ToolName.cmd"
Set-Content -Path $wrapper -Encoding ASCII -Value @(
    '@echo off',
    "java --enable-native-access=ALL-UNNAMED -jar `"%~dp0$JarName`" %*"
)

# ---------------------------------------------------------------- PATH
$pathUpdated = $false
$userPath = Get-UserPath
if (-not (Test-PathEntry $userPath $InstallDir)) {
    Write-Host "Adding $InstallDir to your user PATH..."
    $newPath = if ($userPath) { "$($userPath.TrimEnd(';'));$InstallDir" } else { $InstallDir }
    [Environment]::SetEnvironmentVariable('Path', $newPath, 'User')
    $pathUpdated = $true
}
if (-not (Test-PathEntry $env:Path $InstallDir)) { $env:Path = "$env:Path;$InstallDir" }

Write-Host "$ToolName v$Version installed successfully" -ForegroundColor Green
if ($pathUpdated) {
    Write-Host "Open a new terminal to use '$ToolName' everywhere."
}
Write-Host 'Note: shell completion is not available for PowerShell/cmd.'
