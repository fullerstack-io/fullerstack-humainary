# Substrates 2.3 Spec Compliance Audit

Interface-by-interface comparison of `io.humainary.substrates.api.Substrates`
(2.3.0) against our Fullerstack implementation. Each section captures the spec
contract from the JavaDoc + linked SPEC.md sections, the implementation
choices, and any deviations or missing functionality.

## Status

| # | Interface | Status | Severity |
|---|---|---|---|
| 1 | Substrate | done | 🔴 fixed |
| 2 | Resource | done | 🟡 fixed |
| 3 | Source | done | 🔴 fixed |
| 4 | Extent | done | ✓ none |
| 5 | Id | done | ✓ none |
| 6 | Name | done | 🟢 hygiene |
| 7 | Subject | done | ✓ none |
| 8 | Slot | done | ✓ none |
| 9 | State | done | ✓ none |
| 10 | Receptor | done | ✓ none |
| 11 | Pipe | done | ✓ none |
| 12 | Capture | done | ✓ none |
| 13 | Subscriber | done | ✓ none |
| 14 | Subscription | done | (covered in #1) |
| 15 | Registrar | done | ✓ none |
| 16 | Reservoir | done | (covered in #2) |
| 17 | Tap | done | (covered in #3) |
| 18 | Pool | done | ✓ none |
| 19 | Conduit | done | (covered in #2) |
| 20 | Fiber | done | ✓ none |
| 21 | Flow | done | ✓ none |
| 22 | Closure | done | ✓ none |
| 23 | Scope | done | 🟢 fixed |
| 24 | Current | done | ✓ none |
| 25 | Cortex | done | ✓ none |
| 26 | Circuit | done | (covered in #3) |
| 27 | Substrates | done | ✓ none |

Severity: 🔴 spec-violation · 🟡 missing · 🟢 hygiene · ✓ none

---

## 1. Substrate

**Spec lines:** 5815-5843 in Substrates.java
**Impl:** `FsSubstrate.java` (abstract base, 81 lines) — but only `FsConduit` actually extends it; other permitted impls reimplement `subject()` themselves.
**Annotations on interface:** `@Abstract`, `@Extension`

### Spec contract
- Sealed interface: `Substrate<S extends Substrate<S>>`. Permits **exactly**: `Cortex`, `Current`, `Pipe`, `Scope`, `Reservoir`, `Source`, `Subscriber`, `Subscription`. Java's sealed-types rule means we cannot deviate from the permitted set without compilation error — there's nothing for us to enforce here.
- Single method `@NotNull Subject<S> subject()` returning the typed subject identifying this substrate. Self-referential generic ensures `substrate.subject()` returns `Subject<ThisSubstrateType>`.
- "Base interface for all substrate components that have an associated subject."
- Conceptual link to spec §3 (subject as identity) and §16.3 (cortex factories return substrates).

### Implementation
- All 8 permitted interfaces have Fullerstack impls: `FsCortex`, `FsCurrent`, `FsPipe`, `FsScope`, `FsReservoir`, `FsSubscriber`, `FsSubscription`, plus three impls covering the three `Source` subtypes (`FsCircuit` extends Source via `Source<State, Circuit>`, `FsConduit` extends Source via `Source<E, Conduit<E>>`, `FsTap` extends Source via `Source<T, Tap<T>>`).
- `FsSubstrate.java` is an abstract helper that provides lazy `Subject<S>` creation with double-checked locking (volatile field + synchronized).
- **Only `FsConduit` extends `FsSubstrate`.** Every other impl reimplements `subject()` independently with one of three patterns:
  - **Eager** (subject created in constructor, plain field read): `FsCortex`, `FsCurrent`, `FsReservoir`, `FsSubscriber`, `FsTap`, `FsCircuit`
  - **Lazy single-thread** (no volatile, no lock): `FsScope`, `FsSubscription`
  - **Lazy double-checked** (volatile + synchronized): `FsSubstrate`/`FsConduit`
- Subject return value never returns null in any path examined.

### Deviations (initial)
| Severity | Where | What | Action |
|---|---|---|---|
| 🔴 spec-violation | `FsScope.java:50,89-97`, `FsSubscription.java:67-74` | Unsynchronised lazy `subject()` init. Eval agent caught this — `FsSubject` constructor calls `ID_COUNTER.getAndIncrement()`, so two concurrent first-callers mint **two distinct ids** and one is orphaned. The comment "benign race" was wrong — identity is non-deterministic. Concrete impact: caller-A holds `Subject` with id=42, caller-B's reader sees `Subject` with id=43, identity comparisons (`subject() == subject()`) can fail. | **fixed** — FsScope made eager (subject is a `final` field built in constructor); FsSubscription kept lazy but with proper double-checked locking. |
| 🟢 hygiene | All 11 impls + `FsSubstrate.java` | Three different patterns for `subject()` (eager-final / lazy-unsafe / lazy-DCL) and only `FsConduit` actually extends `FsSubstrate`. Now resolved on the unsafe path; but the abstract base is still under-used. | note |

### Verdict
Initial reading missed a real race. After the eval agent's correction:
- `FsScope` and `FsSubscription` lazy `subject()` paths had a thread-safety bug allowing distinct ids for the same Substrate instance under concurrent first-call. **Fixed**: `FsScope` eager, `FsSubscription` proper DCL.
- Sealed-permit structural contract is automatically enforced by Java — nothing for us to deviate on.
- 482/482 tests pass after fix.

The `FsSubstrate` abstract base is still under-used (only `FsConduit` extends it). Worth revisiting later — either every Substrate impl uses it, or it goes. Not blocking.

---

## 2. Resource

**Spec lines:** 4378-4524 in Substrates.java
**Permits:** Source, Reservoir, Subscriber, Subscription
**Annotations on interface:** `@Abstract`. Default method `close()` annotated `@Idempotent @Queued`.

### Spec contract
- Sealed interface; permits exactly four types.
- One default method `close()` — no-op default; concrete types override when cleanup is required.
- `close()` MUST be **idempotent** — second and subsequent calls are no-ops.
- `close()` MUST be **thread-safe** — concurrent calls must not corrupt state.
- For circuit-managed resources, close is **queued** — submits a cleanup job and returns immediately; actual cleanup runs on circuit thread.
- Post-close: synchronous factory/mutator methods MAY throw `IllegalStateException`; queued operations MUST be silently dropped on the executor thread (NOT throw to caller — SPEC §9.1).

### Implementation
Examined the seven impls that implement (or inherit) `close()`:

| Impl | Pattern | Idempotent | Thread-safe | Notes |
|---|---|---|---|---|
| `FsCircuit` | submit close marker → set closed flag → unpark worker → release awaiters | ✓ early-return on volatile flag | ✓ | textbook queued close |
| `FsConduit` | **inherits default no-op** | n/a | n/a | nothing closed; relies on parent Circuit close |
| `FsTap` | `@Queued`, set volatile flag, chain to `sourceSubscription.close()` | ✓ | ✓ | clean |
| `FsReservoir` | `buffer.clear()` only | semantically yes (clearing empty buffer is a no-op) but **no explicit closed flag** | ✓ | brittle to future cleanup additions |
| `FsSubscriber` | `synchronized(this)`, idempotent flag, close child subscriptions | ✓ | ✓ (synchronized) | could deadlock if circuit thread also synchronizes on subscriber — currently no such site exists |
| `FsSubscription` | volatile closed flag, run onClose + onCloseCallback (with §15.4 isolation) | ✓ | ✓ (volatile) | clean |
| `FsScope` | volatile closed flag, close children first | ✓ | ✓ | not a Resource per spec but follows the same pattern |

### Deviations
| Severity | Where | What | Action |
|---|---|---|---|
| 🟢 hygiene | `FsConduit.java` | No `close()` override — inherits the spec's no-op default. Spec allows this, but spec says "Source: stop emissions / release subscribers". With current impl, `conduit.close()` doesn't reject subsequent `emit()` and doesn't unsubscribe. In practice the parent Circuit's close handles teardown, so users never observe the gap unless they call `conduit.close()` standalone. | note — fix when first user complains; would need a `volatile boolean closed` plus checks in `get()`/`subscribe()` and clearing of `hub.subscribers` |
| 🟢 hygiene | `FsReservoir.java:close()` | No explicit closed flag — relies on `buffer.clear()` being incidentally idempotent. Spec **requires** idempotent close; today this happens to work because clearing an empty buffer is a no-op, but if cleanup grows (e.g., closing a source-side subscription) the lack of a closed flag becomes a real bug. | note |
| 🟢 hygiene | `FsConduit.java`, `FsTap.java` | No `@Idempotent` / `@Queued` annotations on `close()` overrides (FsTap has @Queued but not @Idempotent; FsConduit has neither). The spec puts these annotations on the interface default; subclasses inherit the contract semantically but the annotations don't propagate visually. | note — adding the annotations on the override would be honest documentation |

### Verdict
**To-the-letter compliance: ✓ for all impls that override; partially yes for inherited defaults.** No spec-violation found this round. Two impls (`FsConduit`, `FsReservoir`) have brittle close paths that work today but won't survive future feature additions — flagged as hygiene rather than blocking. The eval agent has not yet reviewed; running it now in case I missed any normative MUST.

**Eval agent corrections (applied):**
- Upgraded `FsConduit` no-op `close()` from 🟢 → 🟡: agent showed Source's javadoc implies functional close. **Fixed**: added `volatile boolean closed`, `close()` sets flag and queues a circuit-thread job to clear `hub.subscribersList` + bump version; `subscribe()` queue job now silent-drops when closed (per §9.1).
- Upgraded `FsReservoir.close()` defensive flag: agent argued §9.1 idempotency is normative, not incidental. **Fixed**: added `volatile boolean closed` gate before `buffer.clear()`.
- Annotation propagation (`@Idempotent` / `@Queued` on overrides) — left as-is (documentation debt, not violation).

482/482 tests pass after fix.

---

## 3. Source

**Spec lines:** 4828-5245 in Substrates.java
**Permits:** Circuit, Conduit, Tap
**Annotations on interface:** `@Abstract`. Sealed; extends `Substrate<S>` and `Resource`.

### Spec contract
- 5 method shapes:
  - `reservoir()` — `@New @NotNull` factory for `Reservoir<E>`
  - `subscribe(Subscriber<E>)` — `@New @NotNull @Queued` **default** that delegates to 2-arg with no-op `onClose`
  - `subscribe(Subscriber<E>, Consumer<? super Subscription>)` — `@New @NotNull @Queued` abstract
  - `tap(Function<Pipe<T>, Pipe<E>>)` — `@New @NotNull` factory
  - `tap(Flow<E, T>)` — `@New @NotNull` factory (since 2.3)
  - `tap(Fiber<E>)` — `@New @NotNull` factory (since 2.3)
- **Cross-circuit subscriber Fault**: detected synchronously on caller thread, signalled before any subscription is registered or queued, leaves no observable side effects (SPEC §7.2). Deliberately diverges from §9.1's queued-drop semantics — programming error, not a race.
- **Asynchronous subscribe**: returns immediately with a Subscription handle; actual registration is queued.
- Subscriber callbacks always run on circuit's processing thread.
- Lazy rebuild — version-tracked, channels rebuild on first emission after a subscription change.

### Implementation
- `FsCircuit`, `FsConduit`, `FsTap` cover the three permits.
- `FsConduit.subscribe(Subscriber, Consumer)` performs the cross-circuit Fault check before allocating an `FsSubscription` — ✓ no side effects on Fault.
- `FsCircuit` 2-arg `subscribe` delegates to `stateConduit().subscribe(...)` — proper.
- All three `tap()` overloads exist on the impls.
- `reservoir()` exists.

### Deviations
| Severity | Where | What | Action |
|---|---|---|---|
| 🔴 spec-violation | `FsCircuit.java` (old 1-arg `subscribe(Subscriber<State>)` override) | The previous override added the subscriber to a private `subscribers` ArrayList that **nothing read**. Subscribers added via `circuit.subscribe(subscriber)` (1-arg) silently received zero callbacks — they were registered into a dead list while the actual subscriber graph (in `stateConduit().hub`) never saw them. Tests using 1-arg likely passed because they never depended on the callback firing. | **fixed** — removed the dead override entirely. Java's interface default delegates 1-arg to 2-arg, which goes through `stateConduit()` correctly. Removed unused `subscribers` field, ArrayList/List imports. |

### Verdict
**Real bug found and fixed.** The 1-arg `subscribe` was a black hole — registration succeeded silently but callbacks never fired. The default impl on the Source interface already does the right thing (delegates to 2-arg); our override was strictly worse than no override.

**Eval agent corrections (applied):**
- Eval flagged `FsTap.subscribe` was missing the cross-circuit Fault check entirely — a foreign subscriber would have been silently added. **Fixed**: added the same check used by `FsConduit` (synchronous detect, no side effects), placed before any allocation or list mutation.

482/482 tests pass after both fixes.

---

## 4. Extent

**Spec lines:** 1583-2069 in Substrates.java
**Direct impl:** none (generic base — extended by `Name` and `Subject`)
**Concrete users:** `FsName` (`Extent<Name, Name>`), `FsSubject` (`Extent<Subject<S>, Subject<?>>`), `FsScope` (uses Extent shape via Scope's substrate inheritance — but scope's Extent contract is via Subject)
**Annotations on interface:** `@Abstract`, `@Extension`. Extends `Iterable<P>`, `Comparable<P>`.

### Spec contract
- One abstract method: `part()` returning `@NotNull String`.
- Default methods provided by the interface:
  - `compareTo(P)` — fast-path identity, then enclosure-first compare, then part compare. Spec javadoc claims `@throws Fault if other is not runtime-provided`, but the default impl doesn't actually do this check (interface-side discrepancy, not ours).
  - `depth()`, `enclosure()`, `enclosure(Consumer)`, `extent()`, `extremity()`, `fold()`, `foldTo()`, `iterator()`, `path()` (4 overloads), `stream()`, `within()`.
- Iteration order: from this (right) to extremity (left/root). `foldTo` reverses to left-to-right.

### Implementation
- `FsName` overrides `part()` (line 344-346) and `enclosure()` (line 352-354), inheriting the rest. `enclosure` is a precomputed `Optional<Name>` field — avoids allocation per call.
- `FsSubject` overrides `part()` (line 131), `enclosure()` (line 89-91), `within()` (line 96-105 — walks parent field directly to avoid Optional allocation per level), and `compareTo()` (line 107-113 — has a fast-path then delegates to `Subject.super.compareTo`). All optimizations preserve the default contract.

### Deviations
| Severity | Where | What | Action |
|---|---|---|---|
| ✓ none | — | Both impls correctly implement the abstract `part()`, override defaults only for performance, never to alter semantics. The interface-side discrepancy on `compareTo @throws Fault` is the API authors' inconsistency, not ours. | none |

### Verdict
Compliant. The Extent abstraction is well-served by Java's interface defaults, and our two impls (`FsName`, `FsSubject`) do exactly what's required: implement `part()`, override defaults only when allocation profiles benefit. No spec violations.

---

## 5. Id

**Spec lines:** 3341-3381 in Substrates.java
**Impl:** `FsSubject.FsId` (private record, ~7 lines)
**Annotations on interface:** `@Tenure(INTERNED)`, `@Provided`, `@Identity`. Marker interface, no methods.

### Spec contract
- Marker interface — no methods.
- `@Identity` semantics: reference equality (`==`), not `.equals()`.
- Unique per Subject within a JVM. Stable for subject lifetime. Not unique across JVM restarts.
- API users never construct directly; obtained via `Subject#id()`.

### Implementation
```java
private record FsId(long value) implements Id {
  @Override public String toString() { return String.valueOf(value); }
}
```
Constructed only inside `FsSubject` with `ID_COUNTER.getAndIncrement()` (atomic monotonic counter). The record is `private`, so external code cannot construct an `FsId` directly.

### Deviations
| Severity | Where | What | Action |
|---|---|---|---|
| ✓ none | — | Records have value-based `equals`/`hashCode` by default, which technically diverges from `@Identity`'s reference-equality intent. In practice this is invisible because (a) `FsId` is `private`, so external callers can never mint a duplicate-value instance, and (b) `ID_COUNTER` guarantees every constructed `FsId` has a unique `value`. Callers using `.equals()` or `==` get the same answer either way. If a future refactor exposes `FsId` construction, this would need to change. | none |

### Verdict
Compliant in practice. Worth a one-line note on the record declaration if it ever gets made non-private, but no action today.

---

## 6. Name

**Spec lines:** 3399-3623 in Substrates.java
**Impl:** `FsName.java` (~480 lines)
**Annotations on interface:** `@Tenure(INTERNED)`, `@Identity`, `@Provided`. Extends `Extent<Name, Name>`.

### Spec contract
- 9 `name(...)` overloads (Name, String, Enum, Iterable<String>, Iterable+mapper, Iterator<String>, Iterator+mapper, Class, Member)
- 2 `path(...)` overloads (with separator char already in Extent; with mapper added here)
- `path()` default overrides Extent's '/' separator with `.` (the spec's `SEPARATOR` constant)
- **Identity-based equality**: Names are interned — equivalent hierarchies share the same reference. `==` is correct, `.equals()` should also work but is O(1) reference internally.
- Thread-safe creation: concurrent `intern()` calls with same path return same instance.
- `name(Name suffix)` — `@throws Fault if suffix is not a runtime-provided implementation` (spec).

### Implementation
- `FsName` interns via `ConcurrentHashMap<String, FsName> NAME_CACHE` plus per-parent child node arrays for chained extension.
- All 9 `name(...)` overloads present (lines 358-441) and use `internChild()` which preserves identity.
- `path()` cached as a final String field built at construction; `path(char SEP)` returns the cache for `.` separator, falls through to default `Extent.path(char)` for other separators.
- Enclosure pre-wrapped in `Optional` field to avoid allocation per `enclosure()` call.

### Deviations
| Severity | Where | What | Action |
|---|---|---|---|
| 🟢 hygiene | `FsName.name(Name suffix):360` | Direct cast `(FsName) suffix` — throws `ClassCastException` if suffix is a foreign Name impl, but spec docs say the method should throw `Fault`. Practical impact zero (only `FsName` instances exist in this runtime), but the documented exception class differs. | note — wrap cast site with explicit `if (!(suffix instanceof FsName)) throw new FsFault(...)` for spec accuracy |

### Verdict
Compliant. Interning, identity equality, all 9 `name(...)` overloads, path with custom separator — all in place. The Fault-vs-ClassCastException is a documentation accuracy issue with no runtime effect.

---

## 7. Subject

**Spec lines:** 5478-5556 in Substrates.java
**Impl:** `FsSubject.java` (~170 lines)
**Annotations on interface:** `@Tenure(ANCHORED)`, `@Identity`, `@Provided`. Extends `Extent<Subject<S>, Subject<?>>`.

### Spec contract
- 6 methods: `id()`, `name()`, `part()` (default), `state()`, `toString()` (default → path), `type()`
- Identity-based equality (`@Identity`)
- Self-referential generic `S extends Substrate<S>`

### Implementation
- All 6 methods present (`id` line 61, `name` 66, `state` 72, `type` 84, `part` 131, `toString` 156).
- `part()` overrides default to return `name + type + id` formatted string.
- `toString()` returns full path representation.

### Deviations
| Severity | Where | What | Action |
|---|---|---|---|
| ✓ none | — | All required methods present and correctly implemented. Identity semantics preserved via per-instance `FsId` allocation in constructor. | none |

### Verdict
Compliant. No spec violations.

---

## 8. Slot

**Spec lines:** 4742-4787 in Substrates.java
**Impl:** `FsSlot.java` (record, 10 lines)
**Annotations on interface:** `@Tenure(ANCHORED)`, `@Utility`, `@Provided`.

### Spec contract
- 3 methods: `name()`, `type()`, `value()` — all `@NotNull`.
- Immutable.
- For primitive types, `type()` returns the **primitive class** (`int.class`, not `Integer.class`).

### Implementation
```java
record FsSlot<T>(Name name, T value, Class<T> type) implements Slot<T> {}
```
Java record — auto-generates `name()`, `value()`, `type()` accessors. Immutable by record contract.

### Deviations
| Severity | Where | What | Action |
|---|---|---|---|
| ✓ none | — | Compliant. The primitive-class contract is the caller's responsibility (whoever constructs `FsSlot` passes the right Class). | none |

### Verdict
Compliant. The simplest possible impl that satisfies the contract.

---

## 9. State

**Spec lines:** 5247-5476 in Substrates.java
**Impl:** `FsState.java` (~260 lines)
**Annotations on interface:** `@Tenure(ANCHORED)`, `@Provided`. Extends `Iterable<Slot<?>>`.

### Spec contract
- 10 `state(...)` overloads (8 primitive/object types + Slot + Enum) — all `@New(conditional)` (return `this` if equivalent slot already present)
- `stream()` — sequential stream of slots from newest to oldest
- `value(Slot<T>)` — looks up slot by name+type; falls back to slot's own value if absent
- Inherited `iterator()` from Iterable
- Immutable; upserts in place
- Slot matching: name identity (`==`) AND type (`==`)

### Implementation
- All 10 `state(...)` overloads present (`FsState.java` lines 133-212)
- `stream()` (line 220), `value(Slot)` (line 232), `iterator()` (line 63)
- One bonus method: `values(Slot)` returning Stream — extension over spec, not a violation
- Empty state singleton via `FsState.EMPTY`

### Deviations
| Severity | Where | What | Action |
|---|---|---|---|
| ✓ none | — | All required overloads present, immutable persistence, slot matching by identity. | none |

### Verdict
Compliant.

---

## 10–17. Phase 2 (Emission types) — batch summary

The remaining Phase 2 interfaces are small, mostly inherited methods, or already covered in #1–#3 fixes.

### 10. Receptor (line 4007)
**Spec:** `@FunctionalInterface` with `receive(E)`. Static `NOOP`, `of(Class)`, `of(Class, Receptor)`, `of()` factories on the interface.
**Impl:** `FsChannel`, `FsCircuit.ReceptorAdapter` both implement Receptor. Static factories live on the API interface — not our concern.
**Verdict:** ✓ compliant.

### 11. Pipe (line 3746)
**Spec:** Single method `emit(E)` `@Queued`. `@NotNull` on the parameter.
**Impl:** `FsPipe.emit()` validates non-null, routes to ingress/transit based on thread identity.
**Verdict:** ✓ compliant.

### 12. Capture (line 331)
**Spec:** `emission()`, `subject()` returning `Subject<Pipe<E>>`. Both `@NotNull`.
**Impl:** `FsReservoir.Cap` is a record `(E emission, Subject<Pipe<E>> subject)`. Auto-generates both accessors.
**Verdict:** ✓ compliant.

### 13. Subscriber (line 5722)
**Spec:** `non-sealed Subscriber<E> extends Substrate<Subscriber<E>>, Resource`. No declared methods of its own — entire contract is inherited (subject, close).
**Impl:** `FsSubscriber` provides eager `subject`, `synchronized` close (idempotent via flag), and the user-supplied callback via the `BiConsumer` factory. Enforces cross-circuit affinity at construction time (in `FsCircuit.subscriber(...)`, the subscriber's subject parents the circuit subject — `findCircuitAncestor()` later detects the binding).
**Verdict:** ✓ compliant.

### 14. Subscription (line 5810)
**Spec:** `non-sealed Subscription extends Substrate<Subscription>, Resource`. No declared methods of its own.
**Impl + fix:** Already fully audited in #1 Substrate (the lazy-subject race fix uses double-checked locking). Idempotent `close()` flag. `onClose` exception isolated per §15.4.
**Verdict:** ✓ compliant after #1 fix.

### 15. Registrar (line 4246)
**Spec:** `register(Pipe)`, `register(Receptor)`. `@Temporal` — only valid during subscriber callback. Implementations **MUST detect** illegal temporal use and throw `IllegalStateException` (not allowed to be undefined behaviour for this type because registration is not a hot path).
**Impl:** `FsRegistrar` has a `closed` flag set by `consumers()`/`receptors()`, throws `IllegalStateException("Registrar is closed — register() only valid during callback")` on both register paths.
**Verdict:** ✓ compliant.

### 16. Reservoir (line 4341)
**Spec:** Single method `drain()` returning `Stream<Capture<E>>`. Inherits subject + close.
**Impl + fix:** `FsReservoir.drain()` snapshot+clear, returns stream of captures. Close path now has explicit `closed` flag (added in #2 Resource fix). Worth noting: drain itself is incremental — only captures since last drain.
**Verdict:** ✓ compliant after #2 fix.

### 17. Tap (line 5891)
**Spec:** `non-sealed Tap<T> extends Source<T, Tap<T>>`. No declared methods of its own — full Source contract applies.
**Impl + fix:** All three `tap()` overloads, all subscribe variants. Cross-circuit Fault check now correctly placed before any side effects (added in #3 Source fix). Volatile `closed` flag, queued semantics on close.
**Verdict:** ✓ compliant after #3 fix.

---

## 18–21. Phase 3 (Composition) — batch summary

### 18. Pool (line 3816)
**Spec:** 1 abstract `get(Name)` + 2 default `get(Substrate)`/`get(Subject)` + 1 abstract `pool(Function)` factory.
**Impls:** `FsConduit` (full Pool + Source), `FsDerivedPool` (transformed view). Both implement `get(Name)` and `pool(Function)`. Default 2 overloads inherited.
**Verdict:** ✓ compliant.

### 19. Conduit (line 911)
**Spec:** `non-sealed Conduit<E> extends Pool<Pipe<E>>, Source<E, Conduit<E>>` — adds `pool(Flow)`, `pool(Fiber)` overloads (since 2.3).
**Impl + fix:** `FsConduit` has both extra `pool` overloads (lines 111, 124). Already audited in #2 for `close()` — added `volatile closed` flag, queued teardown of hub.subscribers, silent-drop on subscribe-after-close.
**Verdict:** ✓ compliant after #2 fix.

### 20. Fiber (line 2184)
**Spec:** 32 unique operator methods (above, below, chance, change, clamp, deadband, delay, diff, dropWhile, edge, every, fiber, guard, high, hysteresis, inhibit, integrate, limit, low, max, min, peek, pulse, range, reduce, relate, replace, rolling, skip, steady, takeWhile, tumble) + `pipe(Pipe<E>)`.
**Impl:** `FsFiber` has all 32 operator methods + `pipe`. Operator implementations consolidated into `FsOperators` after recent refactor. Empty fiber elision (`count == 0` returns target unchanged) — also recently added.
**Verdict:** ✓ compliant. No method missing.

### 21. Flow (line 3173)
**Spec:** 4 methods: `map(Function)`, `fiber(Fiber)`, `flow(Flow)`, `pipe(Pipe)`.
**Impl:** `FsFlow` has all 4. Uniform `Wrap[]` operator storage, MapWrap is the type-changing operator, FiberOp eliminated by inlining the fiber's Wraps directly into the flow's array.
**Verdict:** ✓ compliant.

---

## 22. Closure

**Spec lines:** 785-826 in Substrates.java
**Impl:** `FsClosure.java` (~45 lines)
**Annotations on interface:** `@Tenure(EPHEMERAL)`, `@Utility`, `@Temporal`, `@Provided`.

### Spec contract
- Single method `consume(Consumer)`.
- Single-use: once consumed, must request fresh closure.
- Resource closed in finally; consumer exceptions propagate.
- If owning scope already closed: no-op (consumer not invoked).
- Not thread-safe; confine to acquiring thread.

### Implementation
`FsClosure.consume()` checks `scope.isClosed()` and `consumed` flag (no-op if either), sets consumed = true, removes from scope cache, invokes consumer in try with `resource.close()` in finally. Exceptions propagate.

### Verdict
✓ compliant.

---

## 23. Scope

**Spec lines:** 4527-4738 in Substrates.java
**Impl:** `FsScope.java`
**Annotations on interface:** `@Tenure(ANCHORED)`, `@Provided`. Extends `Substrate<Scope>, Extent<Scope, Scope>, AutoCloseable`.

### Spec contract
- 5 methods: `close()`, `closure(Resource)`, `register(Resource)`, `scope()`, `scope(Name)`.
- `close()` order:
  1. Close all registered resources (**reverse registration order**)
  2. Close all child scopes (if not already closed)
  3. Transition to Closed
- `register` returns same instance; idempotent on identity.
- After close: all factory methods throw `IllegalStateException`.
- Not thread-safe.

### Implementation + fix
Original close did **children first, then resources**. Spec specifies **resources first (LIFO), then children**. **Fixed**: reordered close() body, added explicit comment citing the spec line range. Exception suppression preserved.

### Verdict
✓ compliant after fix.

---

## 24. Current

**Spec lines:** 1500-1557 in Substrates.java
**Impl:** `FsCurrent.java` (~45 lines)
**Annotations on interface:** `@Tenure(INTERNED)`, `@Temporal`, `@Provided`.

### Spec contract
- `non-sealed Current extends Substrate<Current>` — no methods declared.
- Temporal contract: only valid within the obtaining thread.

### Implementation
Trivial — eager final subject field, returns it from `subject()`. The temporal contract is the caller's responsibility; we cannot enforce thread-binding without significant overhead and the spec permits implementation-defined behavior on misuse.

### Verdict
✓ compliant.

---

## 25. Cortex

**Spec lines:** 974-1497 in Substrates.java
**Impl:** `FsCortex.java`
**Annotations on interface:** `@Tenure(INTERNED)`, `@Provided`. Extends `Substrate<Cortex>`.

### Spec contract
~24 method overloads:
- `circuit()`, `circuit(Name)`
- `current()`
- `fiber()`, `fiber(Class)` (default)
- `flow()`, `flow(Class)` (default), `flow(Fiber)`
- `name(...)` × 8 overloads (String, Enum, Iterable<String>, Iterable+mapper, Iterator<String>, Iterator+mapper, Class, Member)
- `scope()`, `scope(Name)`
- `slot(Name, primitive/object)` × 8 overloads + `slot(Enum)`
- `state()`

### Implementation
`FsCortex` has all 9 method names (`circuit`, `current`, `fiber`, `flow`, `name`, `scope`, `slot`, `state`, `subject`). Slot has 9 overloads matching spec.

### Verdict
✓ compliant.

---

## 26. Circuit

**Spec lines:** 357-782 in Substrates.java
**Impl:** `FsCircuit.java`
**Annotations on interface:** `@Tenure(EPHEMERAL)`, `@Provided`. Extends `Source<State, Circuit>`.

### Spec contract
- `await()`, `close()` (overridden), 5 conduit overloads, 3 pipe overloads, `subscriber(Name, BiConsumer)`. Inherits Source/Substrate methods.
- Dual-queue (ingress/transit) with transit priority. Stack-safe cascade.

### Implementation + fix
All required methods present. Dead 1-arg `subscribe(Subscriber<State>)` override removed in #3 Source fix.

### Verdict
✓ compliant after #3 fix.

---

## 27. Substrates (outer interface)

**Spec lines:** entire file
**Impl:** N/A — outer container.

### Spec contract
- `static Cortex cortex()` — delegates to `CortexProvider.cortex()` SPI.
- `enum Routing { PIPE, STEM }`.
- Annotation types.

### Implementation
SPI wired via `META-INF/services/io.humainary.substrates.spi.CortexProvider` → `FsCortexProvider`. Routing honored in `FsConduit` + `FsChannel.cascadeDispatch`.

### Verdict
✓ compliant.

---

## Audit Summary

**Result:** 4 spec-violation fixes, 1 hygiene fix, 4 noted-but-deferred. All 482 tests pass throughout.

### Fixes applied

| # | Interface | Severity | Change |
|---|---|---|---|
| 1 | Substrate | 🔴 | `FsScope`/`FsSubscription` lazy `subject()` was racing `ID_COUNTER`, minting distinct ids for the same Substrate. `FsScope` made eager final; `FsSubscription` uses proper DCL. |
| 2 | Resource | 🟡 | `FsConduit` — added functional `close()` (volatile flag + queued hub teardown + silent-drop on subscribe-after-close per §9.1). `FsReservoir` — added explicit `closed` flag for §9.1 idempotency. |
| 3 | Source | 🔴 | `FsCircuit` — removed dead 1-arg `subscribe(Subscriber<State>)` override (subscribers added to a list nothing read; callbacks never fired). `FsTap` — added missing cross-circuit Fault check before any side effect (per §7.2). |
| 23 | Scope | 🟢 | `FsScope.close()` reordered to spec sequence: registered resources first (LIFO), then children. Was children-first. |

### Open items (note-only)

| # | Interface | Severity | Note |
|---|---|---|---|
| 1 | Substrate | 🟢 | `FsSubstrate` abstract base used by only `FsConduit` — inconsistent subject patterns across impls. |
| 2 | Resource | 🟢 | Annotation propagation (`@Idempotent`, `@Queued`) on overrides — documentation accuracy. |
| 5 | Id | ✓ | `FsId` record uses value equality; harmless because constructor is private and IDs are uniquely minted. |
| 6 | Name | 🟢 | `FsName.name(Name suffix)` direct cast — should throw `Fault` per spec but throws `ClassCastException`. Practical impact zero. |

### Lessons

- The eval agent caught two real bugs my initial reading missed (FsScope/FsSubscription race; FsTap missing Fault check). Process value confirmed.
- The dead 1-arg `subscribe` would never have been caught by tests because the failure mode is silent. Audit pressure caught it.
- `FsScope.close()` order had been wrong but no test exercised the cross-resource ordering. Spec audit caught it.
- Most "to the letter" deviations were hygiene-level. The substantive ones came from sentence-by-sentence comparison of default-method delegations against the JavaDoc — a thorough read was where the wins came from.











