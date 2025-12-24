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
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.jctools.queues.MpscUnboundedXaddArrayQueue;

/**
 * Circuit using JCTools MpscUnboundedXaddArrayQueue.
 *
 * <p>MpscUnboundedXaddArrayQueue uses XADD instead of CAS loop for reduced
 * contention, and pooled chunks for reduced allocation overhead.
 */
public final class FsJctoolsCircuit implements FsInternalCircuit {

  /** Task record holding receiver and value - faster than lambda closure. */
  private record Task(Consumer<?> receiver, Object value) {}

  private static final int SPIN_TRIES = 1000;
  private static final int CHUNK_SIZE = 256;   // Smaller chunks for XADD variant
  private static final int POOL_SIZE = 4;      // Pooled chunks to reduce allocation

  // JCTools MPSC queue with XADD and pooled chunks
  private final MpscUnboundedXaddArrayQueue<Task> ingress =
      new MpscUnboundedXaddArrayQueue<>(CHUNK_SIZE, POOL_SIZE);

  // ArrayDeque for circuit-thread only (no memory barriers needed)
  private final ArrayDeque<Task> transit = new ArrayDeque<>();

  // Circuit state
  private final Subject<Circuit> subject;
  private volatile Thread thread;  // Lazy-started
  private volatile long threadId;  // Cached thread ID for fast comparison
  private volatile boolean started;  // Fast path check (once true, never false)
  private volatile boolean running = true;
  private final AtomicBoolean parked = new AtomicBoolean(false);
  private final List<Subscriber<State>> subscribers = new ArrayList<>();

  // Lock for lazy thread start
  private final Object startLock = new Object();

  public FsJctoolsCircuit(Subject<Circuit> subject) {
    this.subject = subject;
    // Thread is started lazily on first enqueue
  }

  /** Ensure thread is started (lazy initialization with double-checked locking). */
  private void ensureStarted() {
    if (started) return;  // Fast path - no sync needed once started
    synchronized (startLock) {
      if (thread == null) {
        Thread t = Thread.ofVirtual()
            .name("circuit-" + subject.name())
            .start(this::loop);
        thread = t;
        threadId = t.threadId();  // Cache thread ID for fast comparison
        started = true;  // Publish after thread is assigned
      }
    }
  }

  @Override
  public void enqueue(Consumer<?> receiver, Object value) {
    Task task = new Task(receiver, value);
    if (isCircuitThread()) {
      // Transit path: direct to ArrayDeque (single-threaded, no barriers)
      transit.offer(task);
    } else {
      // External path: through MPSC queue
      ensureStarted();
      ingress.offer(task);
      if (parked.get() && parked.compareAndSet(true, false)) {
        LockSupport.unpark(thread);
      }
    }
  }

  @Override
  public boolean isCircuitThread() {
    // Fast path: compare thread IDs (long comparison is faster than object comparison)
    return started && Thread.currentThread().threadId() == threadId;
  }

  @Override
  public boolean isRunning() {
    return running;
  }

  private void loop() {
    int spins = 0;

    for (;;) {
      boolean didWork = false;

      // 1. Drain transit queue first (cascading emissions have priority)
      Task task;
      while ((task = transit.poll()) != null) {
        runTask(task);
        didWork = true;
      }

      // 2. Drain ingress, interleaving transit after each
      int drained = ingress.drain(t -> {
        runTask(t);
        // Drain transit after each ingress task (cascading has priority)
        Task tr;
        while ((tr = transit.poll()) != null) {
          runTask(tr);
        }
      });
      if (drained > 0) didWork = true;

      // 3. Exit check
      if (!running && ingress.isEmpty() && transit.isEmpty()) {
        break;
      }

      // 4. Spin-then-park
      if (didWork) {
        spins = 0;
      } else if (spins < SPIN_TRIES) {
        spins++;
        Thread.onSpinWait();
      } else {
        spins = 0;
        parked.set(true);
        if (ingress.isEmpty() && transit.isEmpty() && running) {
          LockSupport.park();
        }
        parked.set(false);
      }
    }
  }

  /** Execute a task, swallowing exceptions. */
  @SuppressWarnings("unchecked")
  private void runTask(Task task) {
    try {
      ((Consumer<Object>) task.receiver()).accept(task.value());
    } catch (Exception ignored) {
    }
  }

  @Override
  public Subject<Circuit> subject() {
    return subject;
  }

  @Override
  public void await() {
    Thread t = thread;
    if (t == null) {
      // Thread never started - nothing to await
      return;
    }
    if (Thread.currentThread() == t) {
      throw new IllegalStateException("Cannot call Circuit::await from within a circuit's thread");
    }
    if (!running) {
      // Circuit is closed - wait for thread to finish draining queues
      try {
        t.join();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      return;
    }
    // Enqueue a sentinel task and wait for it to complete.
    // When it runs, all prior work is done (FIFO ordering).
    var latch = new CountDownLatch(1);
    enqueue(ignored -> latch.countDown(), null);
    try {
      latch.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  @Override
  public void close() {
    running = false;
    Thread t = thread;
    if (t == null) {
      // Thread never started - nothing to close
      return;
    }
    // Just signal shutdown - don't block waiting for completion
    // Use await() if you need to wait for pending emissions to complete
    LockSupport.unpark(t);
  }

  @Override
  public <I, E> Cell<I, E> cell(
      Name name,
      Composer<E, Pipe<I>> ingress,
      Composer<E, Pipe<E>> egress,
      Receptor<? super E> receptor) {
    requireNonNull(name);
    requireNonNull(ingress);
    requireNonNull(egress);
    requireNonNull(receptor);
    return new FsCell<>(new FsSubject<>(name, (FsSubject<?>) subject, Cell.class), this, ingress, egress, receptor);
  }

  @Override
  public <P extends Percept, E> Conduit<P, E> conduit(Name name, Composer<E, ? extends P> composer) {
    requireNonNull(name);
    requireNonNull(composer);
    return new FsConduit<>((FsSubject<?>) subject, name, channel -> composer.compose(channel), this);
  }

  @Override
  public <P extends Percept, E> Conduit<P, E> conduit(Name name, Composer<E, ? extends P> composer, Configurer<Flow<E>> configurer) {
    requireNonNull(name);
    requireNonNull(composer);
    requireNonNull(configurer);
    return new FsConduit<>((FsSubject<?>) subject, name, channel -> composer.compose(channel), this, configurer);
  }

  @Override
  public <E> Pipe<E> pipe(Pipe<E> target) {
    requireNonNull(target);
    // Chain: use fast-path constructor if target is FsPipe
    if (target instanceof FsPipe<E> fsPipe) {
      return new FsPipe<>(target.subject(), this, fsPipe);
    }
    return new FsPipe<>(target.subject(), this, target::emit);
  }

  @Override
  public <E> Pipe<E> pipe(Receptor<E> receptor) {
    // No source pipe - create a new subject
    Subject<Pipe<E>> pipeSubject = new FsSubject<>(null, (FsSubject<?>) subject, Pipe.class);
    return new FsPipe<>(pipeSubject, this, receptor::receive);
  }

  @Override
  public <E> Pipe<E> pipe(Pipe<E> target, Configurer<Flow<E>> configurer) {
    // Chain with flow: use fast-path constructor if target is FsPipe
    FsPipe<E> asyncPipe;
    if (target instanceof FsPipe<E> fsPipe) {
      asyncPipe = new FsPipe<>(target.subject(), this, fsPipe);
    } else {
      asyncPipe = new FsPipe<>(target.subject(), this, target::emit);
    }
    FsFlow<E> flow = new FsFlow<>(target.subject(), this, asyncPipe);
    configurer.configure(flow);
    return flow.pipe();
  }

  @Override
  public <E> Pipe<E> pipe(Receptor<E> receptor, Configurer<Flow<E>> configurer) {
    // No source pipe - create a new subject
    Subject<Pipe<E>> pipeSubject = new FsSubject<>(null, (FsSubject<?>) subject, Pipe.class);
    FsPipe<E> asyncPipe = new FsPipe<>(pipeSubject, this, receptor::receive);
    FsFlow<E> flow = new FsFlow<>(pipeSubject, this, asyncPipe);
    configurer.configure(flow);
    return flow.pipe();
  }

  @Override
  public <E> Pipe<E> pipe(Name name, Pipe<E> target) {
    requireNonNull(target);
    // Named chain: use fast-path constructor if target is FsPipe
    if (target instanceof FsPipe<E> fsPipe) {
      return new FsPipe<>(target.subject(), this, fsPipe);
    }
    return new FsPipe<>(target.subject(), this, target::emit);
  }

  @Override
  public <E> Pipe<E> pipe(Name name, Receptor<E> receptor) {
    // Named pipe from receptor - create subject with name
    Subject<Pipe<E>> pipeSubject = new FsSubject<>(name, (FsSubject<?>) subject, Pipe.class);
    return new FsPipe<>(pipeSubject, this, receptor::receive);
  }

  @Override
  public <E> Pipe<E> pipe(Name name, Pipe<E> target, Configurer<Flow<E>> configurer) {
    // Named chain with flow: use fast-path constructor if target is FsPipe
    FsPipe<E> asyncPipe;
    if (target instanceof FsPipe<E> fsPipe) {
      asyncPipe = new FsPipe<>(target.subject(), this, fsPipe);
    } else {
      asyncPipe = new FsPipe<>(target.subject(), this, target::emit);
    }
    FsFlow<E> flow = new FsFlow<>(target.subject(), this, asyncPipe);
    configurer.configure(flow);
    return flow.pipe();
  }

  @Override
  public <E> Pipe<E> pipe(Name name, Receptor<E> receptor, Configurer<Flow<E>> configurer) {
    // Named pipe from receptor - create subject with name
    Subject<Pipe<E>> pipeSubject = new FsSubject<>(name, (FsSubject<?>) subject, Pipe.class);
    FsPipe<E> asyncPipe = new FsPipe<>(pipeSubject, this, receptor::receive);
    FsFlow<E> flow = new FsFlow<>(pipeSubject, this, asyncPipe);
    configurer.configure(flow);
    return flow.pipe();
  }

  @Override
  public <E> Subscriber<E> subscriber(Name name, BiConsumer<Subject<Channel<E>>, Registrar<E>> callback) {
    requireNonNull(name);
    requireNonNull(callback);
    return new FsSubscriber<>(new FsSubject<>(name, (FsSubject<?>) subject, Subscriber.class), callback);
  }

  @Override
  @SuppressWarnings("unchecked")
  public Subscription subscribe(Subscriber<State> subscriber) {
    requireNonNull(subscriber);
    // Check for cross-circuit subscription
    if (subscriber.subject() instanceof FsSubject<?> subSubject) {
      FsSubject<?> subscriberCircuit = subSubject.findCircuitAncestor();
      if (subscriberCircuit != null && subscriberCircuit != subject) {
        throw new FsException("Subscriber belongs to a different circuit");
      }
    }
    subscribers.add(subscriber);
    return new FsSubscription((Subject<Subscription>) (Subject<?>) subject, () -> subscribers.remove(subscriber));
  }

  @Override
  @SuppressWarnings("unchecked")
  public Reservoir<State> reservoir() {
    return new FsReservoir<>((Subject<Reservoir<State>>) (Subject<?>) subject);
  }
}
