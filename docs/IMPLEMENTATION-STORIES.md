# Implementation Stories - Substrates API Compliance

**Project:** fullerstack-substrates
**Goal:** Achieve 100% Humainary Substrates API compliance
**Status:** 96% Complete → Need to fix Scheduler abstraction

---

## Story 1: Remove Scheduler Abstraction [CRITICAL]

**Priority:** P0 (Blocker)
**Effort:** 2-3 hours
**Dependencies:** None

### Description

Remove the proprietary `Scheduler` interface and refactor ProducerPipe and EmissionChannel to use the official Circuit.pipe() method as specified in the Substrates API.

### Background

The Scheduler interface is NOT part of the Substrates API. It was created as an internal abstraction but violates API purity. The official API specifies that Circuit provides `pipe()` methods for async dispatch.

### Acceptance Criteria

- [ ] File `circuit/Scheduler.java` deleted
- [ ] ProducerPipe refactored to eliminate Scheduler dependency
- [ ] EmissionChannel refactored to eliminate Scheduler dependency
- [ ] SequentialCircuit method `execute()` renamed to match internal needs (or removed if unused)
- [ ] All tests pass
- [ ] Code compiles without errors

### Technical Approach

#### Option 1: Use Circuit.pipe() for Async Dispatch (RECOMMENDED)

ProducerPipe should create an async pipe via `circuit.pipe(target)` to break synchronous call chains:

```java
// ProducerPipe.java - NEW APPROACH

public class ProducerPipe<E> implements Pipe<E> {

    private final Pipe<Capture<E>> dispatchPipe;  // Async pipe created by Circuit
    private final Subject<Channel<E>> channelSubject;
    private final Consumer<Capture<E>> subscriberNotifier;
    private final BooleanSupplier hasSubscribers;
    private final FlowRegulator<E> flow;

    public ProducerPipe(
        Circuit circuit,  // Pass Circuit directly, not Scheduler
        Subject<Channel<E>> channelSubject,
        Consumer<Capture<E>> subscriberNotifier,
        BooleanSupplier hasSubscribers,
        FlowRegulator<E> flow
    ) {
        this.channelSubject = requireNonNull(channelSubject);
        this.subscriberNotifier = requireNonNull(subscriberNotifier);
        this.hasSubscribers = requireNonNull(hasSubscribers);
        this.flow = flow;

        // Create async dispatch pipe using Circuit.pipe()
        // This breaks sync chains and ensures circuit-thread execution
        this.dispatchPipe = circuit.pipe(capture -> {
            subscriberNotifier.accept(capture);
        });
    }

    @Override
    public void emit(E value) {
        if (!hasSubscribers.getAsBoolean()) {
            return;  // Early exit optimization
        }

        // Apply transformations if present
        E transformed = (flow == null) ? value : flow.apply(value);
        if (transformed == null) {
            return;  // Filtered out
        }

        // Post to circuit via async pipe
        Capture<E> capture = new SubjectCapture<>(channelSubject, transformed);
        dispatchPipe.emit(capture);
    }

    @Override
    public void flush() {
        // No-op - pipe handles buffering
    }
}
```

#### Option 2: Direct Queue Access (Less Clean)

If Circuit exposes internal queue operations, could use those directly. However, this is less clean than using the official `pipe()` API.

### Files to Modify

1. **DELETE:** `src/main/java/io/fullerstack/substrates/circuit/Scheduler.java`

2. **REFACTOR:** `src/main/java/io/fullerstack/substrates/pipe/ProducerPipe.java`
   - Remove `import io.fullerstack.substrates.circuit.Scheduler`
   - Change constructor parameter from `Scheduler` to `Circuit`
   - Create async dispatch pipe via `circuit.pipe(callback)`
   - Update emit() to use dispatchPipe instead of scheduler.schedule()

3. **REFACTOR:** `src/main/java/io/fullerstack/substrates/channel/EmissionChannel.java`
   - Remove `import io.fullerstack.substrates.circuit.Scheduler`
   - Change to pass Circuit directly to ProducerPipe
   - Remove Scheduler cast

4. **UPDATE:** `src/main/java/io/fullerstack/substrates/circuit/SequentialCircuit.java`
   - Rename `execute()` to `enqueue()` or similar (internal method)
   - Or remove if no longer needed after refactoring

### Testing

1. Run existing unit tests - all should pass
2. Run TCK suite - should pass 381/381 tests
3. Verify async behavior:
   - Emissions still processed on circuit thread
   - Order determinism maintained
   - No deadlocks in await()

### DoD (Definition of Done)

- [ ] Code compiles
- [ ] All unit tests pass
- [ ] TCK tests pass (381/381)
- [ ] No references to Scheduler in codebase
- [ ] Code formatted with Spotless
- [ ] Commit message: "fix: Remove proprietary Scheduler abstraction, use Circuit.pipe() per API"

---

## Story 2: Clean Up Experimental Code [NICE-TO-HAVE]

**Priority:** P2 (Technical Debt)
**Effort:** 30 minutes
**Dependencies:** None

### Description

Move experimental prototype code out of main source tree to eliminate confusion and reduce maintenance burden.

### Acceptance Criteria

- [ ] Experimental files moved to `src/test/java` or deleted
- [ ] No references to experimental code from production paths
- [ ] Build still succeeds

### Files to Move/Delete

1. `src/main/java/io/fullerstack/substrates/circuit/AsyncPipe.java` (61 lines)
2. `src/main/java/io/fullerstack/substrates/circuit/ExperimentalCircuit.java` (115 lines)
3. `src/main/java/io/fullerstack/substrates/circuit/experimental/AllInOne.java` (422 lines)

### Approach

**Option A:** Delete (if not needed)
```bash
rm src/main/java/io/fullerstack/substrates/circuit/AsyncPipe.java
rm src/main/java/io/fullerstack/substrates/circuit/ExperimentalCircuit.java
rm -rf src/main/java/io/fullerstack/substrates/circuit/experimental/
```

**Option B:** Move to test sources (if useful for reference)
```bash
mkdir -p src/test/java/io/fullerstack/substrates/experimental
mv src/main/java/io/fullerstack/substrates/circuit/AsyncPipe.java src/test/java/io/fullerstack/substrates/experimental/
mv src/main/java/io/fullerstack/substrates/circuit/ExperimentalCircuit.java src/test/java/io/fullerstack/substrates/experimental/
mv src/main/java/io/fullerstack/substrates/circuit/experimental/AllInOne.java src/test/java/io/fullerstack/substrates/experimental/
rmdir src/main/java/io/fullerstack/substrates/circuit/experimental/
```

### DoD

- [ ] No experimental code in src/main/java
- [ ] mvn clean install succeeds
- [ ] Commit message: "chore: Move experimental prototypes out of production code"

---

## Story 3: Run Full TCK Validation [VERIFICATION]

**Priority:** P1 (Critical Validation)
**Effort:** 1 hour
**Dependencies:** Story 1 (Scheduler removal)

### Description

Run the official Humainary Substrates TCK (Test Compatibility Kit) to verify 100% API compliance after Scheduler removal.

### Prerequisites

- Story 1 completed (Scheduler abstraction removed)
- Substrates API 1.0.0-PREVIEW installed in local Maven repo
- Java 25 active

### Acceptance Criteria

- [ ] TCK test suite runs successfully
- [ ] 381/381 tests pass
- [ ] 0 failures, 0 errors, 0 skipped
- [ ] Test report generated

### Commands

```bash
# Ensure Java 25 is active
source /usr/local/sdkman/bin/sdkman-init.sh
sdk use java 25.0.1-open

# Update Substrates API to latest
cd /workspaces/substrates-api-java
git pull
mvn clean install

# Run TCK against our implementation
cd /workspaces/substrates-api-java/tck
mvn clean test \
  -Dtck \
  -Dtck.spi.groupId=io.fullerstack \
  -Dtck.spi.artifactId=fullerstack-substrates \
  -Dtck.spi.version=1.0.0-SNAPSHOT

# Expected output:
# Tests run: 381, Failures: 0, Errors: 0, Skipped: 0
```

### Validation Checklist

- [ ] All Circuit tests pass (threading, await, close)
- [ ] All Conduit tests pass (routing, subscriptions)
- [ ] All Channel tests pass (pipe creation, flows)
- [ ] All Flow tests pass (transformations, fusion)
- [ ] All Subject tests pass (hierarchy, identity)
- [ ] All State tests pass (immutability, type matching)
- [ ] All Name tests pass (interning, hierarchy)
- [ ] All Scope tests pass (RAII, lifecycle)
- [ ] Temporal contract tests pass (Channel, Current, etc.)
- [ ] Performance tests meet targets (if included)

### DoD

- [ ] TCK report shows 381/381 passed
- [ ] Screenshot/log of test results saved
- [ ] README updated with TCK compliance badge
- [ ] Commit message: "test: Verify 100% TCK compliance (381/381 tests pass)"

---

## Story 4: Update Documentation [POLISH]

**Priority:** P2 (Documentation)
**Effort:** 1 hour
**Dependencies:** Story 1, Story 3

### Description

Update project documentation to reflect 100% API compliance and removal of proprietary abstractions.

### Acceptance Criteria

- [ ] CLAUDE.md updated to remove Scheduler references
- [ ] README.md updated with TCK compliance status
- [ ] ARCHITECTURE.md updated to reflect pure API implementation
- [ ] CRITICAL-FINDINGS.md updated or archived

### Files to Update

1. **CLAUDE.md**
   - Remove any Scheduler documentation
   - Update "Important Implementation Details" section
   - Add note about Circuit.pipe() usage

2. **README.md**
   - Add TCK compliance badge: "✅ 100% TCK Compliant (381/381 tests)"
   - Update quick start examples
   - Add link to official Substrates API docs

3. **docs/ARCHITECTURE.md**
   - Remove Scheduler from architecture diagrams
   - Update "Circuit Queue Architecture" section
   - Clarify use of Circuit.pipe() for async dispatch

4. **docs/CRITICAL-FINDINGS.md**
   - Add "RESOLVED" note at top
   - Keep as historical reference

### DoD

- [ ] All documentation accurate
- [ ] No references to Scheduler
- [ ] Examples use official API
- [ ] Commit message: "docs: Update documentation for 100% API compliance"

---

## Epic Summary

**Epic:** Achieve 100% Substrates API Compliance
**Total Stories:** 4
**Total Effort:** 4-5 hours
**Critical Path:** Story 1 → Story 3

**Completion Criteria:**
- [ ] All 4 stories completed
- [ ] TCK shows 381/381 tests passing
- [ ] No proprietary abstractions in codebase
- [ ] Documentation updated

**Benefits:**
- 100% API compliance guarantees
- Future-proof against API updates
- Can contribute to Substrates ecosystem
- Reference implementation for others
