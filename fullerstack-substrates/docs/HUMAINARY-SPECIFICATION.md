# Humainary Substrates API Specification

**Source**: Extracted from `io.humainary.substrates.api.Substrates.java` (1.0.0-PREVIEW)
**Author**: William David Louth
**Purpose**: This document captures the specification as defined by Humainary, with no assumptions added.

---

## Core Overview

The Substrates API is Humainary's core runtime for deterministic emissions, adaptive control, and mirrored state coordination. It enables building **neural-like computational networks** where values flow through circuits, conduits, and channels in deterministic order.

**Performance Target**: ~2.98ns emission latency (≈336M ops/sec)

---

## Threading Model (Foundational)

**Single-threaded circuit execution** is the foundation:

- Every circuit owns exactly **one processing thread** (virtual thread)
- All emissions, flows, and subscriber callbacks execute **exclusively on that thread**
- **Deterministic ordering**: Emissions observed in the order they were enqueued
- **No synchronization needed**: State touched only from circuit thread requires no locks
- **Sequential execution**: Only one operation executes at a time per circuit

### Caller vs Circuit Thread

- **Caller threads** (your code): Enqueue emissions, return immediately
- **Circuit thread** (executor): Dequeue and process emissions sequentially
- **Performance principle**: Balance work between caller (before enqueue) and circuit (after dequeue). The circuit thread is the bottleneck.

---

## Core Guarantees

### Deterministic Ordering
- Emissions are observed in strict enqueue order
- Earlier emissions complete before later ones begin
- All subscribers see emissions in the same order

### Eventual Consistency
- Subscription changes (add/remove) use **lazy rebuild** with version tracking
- Channels detect changes on next emission (not immediately)
- No blocking or global coordination required
- Lock-free operation with minimal overhead

### State Isolation
- Flow operators maintain independent state per channel
- Subscriber state accessed only from circuit thread (no sync needed)
- No shared mutable state between circuits

---

## Cascading Emission Ordering (Dual Queue Model)

Circuits use **two queues** to manage emissions with deterministic priority ordering:

- **Ingress queue**: Shared queue for emissions from external threads (caller-side)
- **Transit queue**: Local queue for emissions originating from circuit thread during processing

### Key Characteristics
- **No call stack recursion**: All emissions enqueue rather than invoke nested calls
- **Stack safe**: Even deeply cascading chains don't overflow the stack
- **Priority processing**: Transit queue drains completely before returning to ingress queue
- **Causality preservation**: Cascading effects complete before processing next external input
- **Atomic computations**: Cascading chains appear atomic to external observers
- **Neural-like dynamics**: Enables proper signal propagation in feedback networks

When processing transit work that itself emits, those emissions are added to the **back of the transit queue** (not recursively invoked).

---

## Component Specifications

### Circuit

A computational network of conduits, cells, channels, and pipes.

**Subject Hierarchy**: Circuits have cortex subject as parent (depth=2).

**Execution Model**:
- Deterministic ordering: Emissions observed in enqueue order
- Circuit-thread confinement: State touched only from circuit thread needs no sync
- Bounded enqueue: Caller threads do not execute circuit work
- Sequential execution: Only one emission executes at a time per circuit
- Memory visibility: Circuit thread guarantees visibility of all state updates

**await()**: Blocks until circuit's queue is empty, establishing happens-before relationship with all previously enqueued emissions. Cannot be called from circuit thread (would deadlock). After circuit closure, returns immediately.

**close()**: Non-blocking - marks circuit for closure and returns immediately. Processing thread drains queue and terminates asynchronously.

---

### Conduit

A factory for pooled percepts that emit events through named channels.

**Combines**:
- **Lookup**: Creates and caches percept instances by name
- **Source**: Emits events and allows subscription to channel creation

**Lookup Semantics**:
- `percept(Name)` retrieves or creates percept for given name
- Same name always returns the same percept instance (cached)
- Different names create different percepts with separate channels
- **Identity guarantee**: Percepts with the same name are identical objects

**Lazy Subscriber Callbacks**:
1. First call to `percept(Name)` creates new channel (no callbacks invoked)
2. First emission to that channel triggers rebuild on circuit thread
3. During rebuild, subscriber callback is invoked for newly encountered channels
4. Subscriber receives Subject of the channel and Registrar to attach pipes
5. No events of type E are emitted - callback provides registration mechanism

**Threading**:
- **Caller thread** (synchronous): `percept(Name)` creates and caches channels immediately (thread-safe)
- **Circuit thread** (asynchronous):
  - Subscriber registration completes and increments version
  - Emissions trigger lazy rebuild when version mismatch detected
  - Rebuild invokes subscriber callbacks for newly encountered channels
  - Emissions dispatched to registered pipes

---

### Channel

A (subject) named port in a conduit that provides a pipe for emission.

**Temporal Contract**: Channels are only valid during the `Composer#compose(Channel)` callback. The channel reference **must not be retained** or used outside the composer callback. Instead, retain the pipe returned by `pipe()`.

**Pooling and Identity**: Channels are pooled by name within a conduit. **Channels with the same name are identical objects**.

**Thread Safety**: Channels can be accessed from any thread. The `pipe()` method returns a pipe that can be called from any thread. Emissions are enqueued to the owning circuit.

---

### Pipe

Emission carrier abstraction that routes typed values through flows.

**emit(E emission)**: Enqueues emission to circuit. Returns immediately. Circuit thread later processes the emission.

---

### Subscriber

Callback interface invoked lazily on first emission to channels.

**Key Points**:
- Dynamically attaches pipes to channels
- Enables adaptive topologies
- Callbacks always occur on circuit thread
- Created via `circuit.subscriber(name, biConsumer)`

**BiConsumer signature**: `(Subject<Channel<E>>, Registrar<E>) -> void`

---

### Registrar

Temporary handle for attaching pipes during subscriber callbacks.

**Temporal Contract**: **MUST be called only within the subscriber callback** that provided this registrar. Calling after the callback returns results in undefined behavior.

**register(Pipe)**: Registers a pipe to receive emissions from the channel.
- Pipe's emit() invoked on circuit thread
- Multiple pipes can be registered; all receive each emission in registration order

**register(Receptor)**: Convenience method for registering a receptor (lambda).

---

### Source

Event source managing subscription model. Connects subscribers to channels with lazy rebuild synchronization.

**subscribe(Subscriber)**:
- Subscribes a Subscriber to receive lazy callbacks during channel rebuild
- Registration is asynchronous on circuit thread
- Returns immediately with Subscription handle
- Use `circuit.await()` after subscribe to guarantee registration before proceeding

**Lazy Callback Model**:
- Subscriber callbacks invoked lazily during rebuild
- Rebuild occurs on first emission to a channel after subscription registered
- Channel creation (via `percept(Name)`) does NOT invoke callbacks
- Callbacks fire when channel receives its first emission

**Unsubscription**:
- Lazy unsubscription - channels detect on next emission
- Next emission triggers rebuild, removing unsubscribed pipes
- Provides "eventual consistency"

---

### Subscription

A cancellable handle representing an active subscription to a source.

**Lifecycle**:
1. **Active**: Created by subscribe(), subscriber receives callbacks
2. **Closed**: After close() is called, no more callbacks occur

**Cancellation Semantics** (via close()):
- Unregisters subscriber from source
- Stops all future subscriber callbacks
- Removes all pipes registered by this subscriber from active channels
- Is **idempotent** - repeated calls are safe

**Threading**: Can be closed from any thread. Unregistration happens asynchronously on circuit thread.

**Timing**: After close():
- Already-queued callbacks may still execute
- No NEW channels will trigger callbacks after unsubscription completes
- Pipes continue receiving emissions until rebuild
- Next emission on each channel triggers lazy rebuild, removing pipes

---

### Flow

Configurable processing pipeline for data transformation.

**Temporal Constraint**: Flow instances are temporal objects with **callback-scoped lifetime**. They must only be accessed during callback execution.

**Operators**: diff, guard, limit, sample, sift, reduce, replace, peek

**Execution**: Flow operations execute on circuit's worker thread, providing single-threaded execution guarantees. Stateful operators can use mutable state without synchronization.

---

## Data Flow Path

1. **Circuit** creates **Conduit**
2. **Conduit** creates **Channel** (lazily, on first access by name)
3. **Channel** exposes **Pipe** for emissions
4. **Subscriber** attaches **Pipe** to channel via **Registrar**
5. Emissions flow: `Pipe → [circuit thread] → Flow → Channel → Subscriber Pipes`

---

## Key Implementation Details from Specification

### Version-Based Lazy Rebuild

From Source.subscribe() documentation:
> "The current SPI rebuilds pipelines lazily on emission, invoking subscriber callbacks for newly encountered channels during the rebuild phase"

### Subscribe is Asynchronous

From Source.subscribe() documentation:
> "The subscribe call itself is **asynchronous** - it returns immediately with a Subscription handle, but the actual registration is submitted as a job to the circuit's processing thread"

### Percept Creation is Synchronous

From Conduit documentation:
> "Caller thread (synchronous): percept(Name) creates and caches channels immediately (thread-safe)"

### Lazy Unsubscription

From Subscription documentation:
> "Lazy unsubscription: Like subscription, unsubscription uses lazy rebuild. Channels detect the unsubscription on their next emission and rebuild their pipe lists to exclude the unsubscribed pipes."

---

## Design Philosophy

Three principles:
1. **Determinism over throughput**: Predictable ordering enables replay, testing, digital twins
2. **Composition over inheritance**: Small interfaces compose into complex behaviors
3. **Explicitness over magic**: No hidden frameworks, clear data flow, no reflection-based wiring

---

*This specification is extracted directly from Humainary's Substrates.java documentation. No assumptions have been added.*
