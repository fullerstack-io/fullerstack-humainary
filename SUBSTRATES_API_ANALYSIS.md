# Substrates API - Complete Interface Analysis

This document provides a comprehensive analysis of ALL interfaces in the Humainary Substrates API (version 1.0.0-PREVIEW), with focus on NON-SEALED implementation points that require concrete implementations.

Generated: 2025-11-24

---

## NON-SEALED INTERFACES (Implementation Required)

These are the interfaces that implementations MUST provide concrete classes for:

### 1. Circuit (Line 537)

**Definition:**
```java
non-sealed interface Circuit
  extends Source<State, Circuit>,
          Resource
```

**Hierarchy:**
- Extends: `Source<State, Circuit>` (requires implementing `subscribe()`)
- Extends: `Resource` (requires implementing `close()`)
- Also extends (via Source): `Substrate<Circuit>` (requires implementing `subject()`)

**Required Methods (NO default implementation):**

1. `void await()` - Block until circuit queue is empty, establishing happens-before relationship
   - Throws `IllegalStateException` if called from circuit thread (would deadlock)
   - Returns immediately after circuit closure

2. `<I, E> Cell<I, E> cell(Name name, Composer<E, Pipe<I>> ingress, Composer<E, Pipe<E>> egress, Pipe<? super E> pipe)` - Create hierarchical cell with specified name
   - Returns: New Cell instance

3. `<P extends Percept, E> Conduit<P, E> conduit(Name name, Composer<E, ? extends P> composer)` - Create conduit with name and composer
   - Returns: New Conduit instance

4. `<P extends Percept, E> Conduit<P, E> conduit(Name name, Composer<E, ? extends P> composer, Consumer<Flow<E>> configurer)` - Create conduit with Flow configuration
   - Returns: New Conduit instance

5. `<E> Pipe<E> pipe(Pipe<? super E> target)` - Create async pipe that dispatches via circuit queue
   - Returns: New Pipe instance that breaks synchronous call chains

6. `<E> Pipe<E> pipe(Pipe<? super E> target, Consumer<Flow<E>> configurer)` - Create async pipe with Flow transformations
   - Returns: New Pipe instance with Flow operations

**Methods with Defaults:**

7. `default <I, E> Cell<I, E> cell(Composer<E, Pipe<I>> ingress, Composer<E, Pipe<E>> egress, Pipe<? super E> pipe)` - Convenience method using circuit's name
   - Delegates to `cell(subject().name(), ingress, egress, pipe)`

8. `default <P extends Percept, E> Conduit<P, E> conduit(Composer<E, ? extends P> composer)` - Convenience method using circuit's name
   - Delegates to `conduit(subject().name(), composer)`

**Inherited from Source:**

9. `Subscription subscribe(Subscriber<State> subscriber)` - Subscribe to circuit state changes
   - Returns: New Subscription handle

**Inherited from Resource:**

10. `void close()` - Close circuit, release resources (has default no-op in Resource, but Circuit MUST override)
    - Marked `@Idempotent` - safe to call multiple times
    - Non-blocking - marks for closure, returns immediately

**Inherited from Substrate:**

11. `Subject<Circuit> subject()` - Return circuit's subject identity
    - Returns: Subject containing id, name, state, type

---

### 2. Conduit (Line 1160)

**Definition:**
```java
non-sealed interface Conduit<P extends Percept, E>
  extends Lookup<P>,
          Source<E, Conduit<P, E>>
```

**Hierarchy:**
- Extends: `Lookup<P>` (requires implementing `percept(Name)`)
- Extends: `Source<E, Conduit<P, E>>` (requires implementing `subscribe()`)
- Also extends (via Source): `Substrate<Conduit<P, E>>` (requires implementing `subject()`)

**Required Methods:**

**From Lookup:**

1. `P percept(Name name)` - Get or create percept by name (with caching)
   - Returns: Percept instance (cached, same name returns same instance)
   - Channels are pooled - same name = same channel internally

**Methods with Defaults (from Lookup):**

2. `default P percept(Substrate<?> substrate)` - Convenience method extracting name from substrate
   - Delegates to `percept(substrate.subject().name())`

3. `default P percept(Subject<?> subject)` - Convenience method extracting name from subject
   - Delegates to `percept(subject.name())`

**Inherited from Source:**

4. `Subscription subscribe(Subscriber<E> subscriber)` - Subscribe to emissions
   - Returns: New Subscription handle
   - Subscriber callback invoked lazily on first emission to channels

**Inherited from Substrate:**

5. `Subject<Conduit<P, E>> subject()` - Return conduit's subject identity

---

### 3. Channel (Line 407)

**Definition:**
```java
non-sealed interface Channel<E>
  extends Substrate<Channel<E>>
```

**Hierarchy:**
- Extends: `Substrate<Channel<E>>` (requires implementing `subject()`)

**Required Methods:**

1. `Pipe<E> pipe()` - Return pipe for emission
   - Returns: New pipe instance routing to this channel
   - Each call may return different wrapper, but all route to same channel

2. `Pipe<E> pipe(Consumer<Flow<E>> configurer)` - Return pipe with Flow configuration
   - Returns: New pipe with custom flow transformations
   - Multiple calls with different configurers return different pipes

**Inherited from Substrate:**

3. `Subject<Channel<E>> subject()` - Return channel's subject identity

**Note:** Channel is marked `@Temporal` - only valid during `Composer#compose(Channel)` callback. Do NOT retain channel references beyond callback scope.

---

### 4. Cell (Line 356)

**Definition:**
```java
non-sealed interface Cell<I, E>
  extends Pipe<I>,
          Lookup<Cell<I, E>>,
          Source<E, Cell<I, E>>,
          Extent<Cell<I, E>, Cell<I, E>>
```

**Hierarchy:**
- Extends: `Pipe<I>` (requires implementing `emit()`)
- Extends: `Lookup<Cell<I, E>>` (requires implementing `percept(Name)`)
- Extends: `Source<E, Cell<I, E>>` (requires implementing `subscribe()`)
- Extends: `Extent<Cell<I, E>, Cell<I, E>>` (many defaults, some may need override)

**Required Methods:**

**From Pipe:**

1. `void emit(I value)` - Emit input value to cell
   - Can be called from any thread (enqueued to circuit)

**From Lookup:**

2. `Cell<I, E> percept(Name name)` - Get or create child cell by name
   - Returns: Child cell instance (cached)

**Inherited Methods with Defaults (from Lookup):**

3. `default Cell<I, E> percept(Substrate<?> substrate)` - Get child by substrate name
4. `default Cell<I, E> percept(Subject<?> subject)` - Get child by subject name

**From Source:**

5. `Subscription subscribe(Subscriber<E> subscriber)` - Subscribe to cell outputs
   - Returns: Subscription handle for child cell emissions

**From Substrate:**

6. `Subject<Cell<I, E>> subject()` - Return cell's subject identity

**From Extent:**
- All methods have default implementations (iterator, stream, fold, path, etc.)
- May need to override `Optional<Cell<I, E>> enclosure()` if hierarchical parent exists

---

### 5. Cortex (Line 1203)

**Definition:**
```java
non-sealed interface Cortex
  extends Substrate<Cortex>
```

**Hierarchy:**
- Extends: `Substrate<Cortex>` (requires implementing `subject()`)

**Required Methods (ALL are factory methods for creating components):**

**Circuit Creation:**

1. `Circuit circuit()` - Create circuit with generated name
2. `Circuit circuit(Name name)` - Create circuit with specified name

**Current Context:**

3. `Current current()` - Return execution context (analogous to Thread.currentThread())

**Name Creation (11 overloads):**

4. `Name name(String path)` - Parse dot-separated path
5. `Name name(Enum<?> path)` - Create from enum constant
6. `Name name(Iterable<String> it)` - Create from iterable
7. `Name name(Iterable<? extends T> it, Function<T, String> mapper)` - Create from mapped iterable
8. `Name name(Iterator<String> it)` - Create from iterator
9. `Name name(Iterator<? extends T> it, Function<T, String> mapper)` - Create from mapped iterator
10. `Name name(Class<?> type)` - Create from class (canonical name)
11. `Name name(Member member)` - Create from member (class + member name)

**Pipe Creation:**

12. `<E> Pipe<E> pipe(Receptor<? super E> receptor)` - Wrap receptor as pipe
13. `<E> Pipe<E> pipe(Class<E> type, Receptor<? super E> receptor)` - Wrap receptor with type hint
14. `<I, E> Pipe<I> pipe(Function<? super I, ? extends E> transform, Pipe<? super E> target)` - Transform then emit to target
15. `<E> Pipe<E> pipe(Class<E> type)` - Empty pipe (discards emissions)
16. `<E> Pipe<E> pipe()` - Empty pipe (type-inferred)

**Reservoir Creation:**

17. `<E, S extends Source<E, S>> Reservoir<E> reservoir(Source<E, S> source)` - Create reservoir for capturing emissions

**Scope Creation:**

18. `Scope scope(Name name)` - Create named scope
19. `Scope scope()` - Create anonymous scope

**Slot Creation (8 overloads for different types):**

20. `Slot<Boolean> slot(Name name, boolean value)` - Boolean slot
21. `Slot<Integer> slot(Name name, int value)` - Int slot
22. `Slot<Long> slot(Name name, long value)` - Long slot
23. `Slot<Double> slot(Name name, double value)` - Double slot
24. `Slot<Float> slot(Name name, float value)` - Float slot
25. `Slot<String> slot(Name name, String value)` - String slot
26. `Slot<Name> slot(Enum<?> value)` - Name slot from enum
27. `Slot<Name> slot(Name name, Name value)` - Name slot
28. `Slot<State> slot(Name name, State value)` - State slot

**State Creation:**

29. `State state()` - Create empty state

**Subscriber Creation:**

30. `<E> Subscriber<E> subscriber(Name name, BiConsumer<Subject<Channel<E>>, Registrar<E>> subscriber)` - Create subscriber with callback

**Inherited from Substrate:**

31. `Subject<Cortex> subject()` - Return cortex subject (root of hierarchy)

---

### 6. Current (Line 1814)

**Definition:**
```java
non-sealed interface Current
  extends Substrate<Current>
```

**Hierarchy:**
- Extends: `Substrate<Current>` (requires implementing `subject()`)

**Required Methods:**

1. `Subject<Current> subject()` - Return current execution context subject
   - Analogous to `Thread.currentThread()`
   - Provides identity, name, state of execution context

**Note:** Current is marked `@Temporal` - only valid within the execution context (thread) that obtained it. Do NOT retain or use from different threads.

---

### 7. Reservoir (Line 3508)

**Definition:**
```java
non-sealed interface Reservoir<E>
  extends Substrate<Reservoir<E>>,
          Resource
```

**Hierarchy:**
- Extends: `Substrate<Reservoir<E>>` (requires implementing `subject()`)
- Extends: `Resource` (requires implementing `close()`)

**Required Methods:**

1. `Stream<Capture<E>> drain()` - Return stream of captured emissions and clear buffer
   - Returns: Stream of Capture instances (emission + subject)
   - Clears internal buffer after returning stream

**Inherited from Resource:**

2. `void close()` - Release reservoir resources (has default no-op, may need override)

**Inherited from Substrate:**

3. `Subject<Reservoir<E>> subject()` - Return reservoir's subject identity

---

### 8. Scope (Line 3738)

**Definition:**
```java
non-sealed interface Scope
  extends Substrate<Scope>,
          Extent<Scope, Scope>,
          AutoCloseable
```

**Hierarchy:**
- Extends: `Substrate<Scope>` (requires implementing `subject()`)
- Extends: `Extent<Scope, Scope>` (many defaults)
- Extends: `AutoCloseable` (requires implementing `close()`)

**Required Methods:**

1. `void close()` - Close scope and all registered resources
   - Marked `@Idempotent` - safe to call multiple times
   - Closes resources in reverse registration order (LIFO)
   - Closes child scopes
   - After close, throws `IllegalStateException` on register/closure/scope calls

2. `<R extends Resource> Closure<R> closure(R resource)` - Create block-scoped resource closure
   - Returns: Single-use closure for resource
   - Resource closed when `Closure#consume()` returns

3. `<R extends Resource> R register(R resource)` - Register resource for scope-lifetime management
   - Returns: Same resource instance (for fluent usage)
   - Resource closed when scope closes

4. `Scope scope()` - Create anonymous child scope
   - Returns: New child scope within this scope

5. `Scope scope(Name name)` - Create named child scope
   - Returns: New named child scope

**Inherited from Substrate:**

6. `Subject<Scope> subject()` - Return scope's subject identity

**Inherited from Extent:**
- All methods have default implementations (iterator, stream, fold, path, etc.)
- May need to override `Optional<Scope> enclosure()` for parent scope hierarchy

---

### 9. Subscriber (Line 4721)

**Definition:**
```java
non-sealed interface Subscriber<E>
  extends Substrate<Subscriber<E>>,
          Resource
```

**Hierarchy:**
- Extends: `Substrate<Subscriber<E>>` (requires implementing `subject()`)
- Extends: `Resource` (requires implementing `close()`)

**Required Methods:**

**NOTE:** Subscriber is a MARKER interface - it has NO required abstract methods beyond those inherited from Substrate and Resource. The actual subscription behavior is provided via the `BiConsumer` callback passed to `Cortex#subscriber()` factory method.

**Inherited from Resource:**

1. `void close()` - Unsubscribe from all sources
   - Marked `@Idempotent`
   - Has default no-op implementation, but may need override for full functionality

**Inherited from Substrate:**

2. `Subject<Subscriber<E>> subject()` - Return subscriber's subject identity

**Implementation Note:** Users never directly implement Subscriber. They create instances via `Cortex#subscriber(Name, BiConsumer<Subject<Channel<E>>, Registrar<E>>)`, which wraps their callback behavior.

---

### 10. Subscription (Line 4795)

**Definition:**
```java
non-sealed interface Subscription
  extends Substrate<Subscription>,
          Resource
```

**Hierarchy:**
- Extends: `Substrate<Subscription>` (requires implementing `subject()`)
- Extends: `Resource` (requires implementing `close()`)

**Required Methods:**

**NOTE:** Subscription is also effectively a MARKER interface with no additional abstract methods beyond inherited ones.

**Inherited from Resource:**

1. `void close()` - Cancel subscription, unregister subscriber
   - Marked `@Idempotent` - safe to call multiple times
   - Stops future subscriber callbacks
   - Removes pipes registered by subscriber (lazy rebuild)
   - Has default no-op, but implementations MUST override for functionality

**Inherited from Substrate:**

2. `Subject<Subscription> subject()` - Return subscription's subject identity

---

## SEALED INTERFACES (Framework Abstractions)

These interfaces are sealed and define which types can implement them. Applications use them but don't implement them directly.

### Source<E, S extends Source<E, S>> (Line 4207)

**Definition:**
```java
sealed interface Source<E, S extends Source<E, S>>
  extends Substrate<S>
  permits Circuit, Conduit, Cell
```

**Required Methods:**

1. `Subscription subscribe(Subscriber<E> subscriber)` - Subscribe to receive lazy callbacks
   - Returns: Subscription handle
   - Subscriber callback invoked on circuit thread during rebuild
   - Callbacks occur on first emission to channels after subscription

**Permitted Implementations:** Only Circuit, Conduit, and Cell can implement Source.

### Resource (Line 3597)

**Definition:**
```java
sealed interface Resource
  permits Circuit, Reservoir, Subscriber, Subscription
```

**Methods with Defaults:**

1. `default void close()` - No-op default implementation
   - Marked `@Idempotent`
   - Concrete types override for actual cleanup

**Permitted Implementations:** Only Circuit, Reservoir, Subscriber, Subscription can implement Resource.

### Substrate<S extends Substrate<S>> (Line 4811)

**Definition:**
```java
sealed interface Substrate<S extends Substrate<S>>
  permits Channel, Cortex, Current, Scope, Reservoir, Source, Subscriber, Subscription
```

**Required Methods:**

1. `Subject<S> subject()` - Return typed subject for this substrate
   - Provides id, name, state, type

**Permitted Implementations:** Only the listed types (Channel, Cortex, Current, Scope, Reservoir, Source, Subscriber, Subscription) can implement Substrate.

---

## PROVIDED INTERFACES (Utility Types - Implementation Optional)

These interfaces are provided by the framework but may have custom implementations.

### Capture<E> (Line 259)

**Definition:**
```java
@Provided
interface Capture<E>
```

**Required Methods:**

1. `E emission()` - Return emitted value
2. `Subject<Channel<E>> subject()` - Return subject of channel that emitted value

**Usage:** Produced by Reservoir.drain(). Pairs emission with channel subject.

### Composer<E, P extends Percept> (Line 957)

**Definition:**
```java
@FunctionalInterface
interface Composer<E, P extends Percept>
```

**Required Methods:**

1. `P compose(Channel<E> channel)` - Compose percept from channel
   - Called exactly once per channel (channels pooled by name)
   - Must return non-null percept

**Methods with Defaults:**

2. `default <R extends Percept> Composer<E, R> map(Function<? super P, ? extends R> after)` - Chain composers

**Static Factory Methods:**

3. `static <E> Composer<E, Pipe<E>> pipe()` - Returns composer that returns channel's pipe
4. `static <E> Composer<E, Pipe<E>> pipe(Class<E> type)` - Typed pipe composer
5. `static <E> Composer<E, Pipe<E>> pipe(Consumer<Flow<E>> configurer)` - Pipe with Flow config

### Flow<E> (Line 2409)

**Definition:**
```java
@Temporal
@Provided
interface Flow<E>
```

**Required Methods (ALL return Flow<E> for chaining):**

1. `Flow<E> diff()` - Emit only when different from previous
2. `Flow<E> diff(E initial)` - Diff with initial value
3. `Flow<E> forward(Pipe<? super E> pipe)` - Tee emissions to side channel
4. `Flow<E> guard(Predicate<? super E> predicate)` - Filter by predicate
5. `Flow<E> guard(E initial, BiPredicate<? super E, ? super E> predicate)` - Filter by comparison with previous
6. `Flow<E> limit(int limit)` - Pass at most N emissions
7. `Flow<E> limit(long limit)` - Pass at most N emissions (long)
8. `Flow<E> peek(Consumer<? super E> consumer)` - Inspect without modifying
9. `Flow<E> reduce(E initial, BinaryOperator<E> operator)` - Running accumulation
10. `Flow<E> replace(UnaryOperator<E> transformer)` - Transform each emission
11. `Flow<E> sample(int sample)` - Emit every Nth value
12. `Flow<E> sample(double sample)` - Probabilistic sampling
13. `Flow<E> sift(Comparator<? super E> comparator, Consumer<Sift<E>> configurer)` - Range/extrema filtering
14. `Flow<E> skip(long n)` - Skip first N emissions

**Note:** Flow is marked `@Temporal` - only valid during callback. Do NOT retain references.

### Sift<E> (Line 3908)

**Definition:**
```java
@Temporal
@Provided
interface Sift<E>
```

**Required Methods (ALL return Sift<E> for chaining):**

1. `Sift<E> above(E lower)` - Pass only values above bound (exclusive)
2. `Sift<E> below(E upper)` - Pass only values below bound (exclusive)
3. `Sift<E> high()` - Pass only new highs (maximum so far)
4. `Sift<E> low()` - Pass only new lows (minimum so far)
5. `Sift<E> max(E max)` - Pass only values at or below max (inclusive)
6. `Sift<E> min(E min)` - Pass only values at or above min (inclusive)
7. `Sift<E> range(E lower, E upper)` - Pass only values in range (inclusive)

**Note:** Sift is marked `@Temporal` - only valid during Flow#sift callback.

### Closure<R extends Resource> (Line 914)

**Definition:**
```java
@Utility
@Temporal
interface Closure<R extends Resource>
```

**Required Methods:**

1. `void consume(Consumer<? super R> consumer)` - Execute consumer with resource in ARM scope
   - Resource opened before consumer invoked
   - Resource closed in finally block after consumer returns/throws

### Registrar<E> (Line 3416)

**Definition:**
```java
@Temporal
interface Registrar<E>
```

**Required Methods:**

1. `void register(Pipe<? super E> pipe)` - Register pipe to receive emissions
   - Only valid during subscriber callback
   - Undefined behavior if called after callback returns

**Note:** Registrar is marked `@Temporal` - only valid during Subscriber callback.

### Lookup<P extends Percept> (Line 2778)

**Definition:**
```java
@Abstract
@Provided
interface Lookup<P extends Percept>
```

**Required Methods:**

1. `P percept(Name name)` - Get or create percept by name

**Methods with Defaults:**

2. `default P percept(Substrate<?> substrate)` - Get percept using substrate's subject name
3. `default P percept(Subject<?> subject)` - Get percept using subject's name

### Extent<S extends Extent<S, P>, P extends Extent<?, P>> (Line 1850)

**Definition:**
```java
@Abstract
@Extension
interface Extent<S extends Extent<S, P>, P extends Extent<?, P>>
  extends Iterable<P>, Comparable<P>
```

**Methods with Defaults (ALL have default implementations):**

- `default int compareTo(P other)` - Compare by path
- `default int depth()` - Return depth in hierarchy
- `default Optional<P> enclosure()` - Return parent (defaults to empty)
- `default void enclosure(Consumer<? super P> consumer)` - Apply consumer to parent if exists
- `default S extent()` - Return this
- `default P extremity()` - Return root of hierarchy
- `default <R> R fold(...)` - Fold right-to-left
- `default <R> R foldTo(...)` - Fold left-to-right
- `default Iterator<P> iterator()` - Iterate from this to root
- `CharSequence part()` - Return string for this extent (ABSTRACT - must override)
- `default CharSequence path()` - Return full path (uses '/' separator)
- `default CharSequence path(char separator)` - Full path with custom separator
- `default CharSequence path(String separator)` - Full path with string separator
- `default CharSequence path(Function, char)` - Mapped path with separator
- `default CharSequence path(Function, String)` - Mapped path with string separator
- `default Stream<P> stream()` - Stream from this to root
- `default boolean within(Extent<?, ?> enclosure)` - Check if enclosed within extent

**Required Override:**

- `CharSequence part()` - MUST be overridden by implementations

### State (Line 4296)

**Definition:**
```java
@Provided
interface State
  extends Iterable<Slot<?>>
```

**Required Methods:**

1. `State compact()` - Remove duplicate slots (same name+type), keep most recent
2. `State state(Name name, int value)` - Return state with int slot
3. `State state(Name name, long value)` - Return state with long slot
4. `State state(Name name, float value)` - Return state with float slot
5. `State state(Name name, double value)` - Return state with double slot
6. `State state(Name name, boolean value)` - Return state with boolean slot
7. `State state(Name name, String value)` - Return state with String slot
8. `State state(Name name, Name value)` - Return state with Name slot
9. `State state(Name name, State value)` - Return state with State slot
10. `State state(Slot<?> slot)` - Return state with specified slot
11. `State state(Enum<?> value)` - Return state with Name slot from enum
12. `Stream<Slot<?>> stream()` - Return stream of slots
13. `<T> T value(Slot<T> slot)` - Get value matching slot (or slot's default)
14. `<T> Stream<T> values(Slot<? extends T> slot)` - Stream all values matching slot

### Slot<T> (Line 4063)

**Definition:**
```java
@Utility
@Provided
interface Slot<T>
```

**Required Methods:**

1. `Name name()` - Return slot name
2. `Class<T> type()` - Return slot type (primitive class for primitives)
3. `T value()` - Return slot value (immutable)

### Subject<S extends Substrate<S>> (Line 4539)

**Definition:**
```java
@Identity
@Provided
interface Subject<S extends Substrate<S>>
  extends Extent<Subject<S>, Subject<?>>
```

**Required Methods:**

1. `Id id()` - Return unique identifier
2. `Name name()` - Return hierarchical name
3. `State state()` - Return associated state
4. `String toString()` - Return string representation (MUST override)
5. `Class<S> type()` - Return substrate class type

**Methods with Defaults:**

6. `default CharSequence part()` - Return formatted subject string

**Inherited from Extent:**
- All Extent methods with default implementations

### Name (Line 2899)

**Definition:**
```java
@Provided
interface Name
  extends Extent<Name, Name>
```

**Required Methods:**

NONE - All methods inherited from Extent have defaults, EXCEPT:

1. `CharSequence part()` - Must be overridden to return the name part (not full path)

**Note:** Names are interned - same path always returns same Name instance (compared by ==).

### Id (Line 2711)

**Definition:**
```java
@Provided
@Identity
interface Id
```

**Required Methods:**

NONE - This is a marker interface. Equality by reference (==), not equals().

### Percept (Line 3130)

**Definition:**
```java
interface Percept
```

**Required Methods:**

NONE - Marker interface for percept types created by Composers.

### Pipe<E> (Line 3221)

**Definition:**
```java
interface Pipe<E>
  extends Percept
```

**Required Methods:**

1. `void emit(E value)` - Emit value through pipe
   - Can be called from any thread
   - Value enqueued and processed on circuit thread

### Receptor<E> (Line 3311)

**Definition:**
```java
@FunctionalInterface
interface Receptor<E>
```

**Required Methods:**

1. `void receive(E value)` - Receive and process value

---

## SUMMARY: Implementation Checklist

To create a compliant Substrates implementation, you MUST provide concrete classes for:

### **Core Runtime (10 classes):**

1. **Circuit** - Event processing engine with async queue
   - Methods: `await()`, `cell()`, `conduit()`, `pipe()`, `close()`, `subscribe()`, `subject()`

2. **Conduit** - Percept factory with channel pooling
   - Methods: `percept(Name)`, `subscribe()`, `subject()`

3. **Channel** - Named emission port
   - Methods: `pipe()`, `pipe(Consumer<Flow>)`, `subject()`

4. **Cell** - Hierarchical computational unit
   - Methods: `emit()`, `percept(Name)`, `subscribe()`, `subject()`

5. **Cortex** - Root factory (30+ factory methods)
   - Methods: All creation methods + `subject()`

6. **Current** - Execution context reference
   - Methods: `subject()`

7. **Reservoir** - Emission capture buffer
   - Methods: `drain()`, `close()`, `subject()`

8. **Scope** - Resource lifecycle manager
   - Methods: `close()`, `closure()`, `register()`, `scope()`, `subject()`

9. **Subscriber** - Subscription callback wrapper
   - Methods: `close()`, `subject()`

10. **Subscription** - Cancellable subscription handle
    - Methods: `close()`, `subject()`

### **Value Types (10 classes):**

11. **Capture** - Emission + subject pair
12. **Flow** - Transformation pipeline (14 operators)
13. **Sift** - Range/extrema filtering (7 operators)
14. **Closure** - Block-scoped resource manager
15. **Registrar** - Pipe registration (temporal)
16. **State** - Immutable slot collection (13 methods)
17. **Slot** - Named typed value (3 methods)
18. **Subject** - Identity + name + state (5 methods + Extent)
19. **Name** - Hierarchical identifier (Extent + part())
20. **Id** - Unique reference (marker)

### **Functional Interfaces (2 classes):**

21. **Composer** - Channel → Percept transformation
22. **Receptor** - Value consumer (wrappable as Pipe)

---

## KEY DESIGN PATTERNS

### 1. **Sealed Hierarchy Pattern**
- Sealed interfaces (Source, Resource, Substrate) control which types can implement them
- Non-sealed extension points (Circuit, Conduit, Cell, etc.) allow implementations
- Applications use sealed types, implementations provide non-sealed concrete classes

### 2. **Subject Identity Pattern**
- Every substrate has a Subject with Id, Name, State, Type
- Enables hierarchical naming (Cortex → Circuit → Conduit → Channel)
- Identity by reference (==) for Id and Name (interning)

### 3. **Temporal Contract Pattern**
- Interfaces marked `@Temporal` (Channel, Current, Flow, Sift, Closure, Registrar)
- Valid ONLY during callback scope
- Do NOT retain references beyond callback
- Enables object pooling and mutable optimization without synchronization

### 4. **Lazy Subscription Pattern**
- Subscriber callbacks invoked lazily on first emission to channels
- Not invoked when channels created, only when first emission occurs
- Enables dynamic discovery without overhead until emissions happen

### 5. **Single-Threaded Circuit Pattern**
- Each circuit has exactly one processing thread (virtual thread)
- All emissions, flows, subscriber callbacks execute exclusively on that thread
- No synchronization needed for circuit-confined state
- Deterministic ordering guaranteed

### 6. **Async Dispatch Pattern**
- `Circuit.pipe(target)` breaks synchronous call chains via queue
- Enables cyclic topologies without stack overflow
- Supports arbitrarily deep hierarchies

### 7. **Builder/Fluent Pattern**
- Flow, Sift return `this` for method chaining
- Pipeline configuration builds up transformation stages
- Materialized into actual pipe chain when passed to components

### 8. **Factory Pattern**
- Cortex is root factory for all components
- Uses SPI loading (system property or ServiceLoader)
- Singleton accessed via `Substrates.cortex()`

---

## COMPLIANCE VERIFICATION

To verify 100% compliance with Substrates API:

1. **Run TCK**: 381 tests from Humainary Substrates TCK must pass
   ```bash
   cd substrates-api-java/tck
   mvn test -Dtck -Dtck.spi.groupId=io.fullerstack -Dtck.spi.artifactId=fullerstack-substrates -Dtck.spi.version=1.0.0-SNAPSHOT
   ```

2. **Check All Non-Sealed Interfaces Implemented:**
   - ✅ Circuit, Conduit, Channel, Cell
   - ✅ Cortex, Current
   - ✅ Reservoir, Scope
   - ✅ Subscriber, Subscription

3. **Verify Sealed Hierarchy Respected:**
   - Only permitted types implement Source, Resource, Substrate

4. **Validate Threading Model:**
   - Single virtual thread per circuit
   - All subscriber callbacks on circuit thread
   - Deterministic emission ordering

5. **Test Temporal Contracts:**
   - Channel only used in Composer callback
   - Current only used in originating thread
   - Flow/Sift only used in configurer callbacks
   - Registrar only used in subscriber callback
   - Closure only used in consume() call

6. **Verify Subject Hierarchy:**
   - Cortex at depth=1 (root)
   - Circuit at depth=2
   - Conduit at depth=3
   - Channel/Subscription at depth=4

---

**END OF ANALYSIS**
