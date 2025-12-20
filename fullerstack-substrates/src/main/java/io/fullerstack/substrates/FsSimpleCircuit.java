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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BiConsumer;

/**
 * Simple circuit implementation following the specification.
 *
 * <p>Key design:
 * <ul>
 *   <li>ConcurrentLinkedQueue for ingress (MPSC, wait-free producers)
 *   <li>ArrayDeque for transit (single-threaded)
 *   <li>Virtual thread with spin-then-park
 * </ul>
 */
public final class FsSimpleCircuit implements FsInternalCircuit {

  private static final int SPIN_TRIES = 1000;

  // Task record for queue entries
  private record Task(FsAsyncPipe<?> pipe, Object value) {}

  // Ingress: MPSC queue (external threads → circuit thread)
  private final ConcurrentLinkedQueue<Task> ingress = new ConcurrentLinkedQueue<>();

  // Transit: local deque (circuit thread only, for cascading)
  private final ArrayDeque<Runnable> transit = new ArrayDeque<>();

  // Circuit state
  private final Subject<Circuit> subject;
  private volatile Thread thread;
  private volatile boolean running = true;
  private volatile boolean parked = false;
  private final List<Subscriber<State>> subscribers = new ArrayList<>();

  public FsSimpleCircuit(Subject<Circuit> subject) {
    this.subject = subject;
    // Start virtual thread immediately
    this.thread = Thread.ofVirtual()
        .name("circuit-" + subject.name())
        .start(this::loop);
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // HOT PATH: ENQUEUE
  // ═══════════════════════════════════════════════════════════════════════════

  @Override
  public <E> void enqueue(FsAsyncPipe<E> pipe, Object value) {
    ingress.offer(new Task(pipe, value));

    // Wake consumer if parked
    if (parked) {
      LockSupport.unpark(thread);
    }
  }

  void cascade(Runnable task) {
    transit.addLast(task);
  }

  @Override
  public void submit(Runnable task) {
    Thread t = thread;
    if (t != null && Thread.currentThread() == t) {
      cascade(task);
    } else {
      FsAsyncPipe<Runnable> taskPipe = new FsAsyncPipe<>((FsSubject<?>) subject, null, this, Runnable::run);
      enqueue(taskPipe, task);
    }
  }

  @Override
  public boolean isRunning() {
    return running;
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // CONSUMER LOOP
  // ═══════════════════════════════════════════════════════════════════════════

  private void loop() {
    int spins = 0;

    for (;;) {
      boolean didWork = false;

      // 1. Drain transit queue (priority)
      Runnable r;
      while ((r = transit.pollFirst()) != null) {
        try { r.run(); } catch (Exception ignored) {}
        didWork = true;
      }

      // 2. Process ingress
      Task task;
      while ((task = ingress.poll()) != null) {
        try { task.pipe.deliver(task.value); } catch (Exception ignored) {}
        didWork = true;

        // Drain transit after each (cascading has priority)
        while ((r = transit.pollFirst()) != null) {
          try { r.run(); } catch (Exception ignored) {}
        }
      }

      // 3. Check exit condition
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
        parked = true;
        if (ingress.isEmpty() && transit.isEmpty() && running) {
          LockSupport.park();
        }
        parked = false;
      }
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // CIRCUIT INTERFACE
  // ═══════════════════════════════════════════════════════════════════════════

  @Override
  public Subject<Circuit> subject() {
    return subject;
  }

  @Override
  public void await() {
    Thread t = thread;
    if (t != null && Thread.currentThread() == t) {
      throw new IllegalStateException("Cannot await from circuit thread");
    }
    while (!ingress.isEmpty() || !transit.isEmpty()) {
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

  @Override
  public <P extends Percept, E> Conduit<P, E> conduit(Name name, Composer<E, ? extends P> composer) {
    requireNonNull(name, "name must not be null");
    requireNonNull(composer, "composer must not be null");
    return new FsConduit<>((FsSubject<?>) subject, name, channel -> composer.compose(channel), this);
  }

  @Override
  public <P extends Percept, E> Conduit<P, E> conduit(
      Name name, Composer<E, ? extends P> composer, Configurer<Flow<E>> configurer) {
    requireNonNull(name, "name must not be null");
    requireNonNull(composer, "composer must not be null");
    requireNonNull(configurer, "configurer must not be null");
    return new FsConduit<>((FsSubject<?>) subject, name, channel -> composer.compose(channel), this, configurer);
  }

  @Override
  public <E> Pipe<E> pipe(Pipe<E> target) {
    requireNonNull(target, "target must not be null");
    return new FsAsyncPipe<>((FsSubject<?>) subject, null, this, target::emit);
  }

  @Override
  public <E> Pipe<E> pipe(Receptor<E> receptor) {
    requireNonNull(receptor, "receptor must not be null");
    return new FsAsyncPipe<>((FsSubject<?>) subject, null, this, receptor::receive);
  }

  @Override
  public <E> Pipe<E> pipe(Pipe<E> target, Configurer<Flow<E>> configurer) {
    requireNonNull(target, "target must not be null");
    requireNonNull(configurer, "configurer must not be null");
    FsAsyncPipe<E> asyncPipe = new FsAsyncPipe<>((FsSubject<?>) subject, null, this, target::emit);
    FsFlow<E> flow = new FsFlow<>((FsSubject<?>) subject, null, asyncPipe);
    configurer.configure(flow);
    return flow.pipe();
  }

  @Override
  public <E> Pipe<E> pipe(Receptor<E> receptor, Configurer<Flow<E>> configurer) {
    requireNonNull(receptor, "receptor must not be null");
    requireNonNull(configurer, "configurer must not be null");
    FsAsyncPipe<E> asyncPipe = new FsAsyncPipe<>((FsSubject<?>) subject, null, this, receptor::receive);
    FsFlow<E> flow = new FsFlow<>((FsSubject<?>) subject, null, asyncPipe);
    configurer.configure(flow);
    return flow.pipe();
  }

  @Override
  public <E> Pipe<E> pipe(Name name, Pipe<E> target) {
    requireNonNull(name, "name must not be null");
    requireNonNull(target, "target must not be null");
    return new FsAsyncPipe<>((FsSubject<?>) subject, name, this, target::emit);
  }

  @Override
  public <E> Pipe<E> pipe(Name name, Receptor<E> receptor) {
    requireNonNull(name, "name must not be null");
    requireNonNull(receptor, "receptor must not be null");
    return new FsAsyncPipe<>((FsSubject<?>) subject, name, this, receptor::receive);
  }

  @Override
  public <E> Pipe<E> pipe(Name name, Pipe<E> target, Configurer<Flow<E>> configurer) {
    requireNonNull(name, "name must not be null");
    requireNonNull(target, "target must not be null");
    requireNonNull(configurer, "configurer must not be null");
    FsAsyncPipe<E> asyncPipe = new FsAsyncPipe<>((FsSubject<?>) subject, name, this, target::emit);
    FsFlow<E> flow = new FsFlow<>((FsSubject<?>) subject, name, asyncPipe);
    configurer.configure(flow);
    return flow.pipe();
  }

  @Override
  public <E> Pipe<E> pipe(Name name, Receptor<E> receptor, Configurer<Flow<E>> configurer) {
    requireNonNull(name, "name must not be null");
    requireNonNull(receptor, "receptor must not be null");
    requireNonNull(configurer, "configurer must not be null");
    FsAsyncPipe<E> asyncPipe = new FsAsyncPipe<>((FsSubject<?>) subject, name, this, receptor::receive);
    FsFlow<E> flow = new FsFlow<>((FsSubject<?>) subject, name, asyncPipe);
    configurer.configure(flow);
    return flow.pipe();
  }

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
