---
description: Run TCK tests to verify Fullerstack Substrates API compliance
---

**IMPORTANT: ALWAYS run the bash command with `run_in_background: true` to prevent interruption.**

Run the Substrates TCK (Test Compatibility Kit) to verify Fullerstack implementation passes all 381 API compliance tests.

## Steps

1. Run the TCK script:

```bash
/workspaces/fullerstack-humainary/scripts/tck.sh
```

2. Verify results:
   - **Expected:** 381 tests, 0 failures, 0 errors
   - **Status:** PASS if 381/381, FAIL otherwise

## Test Categories

The TCK validates:
- Circuit event ordering and processing
- Conduit channel creation and routing
- Flow transformations (sift, limit, sample, skip, etc.)
- Cell hierarchical computation
- State and Slot immutability
- Resource lifecycle management
- Serventis instrument APIs
