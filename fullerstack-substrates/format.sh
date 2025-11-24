#!/bin/bash
# Format Java files using configured style

FILE=$1

if [ -z "$FILE" ]; then
  echo "Usage: ./format.sh <file.java>"
  exit 1
fi

# Simple formatter that respects .editorconfig
sed -i 's/\t/  /g' "$FILE"  # Convert tabs to 2 spaces
echo "Formatted: $FILE"
