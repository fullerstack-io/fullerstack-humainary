package io.fullerstack.substrates.ring;

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
 * Zero-allocation ring buffer Circuit implementation.
 *
 * <p>Key optimization: Pre-allocated ring buffer slots eliminate per-emit allocation. Each emit
 * only needs one CAS to claim a slot, then a plain store.
 *
 * <p>Design:
 *
 * <ul>
 *   <li>Ring buffer with power-of-2 size (4096 slots)
 *   <li>Producer claims slot via CAS on tail index
 *   <li>Consumer waits for slot to be filled (sequence matches)
 *   <li>Slots reused after consumption
 * </ul>
 */
public final class FsRingCircuit implements FsInternalCircuit {

  // Ring buffer size - must be power of 2
  private static final int RING_SIZE = 4096;
  private static final int RING_MASK = RING_SIZE - 1;

  // VarHandles for atomic operations
  private static final VarHandle TAIL;
  private static final VarHandle SEQUENCE;

  static {
    try {
      var lookup = MethodHandles.lookup();
      TAIL = lookup.findVarHandle(FsRingCircuit.class, "tail", long.class);
      SEQUENCE = MethodHandles.arrayElementVarHandle(long[].class);
    } catch (Exception e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  // Ring buffer slots
  private final Consumer<Object>[] consumers;
  private final Object[] values;
  private final long[] sequences;

  // Producer tail (claimed slot)
  @SuppressWarnings("unused")
  private volatile long tail = 0;

  // Consumer head (processed slot)
  private long head = 0;

  // Transit queue for cascading (circuit thread only)
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
  public FsRingCircuit(Subject<Circuit> subject) {
    this.subject = subject;
    this.consumers = new Consumer[RING_SIZE];
    this.values = new Object[RING_SIZE];
    this.sequences = new long[RING_SIZE];

    // Initialize sequences: slot i is ready for sequence i
    for (int i = 0; i < RING_SIZE; i++) {
      sequences[i] = i;
    }

    this.thread = Thread.ofVirtual().name("ring-" + subject.name()).start(this::loop);
  }

  // === HOT PATH: emit ===

  @SuppressWarnings("unchecked")
  private <E> void emitInternal(Consumer<E> consumer, E value) {
    if (Thread.currentThread() == thread) {
      // On circuit thread: execute inline
      consumer.accept(value);
    } else {
      // Claim a slot via CAS on tail
      long claimed;
      int index;
      long expectedSeq;

      while (true) {
        claimed = (long) TAIL.getVolatile(this);
        index = (int) (claimed & RING_MASK);
        expectedSeq = claimed;

        // Check if slot is available (sequence matches expected)
        long seq = (long) SEQUENCE.getVolatile(sequences, index);
        if (seq == expectedSeq) {
          // Try to claim this slot
          if (TAIL.compareAndSet(this, claimed, claimed + 1)) {
            break;
          }
        } else if (seq < expectedSeq) {
          // Buffer full - spin wait
          Thread.onSpinWait();
        }
        // seq > expectedSeq means slot already claimed, retry
      }

      // Write to claimed slot
      consumers[index] = (Consumer<Object>) consumer;
      values[index] = value;

      // Publish: set sequence to indicate slot is filled
      SEQUENCE.setRelease(sequences, index, claimed + 1);

      // Signal consumer
      LockSupport.unpark(thread);
    }
  }

  @Override
  public <E> void emit(FsCircuitPipe<E> pipe, Object value) {
    if (Thread.currentThread() == thread) {
      // On circuit thread: execute inline
      pipe.deliver(value);
    } else {
      // Claim a slot via CAS on tail
      long claimed;
      int index;
      long expectedSeq;

      while (true) {
        claimed = (long) TAIL.getVolatile(this);
        index = (int) (claimed & RING_MASK);
        expectedSeq = claimed;

        // Check if slot is available (sequence matches expected)
        long seq = (long) SEQUENCE.getVolatile(sequences, index);
        if (seq == expectedSeq) {
          // Try to claim this slot
          if (TAIL.compareAndSet(this, claimed, claimed + 1)) {
            break;
          }
        } else if (seq < expectedSeq) {
          // Buffer full - spin wait
          Thread.onSpinWait();
        }
      }

      // Write to claimed slot - store a consumer that delivers to the pipe
      consumers[index] = (Consumer<Object>) v -> pipe.deliver(v);
      values[index] = value;

      // Publish: set sequence to indicate slot is filled
      SEQUENCE.setRelease(sequences, index, claimed + 1);

      // Signal consumer
      LockSupport.unpark(thread);
    }
  }

  // Transit for cascading
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
    int spins = 0;
    while (running || head != (long) TAIL.getVolatile(this) || transitHead != transitTail) {
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

      // 2. Process ring buffer slots
      int index = (int) (head & RING_MASK);
      long expectedSeq = head + 1; // Slot filled when sequence = claimed + 1

      long seq = (long) SEQUENCE.getVolatile(sequences, index);
      if (seq == expectedSeq) {
        // Slot is filled - process it
        Consumer<Object> consumer = consumers[index];
        Object value = values[index];

        // Clear slot for GC
        consumers[index] = null;
        values[index] = null;

        try {
          consumer.accept(value);
        } catch (Exception ignored) {
        }

        // Drain any cascades
        while (transitHead != transitTail) {
          Runnable r = transit[transitHead & TRANSIT_MASK];
          transit[transitHead++ & TRANSIT_MASK] = null;
          try {
            r.run();
          } catch (Exception ignored) {
          }
        }

        // Release slot: set sequence to next expected value for this slot
        SEQUENCE.setRelease(sequences, index, head + RING_SIZE);
        head++;
        worked = true;
      }

      // 3. Spin then park
      if (worked) {
        spins = 0;
      } else if (++spins < 1000) {
        Thread.onSpinWait();
      } else {
        spins = 0;
        if (head == (long) TAIL.getVolatile(this) && transitHead == transitTail && running) {
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
    while (head != (long) TAIL.getVolatile(this) || transitHead != transitTail) {
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
