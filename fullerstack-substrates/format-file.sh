#!/bin/bash
# Reformat Java file to use 2-space indentation
FILE="$1"
if [ -z "$FILE" ]; then
  echo "Usage: ./format-file.sh path/to/file.java"
  exit 1
fi

# Convert 4 spaces to 2 spaces at start of lines
sed -i 's/^    /  /g; s/^        /    /g; s/^            /      /g; s/^                /        /g' "$FILE"
echo "Reformatted: $FILE (4-space → 2-space indentation)"
