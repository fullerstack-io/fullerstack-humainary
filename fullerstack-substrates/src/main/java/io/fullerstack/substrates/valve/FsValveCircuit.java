package io.fullerstack.substrates.valve;

import static java.util.Objects.requireNonNull;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

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

/**
 * Optimized valve-based Circuit implementation.
 *
 * <p>Optimizations over FsCircuit:
 * <ul>
 *   <li>Object pooling for Node objects - eliminates allocation overhead</li>
 *   <li>Pipe reference in Node - enables lazy subject in pipes</li>
 *   <li>VarHandle for ultra-fast memory access</li>
 *   <li>Aggressive inlining hints for hot paths</li>
 * </ul>
 *
 * <ul>
 *   <li>Fast ingress: MPSC linked list (one CAS per enqueue)</li>
 *   <li>Transit priority: circuit-thread cascades use simple array (no sync)</li>
 *   <li>Batch processing with spin-wait → park progression</li>
 *   <li>Object pooling: reuse Node objects to avoid allocation</li>
 * </ul>
 */
public final class FsValveCircuit implements FsInternalCircuit {

  // === Ingress: MPSC lock-free linked list ===
  private static final VarHandle HEAD;
  private static final VarHandle POOL_HEAD;

  static {
    try {
      var lookup = MethodHandles.lookup();
      HEAD = lookup.findVarHandle(FsValveCircuit.class, "ingressHead", Node.class);
      POOL_HEAD = lookup.findVarHandle(FsValveCircuit.class, "poolHead", Node.class);
    } catch (Exception e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  static final class Node {
    FsCircuitPipe<?> pipe;
    Object value;
    Node next;

    void clear() {
      pipe = null;
      value = null;
      next = null;
    }
  }

  private volatile Node ingressHead;

  // === Object pool for Node reuse ===
  private static final int POOL_SIZE = 256;
  private volatile Node poolHead;
  private int poolCount;

  // === Transit: simple array ring (circuit thread only - no sync needed) ===
  private static final int TRANSIT_SIZE = 256;
  private static final int TRANSIT_MASK = TRANSIT_SIZE - 1;
  private final Runnable[] transit = new Runnable[TRANSIT_SIZE];
  private int transitHead;
  private int transitTail;

  // === Batch tuning (disabled for now - process all) ===
  // private static final int INITIAL_BATCH_LIMIT = 64;
  // private static final int MAX_BATCH_LIMIT = 256;
  // private int batchLimit = INITIAL_BATCH_LIMIT;

  // === Thread state ===
  private final Subject<Circuit> subject;
  private volatile Thread thread;
  private volatile boolean running = true;

  // === Subscribers ===
  private final List<Subscriber<State>> subscribers = new ArrayList<>();

  public FsValveCircuit(Subject<Circuit> subject) {
    this.subject = subject;
    this.thread = Thread.ofVirtual().name("valve-" + subject.name()).start(this::loop);
  }

  // === HOT PATH: emit ===

  @Override
  public <E> void emit(FsCircuitPipe<E> pipe, Object value) {
    if (Thread.currentThread() == thread) {
      // On circuit thread: execute inline (fastest path)
      pipe.deliver(value);
    } else {
      // External thread: push to ingress + signal
      Node n = allocateNode();
      n.pipe = pipe;
      n.value = value;
      Node head;
      do {
        head = (Node) HEAD.getAcquire(this);
        n.next = head;
      } while (!HEAD.compareAndSet(this, head, n));

      // Always unpark - lazy unpark has race conditions
      LockSupport.unpark(thread);
    }
  }

  // === Node allocation and pooling ===

  private Node allocateNode() {
    Node n;
    do {
      n = (Node) POOL_HEAD.getAcquire(this);
      if (n == null) {
        return new Node();
      }
    } while (!POOL_HEAD.compareAndSet(this, n, n.next));
    return n;
  }

  private void releaseNode(Node n) {
    n.clear();
    // Only pool if not at capacity
    if (poolCount < POOL_SIZE) {
      Node head;
      do {
        head = (Node) POOL_HEAD.getAcquire(this);
        n.next = head;
      } while (!POOL_HEAD.compareAndSet(this, head, n));
      poolCount++;
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

      // 2. Drain ingress batch (reverse LIFO to FIFO)
      Node batch = (Node) HEAD.getAndSet(this, null);
      if (batch != null) {
        batch = reverse(batch);

        while (batch != null) {
          Node current = batch;
          batch = batch.next;

          try {
            current.pipe.deliver(current.value);
          } catch (Exception ignored) {
          }

          // Release node back to pool
          releaseNode(current);

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
      }

      // 3. Spin then park
      // NOTE: Lazy unpark removed due to race condition.
      // The safe pattern requires memory barriers which add overhead.
      // Always unpark is simpler and still fast (~5ns).
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
    while (ingressHead != null || transitHead != transitTail) {
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
    // Delegate to FsCell with this circuit
    throw new UnsupportedOperationException("Cell not yet implemented for FsValveCircuit");
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
