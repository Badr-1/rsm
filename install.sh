#!/usr/bin/env bash
set -e

TOOL_NAME="rsm"
REPO="Badr-1/rsm"
INSTALL_DIR="$HOME/.local/bin"

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

# 1. Check for pdflatex
if ! command -v pdflatex >/dev/null 2>&1; then
  echo "pdflatex not found. Installing via TeX distribution..."
  if [[ "$OSTYPE" == "linux-gnu"* ]]; then
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
  elif [[ "$OSTYPE" == "darwin"* ]]; then
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
if [[ ":$PATH:" != *":$INSTALL_DIR:"* ]]; then
  echo "Adding $INSTALL_DIR to PATH..."
  if [[ "$SHELL" == *"bash"* ]]; then
    echo "export PATH=\"\$PATH:$INSTALL_DIR\"" >> "$HOME/.bashrc"
    source "$HOME/.bashrc"
  elif [[ "$SHELL" == *"zsh"* ]]; then
    echo "export PATH=\"\$PATH:$INSTALL_DIR\"" >> "$HOME/.zshrc"
    source "$HOME/.zshrc"
  else
    echo "Please add $INSTALL_DIR to your PATH manually."
  fi
fi


echo "$TOOL_NAME v$TOOL_VERSION installed successfully"