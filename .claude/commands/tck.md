---
description: Run TCK tests to verify Fullerstack Substrates API compliance
---

## MANDATORY: Background Execution

Run TCK in background so user can observe output:

```bash
/workspaces/fullerstack-humainary/scripts/tck.sh $ARGUMENTS
```

**Use `run_in_background: true`** - this is required, not optional.

After launching, immediately tell the user the output file path so they can observe with:
```bash
tail -f <output_file>
```

## Usage

- `/tck` - Run all TCK tests
- `/tck CircuitTest` - Run only CircuitTest

## Expected Results

- **Pass:** 383 tests, 0 failures, 0 errors
- **Fail:** Any failures or errors
