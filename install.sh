#!/bin/bash
INSTALL_DIR="$HOME/.local/share/simp-plus"
mkdir -p "$INSTALL_DIR"
cp -r . "$INSTALL_DIR"
echo "export PATH=\"\$PATH:$INSTALL_DIR/bin\"" >> "$HOME/.bashrc"
echo "Installed! Restart your terminal or run: source ~/.bashrc"