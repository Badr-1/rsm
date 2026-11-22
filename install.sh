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

# 3. Download the JAR file if it doesn't already exist
if [[ -f "$INSTALL_DIR/$JAR_NAME" ]]; then
  echo "$JAR_NAME already exists in $INSTALL_DIR. Skipping download."
else
  curl -L "https://github.com/Badr-1/rsm/releases/download/v$TOOL_VERSION/$JAR_NAME" -o "$INSTALL_DIR/$JAR_NAME"
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