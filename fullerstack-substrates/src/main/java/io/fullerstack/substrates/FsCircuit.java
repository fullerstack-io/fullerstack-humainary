package io.fullerstack.substrates;

import static java.util.Objects.requireNonNull;

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

/// Simple ring-buffer based Circuit implementation.
///
/// - Zero allocation: pre-allocated ring buffer slots
/// - MPSC: CAS to claim slot, single consumer
/// - Transit priority: circuit-thread cascades use simple array (no sync)
public final class FsCircuit implements FsInternalCircuit {

  // === Ring buffer for zero-allocation MPSC ===
  private static final int RING_SIZE = 4096;
  private static final int RING_MASK = RING_SIZE - 1;

  private static final VarHandle TAIL;
  private static final VarHandle SEQUENCE;

  static {
    try {
      var lookup = MethodHandles.lookup();
      TAIL = lookup.findVarHandle(FsCircuit.class, "tail", long.class);
      SEQUENCE = MethodHandles.arrayElementVarHandle(long[].class);
    } catch (Exception e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  // Pre-allocated slots
  private final FsCircuitPipe<?>[] pipes = new FsCircuitPipe<?>[RING_SIZE];
  private final Object[] values = new Object[RING_SIZE];
  private final long[] sequences = new long[RING_SIZE]; // For multi-producer coordination

  @SuppressWarnings("unused")
  private volatile long tail = 0; // Producer claims via CAS
  private long head = 0; // Consumer reads (single thread)

  // === Transit: simple array ring (circuit thread only - no sync needed) ===
  private static final int TRANSIT_SIZE = 256;
  private static final int TRANSIT_MASK = TRANSIT_SIZE - 1;
  private final Runnable[] transit = new Runnable[TRANSIT_SIZE];
  private int transitHead;
  private int transitTail;

  // === Thread state ===
  private final Subject<Circuit> subject;
  private volatile Thread thread;
  private volatile boolean running = true;

  // === Subscribers ===
  private final List<Subscriber<State>> subscribers = new ArrayList<>();

  public FsCircuit(Subject<Circuit> subject) {
    this.subject = subject;
    // Initialize sequences: slot i is ready for sequence i
    for (int i = 0; i < RING_SIZE; i++) {
      sequences[i] = i;
    }
    this.thread = Thread.ofVirtual().name("circuit-" + subject.name()).start(this::loop);
  }

  // === HOT PATH: emit ===

  @Override
  public <E> void emit(FsCircuitPipe<E> pipe, Object value) {
    if (Thread.currentThread() == thread) {
      // On circuit thread: execute inline (fastest path)
      pipe.deliver(value);
    } else {
      // External thread: claim slot via CAS
      long claimed;
      int index;

      while (true) {
        claimed = (long) TAIL.getVolatile(this);
        index = (int) (claimed & RING_MASK);
        long seq = (long) SEQUENCE.getVolatile(sequences, index);

        if (seq == claimed) {
          // Slot available, try to claim
          if (TAIL.compareAndSet(this, claimed, claimed + 1)) {
            break;
          }
        } else if (seq < claimed) {
          // Buffer full - spin wait for consumer
          Thread.onSpinWait();
        }
        // seq > claimed means another producer claimed it, retry
      }

      // Write to claimed slot
      pipes[index] = pipe;
      values[index] = value;

      // Publish: mark slot as filled
      SEQUENCE.setRelease(sequences, index, claimed + 1);

      // Signal consumer
      LockSupport.unpark(thread);
    }
  }

  @SuppressWarnings("unchecked")
  <E> void emitInternal(Consumer<E> consumer, E value) {
    if (Thread.currentThread() == thread) {
      consumer.accept(value);
    } else {
      // For submit(), wrap in a pipe-like delivery
      // This path is less optimized but rarely used
      submit(() -> consumer.accept(value));
    }
  }

  // === Transit: for cascading emissions (circuit thread only) ===

  void cascade(Runnable task) {
    transit[transitTail++ & TRANSIT_MASK] = task;
  }

  // === Public submit (routes to emit or cascade) ===

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

  // === CONSUMER LOOP ===

  private void loop() {
    int spins = 0;
    while (running || head < (long) TAIL.getVolatile(this) || transitHead != transitTail) {
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

      // 2. Consume from ring buffer
      long currentTail = (long) TAIL.getVolatile(this);
      while (head < currentTail) {
        int index = (int) (head & RING_MASK);
        long seq = (long) SEQUENCE.getVolatile(sequences, index);

        // Wait for slot to be published (seq == head + 1)
        if (seq != head + 1) {
          Thread.onSpinWait();
          continue;
        }

        // Read from slot
        FsCircuitPipe<?> pipe = pipes[index];
        Object value = values[index];

        // Clear slot
        pipes[index] = null;
        values[index] = null;

        // Mark slot as available for next round (head + RING_SIZE)
        SEQUENCE.setRelease(sequences, index, head + RING_SIZE);
        head++;

        // Deliver
        try {
          pipe.deliver(value);
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
        worked = true;
      }

      // 3. Spin then park
      if (worked) {
        spins = 0;
      } else if (++spins < 1000) {
        Thread.onSpinWait();
      } else {
        spins = 0;
        LockSupport.park();
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
    // Wait for ring buffer to drain and transit queue to empty
    while (head < (long) TAIL.getVolatile(this) || transitHead != transitTail) {
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
  public <E> Subject<Pipe<E>> createPipeSubject(Name name) {
    return new FsSubject<>(name, (FsSubject<?>) subject, Pipe.class);
  }

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
