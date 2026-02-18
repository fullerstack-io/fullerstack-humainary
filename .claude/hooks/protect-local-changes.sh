#!/bin/bash
# Hook to prevent Claude from overwriting local changes with git checkout/reset/restore
# Also prevents bypass via git show + cp/redirect

# Read JSON input from stdin
input=$(cat)

# Extract the command from tool_input
command=$(echo "$input" | jq -r '.tool_input.command // empty' 2>/dev/null)

# Only process Bash tool
tool_name=$(echo "$input" | jq -r '.tool_name // empty' 2>/dev/null)

if [ "$tool_name" != "Bash" ] || [ -z "$command" ]; then
  exit 0
fi

# Get list of modified files (tracked only)
modified_files=$(git diff --name-only 2>/dev/null; git diff --cached --name-only 2>/dev/null)

# Skip all checks if no modified files
if [ -z "$modified_files" ]; then
  exit 0
fi

# Check if command contains dangerous git operations
if echo "$command" | grep -qE 'git\s+(checkout|reset|restore|stash\s+drop|clean)'; then
  cat >&2 << 'EOF'
BLOCKED: Cannot run git command - there are uncommitted local changes.

Modified files:
EOF
  echo "$modified_files" >&2

  cat >&2 << 'EOF'

To proceed, either:
  1. Ask the user for explicit permission
  2. Commit the changes first
  3. User can run the command manually
EOF
  exit 2
fi

# Check for git show with redirect (bypass attempt)
if echo "$command" | grep -qE 'git\s+show\s+.*>'; then
  cat >&2 << 'EOF'
BLOCKED: git show with redirect detected - potential bypass of change protection.

This pattern (git show ref:path > file) can be used to overwrite local changes.

Modified files:
EOF
  echo "$modified_files" >&2

  cat >&2 << 'EOF'

To proceed, either:
  1. Ask the user for explicit permission
  2. Commit the changes first
  3. User can run the command manually
EOF
  exit 2
fi

# Check for cp commands that might overwrite modified files
if echo "$command" | grep -qE '\bcp\s+'; then
  for file in $modified_files; do
    # Check if the modified file path appears as a cp destination
    # Match: cp ... /path/to/file or cp ... file (at end of command or before &&/;/|)
    if echo "$command" | grep -qE "cp\s+.*\s+.*$(basename "$file")(\s*$|\s*[;&|])"; then
      cat >&2 << EOF
BLOCKED: cp command may overwrite modified file: $file

Modified files:
EOF
      echo "$modified_files" >&2

      cat >&2 << 'EOF'

To proceed, either:
  1. Ask the user for explicit permission
  2. Commit the changes first
  3. User can run the command manually
EOF
      exit 2
    fi
  done
fi

exit 0
