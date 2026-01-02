# Low-Latency Java Patterns

## Table of Contents
- [Memory Ordering](#memory-ordering)
- [Lock-Free Data Structures](#lock-free-data-structures)
- [Intrusive Collections](#intrusive-collections)
- [Thread Coordination](#thread-coordination)
- [JCTools Patterns](#jctools-patterns)

## Memory Ordering

### Java Memory Model Basics
```
Program Order → Happens-Before → Synchronization Order

Volatile write  ──────────────→  Volatile read
     │                                │
 (release)                        (acquire)
     │                                │
 All prior writes visible ───────→ To this thread
```

### VarHandle Access Modes

| Mode | Ordering | Use Case |
|------|----------|----------|
| `getVolatile/setVolatile` | Sequential consistency | Default safe choice |
| `getAcquire/setRelease` | Acquire-release | Producer-consumer |
| `getOpaque/setOpaque` | No tearing, no ordering | Statistics counters |
| `get/set` (plain) | No guarantees | Single-threaded only |

### When to Use Each
```java
// Sequential consistency (strongest, slowest)
FIELD.setVolatile(this, value);
Object v = FIELD.getVolatile(this);

// Acquire-release (sufficient for most lock-free)
FIELD.setRelease(this, value);  // All prior writes visible
Object v = FIELD.getAcquire(this);  // See all prior releases

// Opaque (no reordering with same-variable accesses)
COUNTER.setOpaque(this, count + 1);  // Stats counter
```

## Lock-Free Data Structures

### CAS Loop Pattern
```java
public boolean compareAndSwap(int expected, int newValue) {
  while (true) {
    int current = VALUE.getVolatile(this);
    if (current != expected) return false;
    if (VALUE.compareAndSet(this, current, newValue)) {
      return true;
    }
    // CAS failed, retry
  }
}
```

### Atomic Increment
```java
// VarHandle (Java 9+)
public long increment() {
  return (long) COUNTER.getAndAdd(this, 1L) + 1L;
}

// AtomicLong (simpler API)
private final AtomicLong counter = new AtomicLong();
public long increment() {
  return counter.incrementAndGet();
}
```

## Intrusive Collections

### Why Intrusive?
- **No wrapper allocation**: Node IS the element
- **Cache friendly**: Element and link in same cache line
- **GC friendly**: Fewer objects, simpler reachability

### Intrusive Linked List Node
```java
abstract class Node {
  Node next;  // Intrusive link
  abstract void process();
}

class MyTask extends Node {
  private final Runnable work;

  MyTask(Runnable work) { this.work = work; }

  @Override void process() { work.run(); }
}
```

### Intrusive MPSC Queue
```java
class MpscQueue {
  private static final VarHandle HEAD;
  private volatile Node head = new SentinelNode();

  // Producer: O(1), wait-free
  void offer(Node node) {
    node.next = null;
    Node prev = (Node) HEAD.getAndSet(this, node);
    prev.next = node;  // Link after swap
  }

  // Consumer: O(n), single-threaded
  Node poll() {
    Node h = head;
    Node next = h.next;
    if (next == null) return null;
    head = next;
    return (h instanceof SentinelNode) ? poll() : h;
  }
}
```

## Thread Coordination

### Park/Unpark Pattern
```java
private volatile boolean parked = false;
private final Thread worker;

// Producer
void submit(Job job) {
  queue.offer(job);
  if (parked) {
    LockSupport.unpark(worker);
  }
}

// Consumer loop
void loop() {
  int spins = 0;
  while (running) {
    Job job = queue.poll();
    if (job != null) {
      job.run();
      spins = 0;
    } else if (spins++ < SPIN_LIMIT) {
      Thread.onSpinWait();
    } else {
      parked = true;
      if (queue.isEmpty()) {
        LockSupport.park();
      }
      parked = false;
      spins = 0;
    }
  }
}
```

### Lost Wakeup Prevention
```java
// WRONG: Race between isEmpty check and park
if (queue.isEmpty()) {
  LockSupport.park();  // Producer may have added item!
}

// RIGHT: Set flag, recheck, then park
parked = true;
if (queue.isEmpty()) {  // Recheck after flag
  LockSupport.park();
}
parked = false;
```

## JCTools Patterns

### Naming Conventions
- `Mpsc` = Multi-Producer Single-Consumer
- `Spsc` = Single-Producer Single-Consumer
- `Mpmc` = Multi-Producer Multi-Consumer

### Key Methods
```java
// Store-ordered (release semantics, weaker than volatile)
UNSAFE.putOrderedObject(this, offset, value);  // lazySet equivalent
AtomicReference.lazySet(value);

// Load-acquire
UNSAFE.getObjectVolatile(this, offset);

// Atomic swap
UNSAFE.getAndSetObject(this, offset, newValue);
```

### Padding for False Sharing
```java
// JCTools style: inheritance chain with padding
abstract class Pad0 {
  byte b000,b001,b002,...,b077;  // 64 bytes
}
abstract class ProducerFields extends Pad0 {
  volatile Node producerNode;
}
abstract class Pad1 extends ProducerFields {
  byte b000,b001,b002,...,b077;  // 64 bytes
}
abstract class ConsumerFields extends Pad1 {
  Node consumerNode;
}
public class Queue extends ConsumerFields {
  // Producer and consumer fields now 128+ bytes apart
}
```

### Contended Annotation (Alternative)
```java
@jdk.internal.vm.annotation.Contended
private volatile long producerIndex;

// Requires: --add-opens java.base/jdk.internal.vm.annotation=ALL-UNNAMED
// And: -XX:-RestrictContended
```
