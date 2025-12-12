package io.fullerstack.substrates.disruptor;

import static java.util.Objects.requireNonNull;

import io.fullerstack.substrates.FsCell;
import io.fullerstack.substrates.FsCircuitPipe;
import io.fullerstack.substrates.FsConduit;
import io.fullerstack.substrates.FsException;
import io.fullerstack.substrates.FsFlow;
import io.fullerstack.substrates.FsInternalCircuit;
import io.fullerstack.substrates.FsReservoir;
import io.fullerstack.substrates.FsSubject;
import io.fullerstack.substrates.FsSubscriber;
import io.fullerstack.substrates.FsSubscription;
import io.humainary.substrates.api.Substrates.Cell;
import io.humainary.substrates.api.Substrates.Channel;
import io.humainary.substrates.api.Substrates.Circuit;
import io.humainary.substrates.api.Substrates.Composer;
import io.humainary.substrates.api.Substrates.Conduit;
import io.humainary.substrates.api.Substrates.Configurer;
import io.humainary.substrates.api.Substrates.Flow;
import io.humainary.substrates.api.Substrates.Name;
import io.humainary.substrates.api.Substrates.Percept;
import io.humainary.substrates.api.Substrates.Pipe;
import io.humainary.substrates.api.Substrates.Receptor;
import io.humainary.substrates.api.Substrates.Registrar;
import io.humainary.substrates.api.Substrates.Reservoir;
import io.humainary.substrates.api.Substrates.State;
import io.humainary.substrates.api.Substrates.Subject;
import io.humainary.substrates.api.Substrates.Subscriber;
import io.humainary.substrates.api.Substrates.Subscription;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * LMAX Disruptor-inspired Circuit implementation.
 *
 * <p>Key optimizations:
 *
 * <ul>
 *   <li>Multi-producer support: CAS on producer sequence for slot claiming
 *   <li>Pre-allocated ring buffer slots (zero allocation per emit)
 *   <li>Sequence barriers for coordination (producer sequence, consumer sequence)
 *   <li>Per-slot publish flags for correct multi-producer ordering
 *   <li>Busy-spin consumer with yield backoff
 *   <li>Cache-line padding to prevent false sharing
 * </ul>
 *
 * <p>Design:
 *
 * <ul>
 *   <li>Producers CAS to claim a slot, then write data, then publish via slot flag
 *   <li>Consumer polls slot flags and processes when slots are published
 *   <li>Slots reused after consumption with proper memory barriers
 * </ul>
 */
public final class FsDisruptorCircuit implements FsInternalCircuit {

  // Ring buffer size - must be power of 2
  private static final int RING_SIZE = 8192;
  private static final int RING_MASK = RING_SIZE - 1;

  // VarHandles for atomic operations
  private static final VarHandle CLAIM_SEQ;
  private static final VarHandle CONSUMER_SEQ;
  private static final VarHandle PUBLISHED;

  static {
    try {
      var lookup = MethodHandles.lookup();
      CLAIM_SEQ = lookup.findVarHandle(FsDisruptorCircuit.class, "claimSequence", long.class);
      CONSUMER_SEQ = lookup.findVarHandle(FsDisruptorCircuit.class, "consumerSequence", long.class);
      PUBLISHED = MethodHandles.arrayElementVarHandle(long[].class);
    } catch (Exception e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  // Cache-line padding to prevent false sharing (64 bytes = 8 longs)
  @SuppressWarnings("unused")
  private long p1, p2, p3, p4, p5, p6, p7;

  // Claim sequence - CAS by producers to claim a slot
  @SuppressWarnings("unused")
  private volatile long claimSequence = -1;

  @SuppressWarnings("unused")
  private long p8, p9, p10, p11, p12, p13, p14;

  // Consumer sequence - tracks last consumed slot
  @SuppressWarnings("unused")
  private volatile long consumerSequence = -1;

  @SuppressWarnings("unused")
  private long p15, p16, p17, p18, p19, p20, p21;

  // Ring buffer slots - pre-allocated
  private final Consumer<Object>[] consumers;
  private final Object[] values;

  // Per-slot publish flags: published[i] contains the sequence number when slot i is ready
  // A slot is ready when published[index] == sequence (the sequence that was claimed for that slot)
  private final long[] published;

  // Transit queue for cascading (circuit thread only - no sync needed)
  private static final int TRANSIT_SIZE = 256;
  private static final int TRANSIT_MASK = TRANSIT_SIZE - 1;
  private final Runnable[] transit = new Runnable[TRANSIT_SIZE];
  private int transitHead;
  private int transitTail;

  // Thread state
  private final Subject<Circuit> subject;
  private volatile Thread thread;
  private volatile boolean running = true;

  // Subscribers
  private final List<Subscriber<State>> subscribers = new ArrayList<>();

  @SuppressWarnings("unchecked")
  public FsDisruptorCircuit(Subject<Circuit> subject) {
    this.subject = subject;
    this.consumers = new Consumer[RING_SIZE];
    this.values = new Object[RING_SIZE];
    this.published = new long[RING_SIZE];

    // Initialize published array: -1 means slot is available for sequence 0
    // After consuming sequence N at index i, we set published[i] = N - RING_SIZE
    // so the next producer claiming sequence N+RING_SIZE can use it
    for (int i = 0; i < RING_SIZE; i++) {
      published[i] = -1;
    }

    this.thread = Thread.ofVirtual().name("disruptor-" + subject.name()).start(this::loop);
  }

  // === HOT PATH: emit ===

  @SuppressWarnings("unchecked")
  private <E> void emitInternal(Consumer<E> consumer, E value) {
    if (Thread.currentThread() == thread) {
      // On circuit thread: execute inline (fastest path)
      consumer.accept(value);
    } else {
      // Multi-producer: CAS to claim a slot
      long claimed;
      int index;

      // Step 1: Claim a slot via CAS
      while (true) {
        claimed = (long) CLAIM_SEQ.getVolatile(this);
        long next = claimed + 1;
        index = (int) (next & RING_MASK);

        // Wait for slot to be available (consumer has processed it)
        // Slot is available when published[index] < next - RING_SIZE + 1
        // i.e., the previous use of this slot has been consumed
        long wrapPoint = next - RING_SIZE;
        while ((long) PUBLISHED.getVolatile(published, index) > wrapPoint) {
          // Buffer full at this slot - wait for consumer
          Thread.onSpinWait();
        }

        // Also check consumer sequence to ensure we don't overwrite unconsumed data
        long consumerSeq = (long) CONSUMER_SEQ.getAcquire(this);
        if (consumerSeq < wrapPoint) {
          Thread.onSpinWait();
          continue;
        }

        // Try to claim this slot
        if (CLAIM_SEQ.compareAndSet(this, claimed, next)) {
          claimed = next;
          break;
        }
        // CAS failed - another producer claimed it, retry
      }

      // Step 2: Write to our claimed slot (we have exclusive access now)
      consumers[index] = (Consumer<Object>) consumer;
      values[index] = value;

      // Step 3: Publish - mark slot as ready by setting published[index] = claimed
      // This allows consumer to process it
      PUBLISHED.setRelease(published, index, claimed);

      // Signal consumer
      LockSupport.unpark(thread);
    }
  }

  @Override
  public <E> void emit(FsCircuitPipe<E> pipe, Object value) {
    if (Thread.currentThread() == thread) {
      // On circuit thread: execute inline (fastest path)
      pipe.deliver(value);
    } else {
      // Multi-producer: CAS to claim a slot
      long claimed;
      int index;

      // Step 1: Claim a slot via CAS
      while (true) {
        claimed = (long) CLAIM_SEQ.getVolatile(this);
        long next = claimed + 1;
        index = (int) (next & RING_MASK);

        // Wait for slot to be available
        long wrapPoint = next - RING_SIZE;
        while ((long) PUBLISHED.getVolatile(published, index) > wrapPoint) {
          Thread.onSpinWait();
        }

        // Check consumer sequence
        long consumerSeq = (long) CONSUMER_SEQ.getAcquire(this);
        if (consumerSeq < wrapPoint) {
          Thread.onSpinWait();
          continue;
        }

        // Try to claim this slot
        if (CLAIM_SEQ.compareAndSet(this, claimed, next)) {
          claimed = next;
          break;
        }
      }

      // Step 2: Write to claimed slot - store a consumer that delivers to the pipe
      consumers[index] = (Consumer<Object>) v -> pipe.deliver(v);
      values[index] = value;

      // Step 3: Publish
      PUBLISHED.setRelease(published, index, claimed);
      LockSupport.unpark(thread);
    }
  }

  // Transit for cascading (circuit thread only)
  void cascade(Runnable task) {
    transit[transitTail++ & TRANSIT_MASK] = task;
  }

  @Override
  public void submit(Runnable task) {
    if (Thread.currentThread() == thread) {
      cascade(task);
    } else {
      emitInternal(r -> task.run(), null);
    }
  }

  @Override
  public boolean isRunning() {
    return running;
  }

  // === Consumer loop ===

  private void loop() {
    long nextSequence = 0;
    int spins = 0;

    while (running || nextSequence <= (long) CLAIM_SEQ.getVolatile(this) || transitHead != transitTail) {
      boolean worked = false;

      // 1. Transit first (cascades have priority)
      while (transitHead != transitTail) {
        Runnable r = transit[transitHead & TRANSIT_MASK];
        transit[transitHead++ & TRANSIT_MASK] = null;
        try {
          r.run();
        } catch (Exception ignored) {
        }
        worked = true;
      }

      // 2. Process ring buffer - check if next slot is published
      int index = (int) (nextSequence & RING_MASK);
      long publishedSeq = (long) PUBLISHED.getAcquire(published, index);

      if (publishedSeq == nextSequence) {
        // Slot is published and ready to consume
        Consumer<Object> consumer = consumers[index];
        Object value = values[index];

        // Clear slot for GC
        consumers[index] = null;
        values[index] = null;

        try {
          consumer.accept(value);
        } catch (Exception ignored) {
        }

        // Drain any cascades this triggered
        while (transitHead != transitTail) {
          Runnable r = transit[transitHead & TRANSIT_MASK];
          transit[transitHead++ & TRANSIT_MASK] = null;
          try {
            r.run();
          } catch (Exception ignored) {
          }
        }

        // Mark slot as consumed by setting published to a value that allows
        // the next producer (at sequence nextSequence + RING_SIZE) to use it
        // We set it to nextSequence - RING_SIZE so the check in emit() passes
        PUBLISHED.setRelease(published, index, nextSequence - RING_SIZE);

        // Update consumer sequence
        CONSUMER_SEQ.setRelease(this, nextSequence);
        nextSequence++;
        worked = true;
      }

      // 3. Backoff strategy: spin → park
      if (worked) {
        spins = 0;
      } else if (++spins < 1000) {
        Thread.onSpinWait();
      } else {
        spins = 0;
        // Double-check before parking
        long claimed = (long) CLAIM_SEQ.getVolatile(this);
        if (nextSequence > claimed && transitHead == transitTail && running) {
          LockSupport.park();
        }
      }
    }
  }

  // === Circuit interface ===

  @Override
  public Subject<Circuit> subject() {
    return subject;
  }

  @Override
  public void await() {
    if (Thread.currentThread() == thread) {
      throw new IllegalStateException("Cannot await from circuit thread");
    }
    // Wait until consumer has caught up to all claimed sequences
    while ((long) CONSUMER_SEQ.getVolatile(this) < (long) CLAIM_SEQ.getVolatile(this) || transitHead != transitTail) {
      Thread.onSpinWait();
    }
  }

  @Override
  public void close() {
    running = false;
    LockSupport.unpark(thread);
    try {
      thread.join();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  // === Subject factory for pipes ===

  @Override
  public <E> Subject<Pipe<E>> createPipeSubject(Name name) {
    return new FsSubject<>(name, (FsSubject<?>) subject, Pipe.class);
  }

  // === Cell ===

  @Override
  public <I, E> Cell<I, E> cell(
      Name name,
      Composer<E, Pipe<I>> ingress,
      Composer<E, Pipe<E>> egress,
      Receptor<? super E> receptor) {
    requireNonNull(name, "name must not be null");
    requireNonNull(ingress, "ingress must not be null");
    requireNonNull(egress, "egress must not be null");
    requireNonNull(receptor, "receptor must not be null");
    FsSubject<Cell<I, E>> cellSubject = new FsSubject<>(name, (FsSubject<?>) subject, Cell.class);
    return new FsCell<>(cellSubject, this, ingress, egress, receptor);
  }

  // === Conduit ===

  @Override
  public <P extends Percept, E> Conduit<P, E> conduit(Name name, Composer<E, ? extends P> composer) {
    requireNonNull(name, "name must not be null");
    requireNonNull(composer, "composer must not be null");
    FsSubject<Conduit<P, E>> conduitSubject =
        new FsSubject<>(name, (FsSubject<?>) subject, Conduit.class);
    return new FsConduit<>(conduitSubject, channel -> composer.compose(channel), this);
  }

  @Override
  public <P extends Percept, E> Conduit<P, E> conduit(
      Name name, Composer<E, ? extends P> composer, Configurer<Flow<E>> configurer) {
    requireNonNull(name, "name must not be null");
    requireNonNull(composer, "composer must not be null");
    requireNonNull(configurer, "configurer must not be null");
    FsSubject<Conduit<P, E>> conduitSubject =
        new FsSubject<>(name, (FsSubject<?>) subject, Conduit.class);
    return new FsConduit<>(conduitSubject, channel -> composer.compose(channel), this, configurer);
  }

  // === Pipe methods ===

  @Override
  public <E> Pipe<E> pipe(Pipe<E> target) {
    requireNonNull(target, "target must not be null");
    return new FsCircuitPipe<>(this, target::emit, null);
  }

  @Override
  public <E> Pipe<E> pipe(Receptor<E> receptor) {
    requireNonNull(receptor, "receptor must not be null");
    return new FsCircuitPipe<>(this, receptor::receive, null);
  }

  @Override
  public <E> Pipe<E> pipe(Pipe<E> target, Configurer<Flow<E>> configurer) {
    requireNonNull(target, "target must not be null");
    requireNonNull(configurer, "configurer must not be null");
    Subject<Pipe<E>> pipeSubject = createPipeSubject(null);
    FsCircuitPipe<E> asyncPipe = new FsCircuitPipe<>(this, target::emit, null);
    FsFlow<E> flow = new FsFlow<>(pipeSubject, asyncPipe);
    configurer.configure(flow);
    return flow.pipe();
  }

  @Override
  public <E> Pipe<E> pipe(Receptor<E> receptor, Configurer<Flow<E>> configurer) {
    requireNonNull(receptor, "receptor must not be null");
    requireNonNull(configurer, "configurer must not be null");
    Subject<Pipe<E>> pipeSubject = createPipeSubject(null);
    FsCircuitPipe<E> asyncPipe = new FsCircuitPipe<>(this, receptor::receive, null);
    FsFlow<E> flow = new FsFlow<>(pipeSubject, asyncPipe);
    configurer.configure(flow);
    return flow.pipe();
  }

  @Override
  public <E> Pipe<E> pipe(Name name, Pipe<E> target) {
    requireNonNull(name, "name must not be null");
    requireNonNull(target, "target must not be null");
    return new FsCircuitPipe<>(this, target::emit, name);
  }

  @Override
  public <E> Pipe<E> pipe(Name name, Receptor<E> receptor) {
    requireNonNull(name, "name must not be null");
    requireNonNull(receptor, "receptor must not be null");
    return new FsCircuitPipe<>(this, receptor::receive, name);
  }

  @Override
  public <E> Pipe<E> pipe(Name name, Pipe<E> target, Configurer<Flow<E>> configurer) {
    requireNonNull(name, "name must not be null");
    requireNonNull(target, "target must not be null");
    requireNonNull(configurer, "configurer must not be null");
    Subject<Pipe<E>> pipeSubject = createPipeSubject(name);
    FsCircuitPipe<E> asyncPipe = new FsCircuitPipe<>(this, target::emit, name);
    FsFlow<E> flow = new FsFlow<>(pipeSubject, asyncPipe);
    configurer.configure(flow);
    return flow.pipe();
  }

  @Override
  public <E> Pipe<E> pipe(Name name, Receptor<E> receptor, Configurer<Flow<E>> configurer) {
    requireNonNull(name, "name must not be null");
    requireNonNull(receptor, "receptor must not be null");
    requireNonNull(configurer, "configurer must not be null");
    Subject<Pipe<E>> pipeSubject = createPipeSubject(name);
    FsCircuitPipe<E> asyncPipe = new FsCircuitPipe<>(this, receptor::receive, name);
    FsFlow<E> flow = new FsFlow<>(pipeSubject, asyncPipe);
    configurer.configure(flow);
    return flow.pipe();
  }

  // === Subscriber ===

  @Override
  public <E> Subscriber<E> subscriber(
      Name name, BiConsumer<Subject<Channel<E>>, Registrar<E>> callback) {
    requireNonNull(name, "name must not be null");
    requireNonNull(callback, "callback must not be null");
    FsSubject<Subscriber<E>> subSubject =
        new FsSubject<>(name, (FsSubject<?>) subject, Subscriber.class);
    return new FsSubscriber<>(subSubject, callback);
  }

  @Override
  @SuppressWarnings("unchecked")
  public Subscription subscribe(Subscriber<State> subscriber) {
    if (subscriber.subject() instanceof FsSubject<?> subSubject) {
      FsSubject<?> subscriberCircuit = subSubject.findCircuitAncestor();
      if (subscriberCircuit != null && subscriberCircuit != subject) {
        throw new FsException("Subscriber belongs to a different circuit");
      }
    }
    subscribers.add(subscriber);
    Subject<Subscription> subSubject = (Subject<Subscription>) (Subject<?>) subject;
    return new FsSubscription(subSubject, () -> subscribers.remove(subscriber));
  }

  @Override
  @SuppressWarnings("unchecked")
  public Reservoir<State> reservoir() {
    Subject<Reservoir<State>> resSubject = (Subject<Reservoir<State>>) (Subject<?>) subject;
    return new FsReservoir<>(resSubject);
  }
}
