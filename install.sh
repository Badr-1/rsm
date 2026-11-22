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

# Resolve version: use the argument if given, otherwise the latest GitHub release.
TOOL_VERSION="${1#v}" # accept both "2.0.2" and "v2.0.2"
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

# 1. Check for pdflatex
if ! command -v pdflatex >/dev/null 2>&1; then
  echo "pdflatex not found. Installing via TeX distribution..."
  if [[ "$OS" == "linux" ]]; then
    if command -v apt-get >/dev/null 2>&1; then
      sudo apt-get update
      sudo apt-get install -y texlive-latex-base
    elif command -v dnf >/dev/null 2>&1; then
      sudo dnf install -y texlive
    elif command -v pacman >/dev/null 2>&1; then
      sudo pacman -S --noconfirm texlive-latexextra
    else
      echo "Unsupported package manager. Please install TeX manually."
      exit 1
    fi
  elif [[ "$OS" == "macos" ]]; then
    if command -v brew >/dev/null 2>&1; then
      brew install --cask mactex
    else
      echo "Homebrew not found. Please install MacTeX manually."
      exit 1
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

echo "$TOOL_NAME v$TOOL_VERSION installed successfully"
if [[ "$PATH_UPDATED" == true ]]; then
  echo "Restart your terminal (or run: source $RC_FILE) to start using '$TOOL_NAME'."
fi