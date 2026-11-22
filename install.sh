#!/usr/bin/env bash
set -e

TOOL_NAME="rsm"
REPO="Badr-1/rsm"
INSTALL_DIR="$HOME/.local/bin"

# Detect the OS up front. uname is used instead of $OSTYPE, which is
# "linux-musl" on Alpine and is unset in some non-bash environments.
case "$(uname -s)" in
  Linux*) OS="linux" ;;
  Darwin*) OS="macos" ;;
  MINGW* | MSYS* | CYGWIN*)
    echo "Error: Windows shells (Git Bash/MSYS/Cygwin) are not supported by install.sh." >&2
    echo "Use the PowerShell installer instead: install.ps1" >&2
    exit 1
    ;;
  *)
    echo "Error: unsupported operating system '$(uname -s)'. Only Linux and macOS are supported." >&2
    echo "You can still run rsm manually: java -jar rsm-<version>.jar" >&2
    exit 1
    ;;
esac

usage() {
  cat <<EOF
Usage: install.sh [VERSION | --latest] [options]

Install $TOOL_NAME. Without VERSION or --latest, shows the available GitHub
releases to choose from.

Options:
  VERSION          Install this release (e.g. 2.0.2 or v2.0.2)
      --latest     Install the latest release without asking
      --jar FILE   Install from a local JAR instead of downloading
                   (e.g. build/libs/$TOOL_NAME-2.0.2.jar from a source build)
  -y, --yes        Install missing system packages (LaTeX) without asking
      --skip-deps  Don't install system packages; only warn if they're missing
  -h, --help       Show this help
EOF
}

ASSUME_YES=false
SKIP_DEPS=false
TOOL_VERSION=""
LOCAL_JAR=""
USE_LATEST=false
while [[ $# -gt 0 ]]; do
  case "$1" in
    --latest) USE_LATEST=true ;;
    --jar)
      if [[ $# -lt 2 ]]; then echo "Error: --jar needs a file path" >&2; exit 1; fi
      LOCAL_JAR="$2"
      shift
      ;;
    -y | --yes) ASSUME_YES=true ;;
    --skip-deps) SKIP_DEPS=true ;;
    -h | --help) usage; exit 0 ;;
    -*) echo "Error: unknown option '$1'" >&2; usage >&2; exit 1 ;;
    *)
      if [[ -n "$TOOL_VERSION" ]]; then
        echo "Error: more than one version given" >&2; exit 1
      fi
      TOOL_VERSION="$1"
      ;;
  esac
  shift
done

# Ask a yes/no question. Reads from /dev/tty so it also works with `curl ... | bash`.
# Returns non-zero when there's no terminal to ask on (and --yes wasn't given).
confirm() {
  [[ "$ASSUME_YES" == true ]] && return 0
  if ! { exec 3</dev/tty; } 2>/dev/null; then
    return 1
  fi
  local reply
  printf '%s [y/N] ' "$1" >/dev/tty
  read -r reply <&3 || reply=""
  exec 3<&-
  [[ "$reply" =~ ^[Yy]([Ee][Ss])?$ ]]
}

if [[ "$USE_LATEST" == true && -n "$TOOL_VERSION" ]]; then
  echo "Error: use either VERSION or --latest, not both" >&2; exit 1
fi
if [[ "$USE_LATEST" == true && -n "$LOCAL_JAR" ]]; then
  echo "Error: --latest can't be combined with --jar" >&2; exit 1
fi

# Print the installable releases, newest first, one per line:
#   <version><TAB><prerelease true|false><TAB><published date>
# Drafts and releases without an $TOOL_NAME-<version>.jar asset are skipped.
# Parsed with grep/awk so jq isn't required.
list_releases() {
  curl -fsSL "https://api.github.com/repos/$REPO/releases?per_page=30" |
    grep -oE '"(tag_name|draft|prerelease|published_at|name)": *("[^"]*"|true|false|null)' |
    awk -v tool="$TOOL_NAME" '
      function flush() {
        if (tag != "" && draft != "true" && has_jar && ver ~ /^[0-9]+\.[0-9]+\.[0-9]+$/)
          printf "%s\t%s\t%s\n", ver, (pre == "true" ? "true" : "false"), substr(date, 1, 10)
        tag = ""
      }
      {
        key = $0; sub(/^"/, "", key); sub(/".*/, "", key)
        val = $0; sub(/^[^:]*: */, "", val); gsub(/"/, "", val)
      }
      key == "tag_name" {
        flush(); tag = val; ver = val; sub(/^v/, "", ver)
        draft = ""; pre = ""; date = ""; has_jar = 0
        next
      }
      key == "draft" { draft = val }
      key == "prerelease" { pre = val }
      key == "published_at" && date == "" { date = val }
      key == "name" && val == tool "-" ver ".jar" { has_jar = 1 }
      END { flush() }
    '
}

# Show the releases and let the user pick one; sets TOOL_VERSION.
# Without a terminal (e.g. CI), falls back to the latest stable release.
choose_version() {
  local releases
  echo "Fetching available releases..."
  releases="$(list_releases)" || true
  if [[ -z "$releases" ]]; then
    echo "Error: could not fetch releases from GitHub. Pass a version, e.g. ./install.sh 2.0.2" >&2
    exit 1
  fi

  local versions=() labels=() latest_idx="" i=0 v pre date label
  while IFS=$'\t' read -r v pre date; do
    i=$((i + 1))
    versions+=("$v")
    label="v$v"
    [[ -n "$date" ]] && label+="  ($date)"
    if [[ "$pre" == true ]]; then
      label+="  [pre-release]"
    elif [[ -z "$latest_idx" ]]; then
      latest_idx=$i
      label+="  [latest]"
    fi
    labels+=("$label")
  done <<<"$releases"
  local default="${latest_idx:-1}"

  if ! { exec 3</dev/tty; } 2>/dev/null; then
    TOOL_VERSION="${versions[default - 1]}"
    echo "No terminal to choose a version on; installing v$TOOL_VERSION (pass VERSION or --latest to be explicit)."
    return
  fi

  {
    echo
    echo "Available $TOOL_NAME releases:"
    for ((i = 0; i < ${#versions[@]}; i++)); do
      printf '  %2d) %s\n' $((i + 1)) "${labels[i]}"
    done
    echo
  } >/dev/tty

  local reply
  while true; do
    printf 'Choose a release [%d]: ' "$default" >/dev/tty
    if ! read -r reply <&3; then
      echo >/dev/tty
      echo "Error: no choice made." >&2
      exit 1
    fi
    reply="${reply:-$default}"
    if [[ "$reply" =~ ^[0-9]+$ ]] && ((reply >= 1 && reply <= ${#versions[@]})); then
      TOOL_VERSION="${versions[reply - 1]}"
      break
    fi
    # Also accept a version typed directly, e.g. "2.0.2" or "v2.0.2"
    for v in "${versions[@]}"; do
      if [[ "$v" == "${reply#v}" ]]; then
        TOOL_VERSION="$v"
        break 2
      fi
    done
    echo "Invalid choice '$reply'. Enter a number from 1 to ${#versions[@]}." >/dev/tty
  done
  exec 3<&-
}

if [[ -n "$LOCAL_JAR" ]]; then
  if [[ ! -f "$LOCAL_JAR" ]]; then
    echo "Error: JAR file not found: $LOCAL_JAR" >&2
    exit 1
  fi
  LOCAL_JAR="$(cd "$(dirname "$LOCAL_JAR")" && pwd)/$(basename "$LOCAL_JAR")"
elif ! command -v curl >/dev/null 2>&1; then
  echo "Error: curl is required but not installed." >&2
  exit 1
fi

# Resolve version: use the argument if given, then the local JAR's file name
# (rsm-X.Y.Z.jar), then --latest; otherwise let the user pick a release.
TOOL_VERSION="${TOOL_VERSION#v}" # accept both "2.0.2" and "v2.0.2"
if [[ -z "$TOOL_VERSION" && -n "$LOCAL_JAR" ]]; then
  if [[ "$(basename "$LOCAL_JAR")" =~ ^$TOOL_NAME-([0-9]+\.[0-9]+\.[0-9]+)\.jar$ ]]; then
    TOOL_VERSION="${BASH_REMATCH[1]}"
  else
    echo "Error: can't tell the version from '$(basename "$LOCAL_JAR")'. Pass it too, e.g. ./install.sh 2.0.2 --jar FILE" >&2
    exit 1
  fi
elif [[ -z "$TOOL_VERSION" && "$USE_LATEST" != true ]]; then
  choose_version
elif [[ -z "$TOOL_VERSION" ]]; then
  echo "Looking up the latest release..."
  TOOL_VERSION="$(curl -fsSL "https://api.github.com/repos/$REPO/releases/latest" \
    | sed -n 's/.*"tag_name"[[:space:]]*:[[:space:]]*"v\{0,1\}\([^"]*\)".*/\1/p' \
    | head -n 1)" || true
  if [[ -z "$TOOL_VERSION" ]]; then
    echo "Error: could not determine the latest release. Pass a version, e.g. ./install.sh 2.0.2" >&2
    exit 1
  fi
fi

if [[ ! "$TOOL_VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "Error: invalid version '$TOOL_VERSION'. Expected format X.Y.Z (e.g. 2.0.2)." >&2
  exit 1
fi

JAR_NAME="$TOOL_NAME-$TOOL_VERSION.jar"

echo "Installing $TOOL_NAME v$TOOL_VERSION..."

# 0. Check for Java 17+
REQUIRED_JAVA=17
java_install_hint() {
  if [[ "$OS" == "macos" ]]; then
    echo "  brew install openjdk@$REQUIRED_JAVA" >&2
  elif command -v apt-get >/dev/null 2>&1; then
    echo "  sudo apt-get install openjdk-$REQUIRED_JAVA-jre" >&2
  elif command -v dnf >/dev/null 2>&1; then
    echo "  sudo dnf install java-$REQUIRED_JAVA-openjdk" >&2
  elif command -v pacman >/dev/null 2>&1; then
    echo "  sudo pacman -S jre$REQUIRED_JAVA-openjdk" >&2
  else
    echo "  https://adoptium.net/" >&2
  fi
}

if ! command -v java >/dev/null 2>&1; then
  echo "Error: Java $REQUIRED_JAVA or newer is required but 'java' was not found. Install it with:" >&2
  java_install_hint
  exit 1
fi
# `java -version` prints e.g. 'openjdk version "17.0.9"' or 'java version "1.8.0_392"'
JAVA_VERSION="$(java -version 2>&1 | sed -n 's/.* version "\([^"]*\)".*/\1/p' | head -n 1)"
JAVA_MAJOR="${JAVA_VERSION%%.*}"
if [[ "$JAVA_MAJOR" == "1" ]]; then
  JAVA_MAJOR="$(echo "$JAVA_VERSION" | cut -d. -f2)"
fi
JAVA_MAJOR="${JAVA_MAJOR%%[!0-9]*}" # strip suffixes like "-ea"
if [[ -z "$JAVA_MAJOR" ]]; then
  echo "Warning: could not determine Java version; make sure Java $REQUIRED_JAVA+ is installed."
elif ((JAVA_MAJOR < REQUIRED_JAVA)); then
  echo "Error: Java $REQUIRED_JAVA or newer is required, found Java $JAVA_VERSION. Install a newer one with:" >&2
  java_install_hint
  exit 1
fi

# 1. Check for pdflatex and the LaTeX packages the resume template uses
# (see the preamble in src/main/kotlin/LaTeXGenerator.kt). Checking only for
# pdflatex isn't enough: minimal installs lack titlesec, enumitem, etc.
TEX_FILES=(
  latexsym.sty fullpage.sty titlesec.sty marvosym.sty color.sty verbatim.sty
  enumitem.sty hyperref.sty fancyhdr.sty babel.sty english.ldf tabularx.sty
  glyphtounicode.tex mathptmx.sty
)
# TeX Live package names for the files above (used with tlmgr)
TLMGR_PACKAGES=(
  latex-bin latexsym preprint titlesec marvosym graphics tools enumitem
  hyperref fancyhdr babel babel-english pdftex psnfss
)

# MacTeX/BasicTeX install here, which isn't on PATH until a new shell starts
if [[ "$OS" == "macos" && -d /Library/TeX/texbin ]]; then
  PATH="$PATH:/Library/TeX/texbin"
fi

missing_tex_files() {
  if ! command -v pdflatex >/dev/null 2>&1 || ! command -v kpsewhich >/dev/null 2>&1; then
    echo "pdflatex"
    return
  fi
  local f
  for f in "${TEX_FILES[@]}"; do
    [[ -n "$(kpsewhich "$f" 2>/dev/null)" ]] || echo "$f"
  done
}

install_tex() {
  if [[ "$OS" == "linux" ]]; then
    if command -v apt-get >/dev/null 2>&1; then
      sudo apt-get update &&
        sudo apt-get install -y texlive-latex-base texlive-latex-recommended \
        texlive-latex-extra texlive-fonts-recommended
    elif command -v dnf >/dev/null 2>&1; then
      # Fedora packages provide tex(<file>) virtual names
      local deps=(/usr/bin/pdflatex) f
      for f in "${TEX_FILES[@]}"; do deps+=("tex($f)"); done
      sudo dnf install -y "${deps[@]}"
    elif command -v zypper >/dev/null 2>&1; then
      local deps=(/usr/bin/pdflatex) f
      for f in "${TEX_FILES[@]}"; do deps+=("tex($f)"); done
      sudo zypper --non-interactive install "${deps[@]}"
    elif command -v pacman >/dev/null 2>&1; then
      sudo pacman -S --needed --noconfirm texlive-basic texlive-latex \
        texlive-latexrecommended texlive-latexextra texlive-fontsrecommended
    elif command -v tlmgr >/dev/null 2>&1; then
      tlmgr install "${TLMGR_PACKAGES[@]}" || sudo tlmgr install "${TLMGR_PACKAGES[@]}"
    else
      return 1
    fi
  elif [[ "$OS" == "macos" ]]; then
    if ! command -v tlmgr >/dev/null 2>&1; then
      command -v brew >/dev/null 2>&1 || return 1
      # BasicTeX (~100 MB) instead of the full MacTeX (~5 GB); missing
      # packages are added with tlmgr below.
      brew install --cask basictex || return 1
      PATH="$PATH:/Library/TeX/texbin"
    fi
    sudo tlmgr update --self && sudo tlmgr install "${TLMGR_PACKAGES[@]}"
  fi
}

MISSING_TEX="$(missing_tex_files)"
if [[ -n "$MISSING_TEX" ]]; then
  echo "Missing LaTeX components needed by 'rsm compile': $(echo $MISSING_TEX)"
  if [[ "$SKIP_DEPS" == true ]]; then
    echo "Skipping LaTeX install (--skip-deps). Install a TeX distribution with these"
    echo "packages before using 'rsm compile': ${TLMGR_PACKAGES[*]}"
  elif ! confirm "Install them now? This uses your system package manager and may ask for your sudo password."; then
    echo "Skipping LaTeX install. Re-run with --yes to install automatically, or install"
    echo "a TeX distribution with these packages: ${TLMGR_PACKAGES[*]}"
  elif ! install_tex; then
    echo "Error: could not install LaTeX automatically. Install a TeX distribution" >&2
    echo "with these packages, then re-run this script: ${TLMGR_PACKAGES[*]}" >&2
    exit 1
  else
    MISSING_TEX="$(missing_tex_files)"
    if [[ -n "$MISSING_TEX" ]]; then
      echo "Warning: still missing after install: $(echo $MISSING_TEX)"
      echo "'rsm compile' may fail until these are installed."
    fi
  fi
fi

# 2. check if the installation directory exists, if not create it
if [[ ! -d "$INSTALL_DIR" ]]; then
  echo "Creating installation directory $INSTALL_DIR..."
  mkdir -p "$INSTALL_DIR"
fi

# 3. Download the JAR file if a verified copy doesn't already exist
#
# Downloads are verified against the SHA-256 digest GitHub publishes for each
# release asset, which proves the file is exactly what was uploaded. When no
# digest is available (API unreachable or rate-limited, very old assets, or a
# local --jar), we only check the ZIP signature to catch HTML error pages and
# empty files. (An earlier `unzip -t` check was dropped: it fails on harmless
# warnings and adds a dependency without proving much.)

# Is the file non-empty and does it start with the ZIP signature "PK\x03\x04"?
looks_like_jar() {
  [[ -s "$1" ]] && [[ "$(head -c 4 "$1" | od -An -tx1 | tr -d ' \n')" == "504b0304" ]]
}

sha256_of() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | cut -d' ' -f1
  elif command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$1" | cut -d' ' -f1
  else
    return 1
  fi
}

# Print the SHA-256 GitHub reports for this release's JAR asset (empty if unknown).
fetch_jar_digest() {
  curl -fsSL "https://api.github.com/repos/$REPO/releases/tags/v$TOOL_VERSION" 2>/dev/null |
    grep -oE '"(name|digest)": *"[^"]*"' |
    awk -v jar="$JAR_NAME" '
      { val = $0; sub(/^[^:]*: *"/, "", val); sub(/"$/, "", val) }
      /^"name"/ { want = (val == jar); next }
      /^"digest"/ && want && val ~ /^sha256:[0-9a-f]+$/ { sub(/^sha256:/, "", val); print val; exit }
    ' || true
}

# Verify a file: exact digest match when we have one, ZIP signature otherwise.
verify_jar() {
  local f="$1" actual
  if [[ -n "$EXPECTED_SHA256" ]]; then
    if actual="$(sha256_of "$f")"; then
      [[ "$actual" == "$EXPECTED_SHA256" ]]
      return
    fi
  fi
  looks_like_jar "$f"
}

JAR_PATH="$INSTALL_DIR/$JAR_NAME"
EXPECTED_SHA256=""
if [[ -z "$LOCAL_JAR" ]]; then
  EXPECTED_SHA256="$(fetch_jar_digest)"
  if [[ -z "$EXPECTED_SHA256" ]]; then
    echo "Note: no checksum available for $JAR_NAME; only a basic file check will be done."
  elif ! sha256_of /dev/null >/dev/null; then
    echo "Note: sha256sum/shasum not found; only a basic file check will be done."
  fi
fi

if [[ -n "$LOCAL_JAR" ]]; then
  if ! looks_like_jar "$LOCAL_JAR"; then
    echo "Error: $LOCAL_JAR is not a JAR file." >&2
    exit 1
  fi
  # Always copy: a local build can change without its version changing
  if [[ "$LOCAL_JAR" != "$JAR_PATH" ]]; then
    echo "Copying $LOCAL_JAR to $JAR_PATH..."
    cp -f "$LOCAL_JAR" "$JAR_PATH"
  fi
elif [[ -f "$JAR_PATH" ]] && verify_jar "$JAR_PATH"; then
  echo "$JAR_NAME already exists in $INSTALL_DIR. Skipping download."
else
  if [[ -f "$JAR_PATH" ]]; then
    echo "Existing $JAR_NAME doesn't match the release. Re-downloading..."
  fi
  DOWNLOAD_URL="https://github.com/$REPO/releases/download/v$TOOL_VERSION/$JAR_NAME"
  TMP_JAR="$(mktemp "$INSTALL_DIR/.$JAR_NAME.XXXXXX")"
  trap 'rm -f "$TMP_JAR"' EXIT

  echo "Downloading $DOWNLOAD_URL..."
  if ! curl -fL --retry 3 --progress-bar "$DOWNLOAD_URL" -o "$TMP_JAR"; then
    echo "Error: download failed. Check that release v$TOOL_VERSION exists:" >&2
    echo "  https://github.com/$REPO/releases" >&2
    exit 1
  fi
  if ! verify_jar "$TMP_JAR"; then
    if [[ -n "$EXPECTED_SHA256" ]]; then
      echo "Error: checksum mismatch for $JAR_NAME (expected sha256 $EXPECTED_SHA256). Aborting." >&2
    else
      echo "Error: downloaded file is not a JAR. Aborting." >&2
    fi
    exit 1
  fi
  [[ -n "$EXPECTED_SHA256" ]] && echo "Checksum verified (sha256)."
  mv -f "$TMP_JAR" "$JAR_PATH"
  trap - EXIT
fi
# 4. Create a wrapper script
WRAPPER_SCRIPT="$INSTALL_DIR/$TOOL_NAME"
echo "#!/usr/bin/env bash" > "$WRAPPER_SCRIPT"
echo "java --enable-native-access=ALL-UNNAMED -jar \"$INSTALL_DIR/$JAR_NAME\" \"\$@\"" >> "$WRAPPER_SCRIPT"
chmod +x "$WRAPPER_SCRIPT"


# 5. Add to PATH if not already present
# Note: we never `source` the rc file here. This script runs in its own bash
# process, so sourcing can't change the caller's shell (and zsh/fish syntax
# would break under bash). The user is told to restart their shell instead.
PATH_MARKER="# Added by $TOOL_NAME installer"
RC_FILE=""
PATH_UPDATED=false

case "$(basename "${SHELL:-}")" in
  bash)
    # macOS Terminal opens login shells, which read .bash_profile, not .bashrc
    if [[ "$OS" == "macos" ]]; then RC_FILE="$HOME/.bash_profile"; else RC_FILE="$HOME/.bashrc"; fi
    PATH_LINE="export PATH=\"\$PATH:$INSTALL_DIR\""
    ;;
  zsh)
    RC_FILE="${ZDOTDIR:-$HOME}/.zshrc"
    PATH_LINE="export PATH=\"\$PATH:$INSTALL_DIR\""
    ;;
  fish)
    RC_FILE="$HOME/.config/fish/conf.d/$TOOL_NAME.fish"
    PATH_LINE="fish_add_path -g \"$INSTALL_DIR\""
    ;;
esac

if [[ ":$PATH:" == *":$INSTALL_DIR:"* ]]; then
  : # already on PATH in this session
elif [[ -z "$RC_FILE" ]]; then
  echo "Unrecognized shell '${SHELL:-unknown}'. Add $INSTALL_DIR to your PATH manually."
elif [[ -f "$RC_FILE" ]] && grep -qF "$INSTALL_DIR" "$RC_FILE"; then
  # rc file already has it; the current session just hasn't reloaded yet
  PATH_UPDATED=true
else
  echo "Adding $INSTALL_DIR to PATH in $RC_FILE..."
  mkdir -p "$(dirname "$RC_FILE")"
  printf '\n%s\n%s\n' "$PATH_MARKER" "$PATH_LINE" >> "$RC_FILE"
  PATH_UPDATED=true
fi

# 6. Install shell completion (generated by `rsm completion <shell>`)
DATA_DIR="${XDG_DATA_HOME:-$HOME/.local/share}"
install_completion() {
  local shell_name="$1" target="$2"
  local tmp
  tmp="$(mktemp)"
  if "$WRAPPER_SCRIPT" completion "$shell_name" >"$tmp" 2>/dev/null && [[ -s "$tmp" ]]; then
    mkdir -p "$(dirname "$target")"
    mv -f "$tmp" "$target"
    echo "Installed $shell_name completion to $target"
  else
    rm -f "$tmp"
    echo "Warning: could not generate $shell_name completion (skipped)."
    return 1
  fi
}

case "$(basename "${SHELL:-}")" in
  bash)
    # Picked up automatically by the bash-completion package
    install_completion bash "$DATA_DIR/bash-completion/completions/$TOOL_NAME" || true
    ;;
  zsh)
    # Clikt's zsh script must be sourced (it sets up bashcompinit itself)
    ZSH_COMPLETION="$DATA_DIR/$TOOL_NAME/completion.zsh"
    if install_completion zsh "$ZSH_COMPLETION" && ! grep -qF "$ZSH_COMPLETION" "$RC_FILE" 2>/dev/null; then
      printf '\n%s\n%s\n' "$PATH_MARKER" "[[ -f \"$ZSH_COMPLETION\" ]] && source \"$ZSH_COMPLETION\"" >>"$RC_FILE"
      PATH_UPDATED=true
    fi
    ;;
  fish)
    # Loaded automatically from fish's completions directory
    install_completion fish "$HOME/.config/fish/completions/$TOOL_NAME.fish" || true
    ;;
esac

echo "$TOOL_NAME v$TOOL_VERSION installed successfully"
if [[ "$PATH_UPDATED" == true ]]; then
  echo "Restart your terminal (or run: source $RC_FILE) to start using '$TOOL_NAME'."
fi