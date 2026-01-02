#!/bin/bash
# Hook to prevent Claude from overwriting local changes with git checkout/reset/restore

COMMAND="$CLAUDE_TOOL_INPUT"

# Check if command contains dangerous git operations
if echo "$COMMAND" | grep -qE 'git\s+(checkout|reset|restore|stash\s+drop|clean)'; then

  # Check for uncommitted changes to tracked files
  if ! git diff --quiet 2>/dev/null || ! git diff --cached --quiet 2>/dev/null; then
    echo "BLOCKED: Cannot run '$COMMAND' - there are uncommitted local changes."
    echo ""
    echo "Modified files:"
    git diff --name-only 2>/dev/null
    git diff --cached --name-only 2>/dev/null
    echo ""
    echo "To proceed, either:"
    echo "  1. Ask the user for explicit permission"
    echo "  2. Commit the changes first"
    echo "  3. User can run the command manually"
    exit 1
  fi
fi

exit 0
