---
name: java-performance
description: Java performance optimization for low-latency systems. Use when analyzing JMH benchmarks, optimizing hot paths, implementing lock-free algorithms, tuning JVM flags, or working with concurrent data structures. Triggers on performance issues, benchmark analysis, VarHandle/UNSAFE usage, memory barriers, false sharing, MPSC queues, or JCTools patterns.
---

# Java Performance Optimization

## Core Principles

1. **Measure first** - Use JMH for microbenchmarks; never guess where time goes
2. **Understand the hardware** - CPU cache lines (64 bytes), memory barriers, branch prediction
3. **Minimize allocations on hot paths** - But don't assume allocation is the problem without proof
4. **Lock-free when possible** - VarHandle atomics, CAS loops, intrusive data structures

## Quick Reference

### VarHandle Atomic Operations

```java
// Setup
private static final VarHandle FIELD;
static {
  FIELD = MethodHandles.lookup().findVarHandle(MyClass.class, "field", Type.class);
}

// Operations (strongest to weakest memory ordering)
FIELD.getAndSet(this, newVal);      // Full fence, atomic swap
FIELD.compareAndSet(this, exp, new); // CAS with full fence
FIELD.getVolatile(this);            // Acquire semantics
FIELD.setVolatile(this, val);       // Release semantics
FIELD.getOpaque(this);              // No reordering, no fence
FIELD.setRelease(this, val);        // Release only (weaker than volatile)
FIELD.getAcquire(this);             // Acquire only (weaker than volatile)
```

### False Sharing Prevention

```java
// Pad between producer and consumer fields (128 bytes for safety)
@SuppressWarnings("unused")
private long p0, p1, p2, p3, p4, p5, p6, p7;  // 64 bytes
@SuppressWarnings("unused")
private long p8, p9, p10, p11, p12, p13, p14; // 56 bytes (120 total)
```

### MPSC Queue Pattern (JCTools-style)

```java
// Producer: atomic swap + link
Node prev = (Node) HEAD.getAndSet(this, newNode);
prev.next = newNode;  // or use lazySet for weaker ordering

// Consumer: drain with spin-wait for visibility
Node node = head;
Node next = node.next;
if (next == null && node != tail) {
  // Spin: producer swapped but hasn't linked yet
}
```

## Detailed References

- **JMH benchmarking**: See [references/jmh-patterns.md](references/jmh-patterns.md) for benchmark setup, pitfalls, and interpretation
- **Low-latency patterns**: See [references/low-latency.md](references/low-latency.md) for lock-free algorithms, memory barriers, intrusive structures
- **JVM tuning**: See [references/jvm-tuning.md](references/jvm-tuning.md) for GC selection, flags, and profiling

## Common Pitfalls

| Pitfall | Solution |
|---------|----------|
| System.nanoTime() in hot path | Use JMH instead (~20-30ns overhead per call) |
| Separate volatile read+write | Use atomic getAndSet (single operation) |
| No padding between thread-local fields | Add 128 bytes padding |
| Blocking in callbacks | Use virtual threads or async |
| Reversing LIFO to FIFO on every drain | Consider true FIFO queue design |
