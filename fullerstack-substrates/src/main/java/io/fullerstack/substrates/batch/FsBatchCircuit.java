package io.fullerstack.substrates.batch;

import static java.util.Objects.requireNonNull;

import io.fullerstack.substrates.FsCircuitPipe;
import io.fullerstack.substrates.FsConduit;
import io.fullerstack.substrates.FsException;
import io.fullerstack.substrates.FsFlow;
import io.fullerstack.substrates.FsInternalCircuit;
import io.fullerstack.substrates.FsRegistrar;
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

/**
 * Circuit implementation with thread-local batching.
 *
 * <p>Key design:
 *
 * <ul>
 *   <li>Thread-local batch buffers - accumulate emissions locally before flush
 *   <li>Batch flush on threshold (default 64) or explicit flush
 *   <li>Single bulk transfer to circuit thread per batch
 *   <li>Amortizes synchronization cost across many emissions
 * </ul>
 *
 * <p>Architecture:
 *
 * <ul>
 *   <li>MPSC ingress list - receives batch transfers from worker threads
 *   <li>Transit ring buffer - handles circuit-thread cascades (priority)
 *   <li>Thread-local batch buffers - one per worker thread, independent
 *   <li>Virtual thread processor - single-threaded processing for determinism
 * </ul>
 */
public final class FsBatchCircuit implements FsInternalCircuit {

  // === Batch configuration ===
  private static final int DEFAULT_BATCH_SIZE = 64;

  // === Thread-local batch buffer ===
  static final class Batch {
    final FsCircuitPipe<?>[] pipes = new FsCircuitPipe<?>[DEFAULT_BATCH_SIZE];
    final Object[] values = new Object[DEFAULT_BATCH_SIZE];
    int count = 0;
  }

  private final ThreadLocal<Batch> threadBatch = ThreadLocal.withInitial(Batch::new);

  // === Ingress: MPSC lock-free linked list ===
  private static final VarHandle HEAD;

  static {
    try {
      HEAD = MethodHandles.lookup().findVarHandle(FsBatchCircuit.class, "ingressHead", Node.class);
    } catch (Exception e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  static final class Node {
    Batch batch;
    Node next;
  }

  private volatile Node ingressHead;

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

  public FsBatchCircuit(Subject<Circuit> subject) {
    this.subject = subject;
    this.thread = Thread.ofVirtual().name("batch-" + subject.name()).start(this::loop);
  }

  // === HOT PATH: emit ===

  @Override
  public <E> void emit(FsCircuitPipe<E> pipe, Object value) {
    if (Thread.currentThread() == thread) {
      // On circuit thread: execute inline (fastest path)
      pipe.deliver(value);
    } else {
      // External thread: add to thread-local batch
      Batch batch = threadBatch.get();
      batch.pipes[batch.count] = pipe;
      batch.values[batch.count] = value;
      batch.count++;

      // Flush if batch is full
      if (batch.count >= DEFAULT_BATCH_SIZE) {
        flushBatch(batch);
      }
    }
  }

  /**
   * Flushes the current thread's batch buffer to the circuit.
   *
   * @param batch the batch to flush
   */
  private void flushBatch(Batch batch) {
    if (batch.count == 0) {
      return;
    }

    // Create a snapshot of the batch
    Batch snapshot = new Batch();
    System.arraycopy(batch.pipes, 0, snapshot.pipes, 0, batch.count);
    System.arraycopy(batch.values, 0, snapshot.values, 0, batch.count);
    snapshot.count = batch.count;

    // Reset the thread-local batch
    batch.count = 0;

    // Push snapshot to ingress
    Node n = new Node();
    n.batch = snapshot;
    Node head;
    do {
      head = ingressHead;
      n.next = head;
    } while (!HEAD.compareAndSet(this, head, n));

    // Signal circuit thread
    LockSupport.unpark(thread);
  }

  /**
   * Flushes the current thread's batch buffer if there are pending emissions.
   *
   * <p>This is useful to force immediate processing without waiting for batch threshold.
   */
  public void flush() {
    if (Thread.currentThread() == thread) {
      return; // Circuit thread doesn't need to flush
    }
    Batch batch = threadBatch.get();
    if (batch.count > 0) {
      flushBatch(batch);
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
      // Wrap runnable in a pipe emission
      FsCircuitPipe<Runnable> taskPipe = new FsCircuitPipe<>(this, Runnable::run, null);
      emit(taskPipe, task);
      flush(); // Ensure task is submitted immediately
    }
  }

  @Override
  public boolean isRunning() {
    return running;
  }

  // === VALVE LOOP ===

  private void loop() {
    int spins = 0;
    while (running || ingressHead != null || transitHead != transitTail) {
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

      // 2. Drain ingress batches (reverse LIFO to FIFO)
      Node batchNode = (Node) HEAD.getAndSet(this, null);
      if (batchNode != null) {
        batchNode = reverse(batchNode);

        while (batchNode != null) {
          Batch batch = batchNode.batch;

          // Process entire batch
          for (int i = 0; i < batch.count; i++) {
            try {
              batch.pipes[i].deliver(batch.values[i]);
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
          }

          batchNode = batchNode.next;
          worked = true;
        }
      }

      // 3. Spin then park
      if (worked) {
        spins = 0;
      } else if (++spins < 1000) {
        Thread.onSpinWait();
      } else {
        spins = 0;
        // Double-check before parking
        if (ingressHead == null && transitHead == transitTail && running) {
          LockSupport.park();
        }
      }
    }
  }

  private Node reverse(Node head) {
    Node prev = null;
    while (head != null) {
      Node next = head.next;
      head.next = prev;
      prev = head;
      head = next;
    }
    return prev;
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
    // Flush any pending batches from this thread
    flush();
    // Wait for all work to complete
    while (ingressHead != null || transitHead != transitTail) {
      Thread.onSpinWait();
    }
  }

  @Override
  public void close() {
    // Flush all thread-local batches before closing
    // Note: This only flushes the current thread's batch
    // Other threads should call flush() before close()
    flush();
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
    if (name == null) {
      // Anonymous pipe - create minimal subject
      return new FsSubject<>(null, (FsSubject<?>) subject, Pipe.class);
    }
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
    throw new UnsupportedOperationException("Cell not yet implemented for FsBatchCircuit");
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
