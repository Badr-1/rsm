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

if ! command -v curl >/dev/null 2>&1; then
  echo "Error: curl is required but not installed." >&2
  exit 1
fi

usage() {
  cat <<EOF
Usage: install.sh [VERSION] [options]

Install $TOOL_NAME. VERSION defaults to the latest release (e.g. 2.0.2 or v2.0.2).

Options:
  -y, --yes        Install missing system packages (LaTeX) without asking
      --skip-deps  Don't install system packages; only warn if they're missing
  -h, --help       Show this help
EOF
}

ASSUME_YES=false
SKIP_DEPS=false
TOOL_VERSION=""
while [[ $# -gt 0 ]]; do
  case "$1" in
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

# Resolve version: use the argument if given, otherwise the latest GitHub release.
TOOL_VERSION="${TOOL_VERSION#v}" # accept both "2.0.2" and "v2.0.2"
if [[ -z "$TOOL_VERSION" ]]; then
  echo "No version given, looking up the latest release..."
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

# 3. Download the JAR file if a valid copy doesn't already exist
is_valid_jar() {
  local f="$1"
  [[ -s "$f" ]] || return 1
  if command -v unzip >/dev/null 2>&1; then
    unzip -tqq "$f" >/dev/null 2>&1 || return 1
    unzip -l "$f" 2>/dev/null | grep -q "META-INF/MANIFEST.MF" || return 1
  else
    # Fallback: a JAR is a ZIP file, which starts with "PK\x03\x04"
    [[ "$(head -c 4 "$f" | od -An -tx1 | tr -d ' \n')" == "504b0304" ]] || return 1
  fi
}

JAR_PATH="$INSTALL_DIR/$JAR_NAME"
if [[ -f "$JAR_PATH" ]] && is_valid_jar "$JAR_PATH"; then
  echo "$JAR_NAME already exists in $INSTALL_DIR. Skipping download."
else
  if [[ -f "$JAR_PATH" ]]; then
    echo "Existing $JAR_NAME is corrupt or incomplete. Re-downloading..."
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
  if ! is_valid_jar "$TMP_JAR"; then
    echo "Error: downloaded file is not a valid JAR. Aborting." >&2
    exit 1
  fi
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