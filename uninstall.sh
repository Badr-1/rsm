#!/usr/bin/env bash
# Removes everything install.sh added: the JARs, the wrapper script, shell
# completions and the lines added to shell rc files. Java, LaTeX and your
# resume repositories are left untouched.
set -e

TOOL_NAME="rsm"
INSTALL_DIR="$HOME/.local/bin"
DATA_DIR="${XDG_DATA_HOME:-$HOME/.local/share}"
MARKER="# Added by $TOOL_NAME installer"

removed=false
remove() {
  local f
  for f in "$@"; do
    if [[ -e "$f" ]]; then
      rm -f "$f"
      echo "Removed $f"
      removed=true
    fi
  done
}

# 1. Wrapper script (only if it's the one install.sh created)
WRAPPER="$INSTALL_DIR/$TOOL_NAME"
if [[ -f "$WRAPPER" ]] && grep -q "$INSTALL_DIR/$TOOL_NAME-.*\.jar" "$WRAPPER"; then
  remove "$WRAPPER"
elif [[ -e "$WRAPPER" ]]; then
  echo "Skipping $WRAPPER: not created by the $TOOL_NAME installer."
fi

# 2. JARs (all installed versions) and leftover partial downloads
shopt -s nullglob
remove "$INSTALL_DIR/$TOOL_NAME"-*.jar "$INSTALL_DIR/.$TOOL_NAME"-*.jar.*
shopt -u nullglob

# 3. Shell completions
remove "$DATA_DIR/bash-completion/completions/$TOOL_NAME" \
  "$DATA_DIR/$TOOL_NAME/completion.zsh" \
  "$HOME/.config/fish/completions/$TOOL_NAME.fish" \
  "$HOME/.config/fish/conf.d/$TOOL_NAME.fish"
rmdir "$DATA_DIR/$TOOL_NAME" 2>/dev/null || true

# 4. Lines added to rc files: each is the marker comment, followed by one
# line, preceded by a blank line. Only remove the marker's line when it
# references our install or data directory.
clean_rc() {
  local rc="$1"
  [[ -f "$rc" ]] && grep -qF "$MARKER" "$rc" || return 0
  local tmp
  tmp="$(mktemp)"
  awk -v marker="$MARKER" -v dir1="$INSTALL_DIR" -v dir2="$DATA_DIR/$TOOL_NAME" '
    pending_blank && $0 != marker { print ""; pending_blank = 0 }
    $0 == "" && !pending_blank { pending_blank = 1; next }
    $0 == marker {
      if ((getline next_line) > 0) {
        if (index(next_line, dir1) || index(next_line, dir2)) { pending_blank = 0; next }
        if (pending_blank) { print ""; pending_blank = 0 }
        print; print next_line; next
      }
    }
    { print }
    END { if (pending_blank) print "" }
  ' "$rc" >"$tmp"
  if ! cmp -s "$rc" "$tmp"; then
    cp "$rc" "$rc.$TOOL_NAME-uninstall.bak"
    cat "$tmp" >"$rc" # keep the original file's permissions and symlinks
    echo "Cleaned $rc (backup: $rc.$TOOL_NAME-uninstall.bak)"
    removed=true
  fi
  rm -f "$tmp"
}

for rc in "$HOME/.bashrc" "$HOME/.bash_profile" "${ZDOTDIR:-$HOME}/.zshrc"; do
  clean_rc "$rc"
done

if [[ "$removed" == true ]]; then
  echo "$TOOL_NAME uninstalled. Restart your terminal to apply the PATH change."
  echo "Java, LaTeX and your resume repositories were not touched."
else
  echo "$TOOL_NAME doesn't appear to be installed. Nothing to do."
fi
