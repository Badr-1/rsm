<#
.SYNOPSIS
  Installs (or uninstalls) rsm on Windows.

.DESCRIPTION
  Downloads the rsm JAR from GitHub releases into %LOCALAPPDATA%\Programs\rsm,
  creates an rsm.cmd wrapper, adds that folder to your user PATH, and checks
  for Java 17+ and the LaTeX packages needed by 'rsm compile'.

.EXAMPLE
  .\install.ps1                  # latest release
  .\install.ps1 -Version 2.0.2
  .\install.ps1 -Jar .\build\libs\rsm-2.0.2.jar   # install a local build
  .\install.ps1 -Yes             # install MiKTeX without asking if LaTeX is missing
  .\install.ps1 -Uninstall

  One-liner (latest release):
  irm https://raw.githubusercontent.com/Badr-1/rsm/main/install.ps1 | iex
#>
[CmdletBinding()]
param(
    [string]$Version = $env:RSM_VERSION,
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
if ([string]::IsNullOrWhiteSpace($Version) -and $Jar) {
    if ((Split-Path -Leaf $Jar) -match "^$ToolName-(\d+\.\d+\.\d+)\.jar$") {
        $Version = $Matches[1]
    } else {
        Fail "can't tell the version from '$(Split-Path -Leaf $Jar)'. Pass it too, e.g. -Version 2.0.2 -Jar FILE"
    }
} elseif ([string]::IsNullOrWhiteSpace($Version)) {
    Write-Host 'No version given, looking up the latest release...'
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
function Test-Jar([string]$Path) {
    if (-not (Test-Path $Path) -or (Get-Item $Path).Length -eq 0) { return $false }
    try {
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        $zip = [IO.Compression.ZipFile]::OpenRead($Path)
        try {
            return [bool]($zip.Entries | Where-Object { $_.FullName -eq 'META-INF/MANIFEST.MF' })
        } finally {
            $zip.Dispose()
        }
    } catch {
        return $false
    }
}

New-Item -ItemType Directory -Force -Path $InstallDir | Out-Null
$JarPath = Join-Path $InstallDir $JarName
if ($Jar) {
    if (-not (Test-Jar $Jar)) { Fail "$Jar is not a valid JAR." }
    # Always copy: a local build can change without its version changing
    if ($Jar -ine $JarPath) {
        Write-Host "Copying $Jar to $JarPath..."
        Copy-Item -Force $Jar $JarPath
    }
} elseif (Test-Jar $JarPath) {
    Write-Host "$JarName already exists in $InstallDir. Skipping download."
} else {
    if (Test-Path $JarPath) { Write-Host "Existing $JarName is corrupt or incomplete. Re-downloading..." }
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
        Fail 'downloaded file is not a valid JAR. Aborting.'
    }
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
