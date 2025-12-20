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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BiConsumer;
import org.jctools.queues.MpscUnboundedArrayQueue;

/**
 * Circuit using JCTools MpscUnboundedArrayQueue.
 *
 * <p>MpscUnboundedArrayQueue is optimized for MPSC pattern:
 * - Wait-free offer for producers
 * - Uses linked chunks (less allocation than CLQ)
 * - Better cache locality than CLQ
 */
public final class FsJctoolsCircuit implements FsInternalCircuit {

  private static final int SPIN_TRIES = 1000;
  private static final int CHUNK_SIZE = 1024;  // Chunk size for unbounded queue

  // Task record - still allocates but JCTools doesn't allocate Node
  private record Task(FsAsyncPipe<?> pipe, Object value) {}

  // JCTools MPSC queue - unbounded, chunked allocation
  private final MpscUnboundedArrayQueue<Task> ingress = new MpscUnboundedArrayQueue<>(CHUNK_SIZE);

  // Transit queue for cascading (consumer thread only)
  private final ArrayDeque<Runnable> transit = new ArrayDeque<>();

  // Circuit state
  private final Subject<Circuit> subject;
  private final Thread thread;
  private volatile boolean running = true;
  private final AtomicBoolean parked = new AtomicBoolean(false);
  private final List<Subscriber<State>> subscribers = new ArrayList<>();

  public FsJctoolsCircuit(Subject<Circuit> subject) {
    this.subject = subject;
    this.thread = Thread.ofVirtual()
        .name("circuit-" + subject.name())
        .start(this::loop);
  }

  @Override
  public <E> void enqueue(FsAsyncPipe<E> pipe, Object value) {
    ingress.offer(new Task(pipe, value));
    // CAS ensures only ONE producer wins and calls unpark
    if (parked.compareAndSet(true, false)) {
      LockSupport.unpark(thread);
    }
  }

  void cascade(Runnable task) {
    transit.push(task);  // LIFO - most recent first (true depth-first)
  }

  @Override
  public void submit(Runnable task) {
    if (Thread.currentThread() == thread) {
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

  private void loop() {
    int spins = 0;

    for (;;) {
      boolean didWork = false;

      // 1. Drain transit stack (LIFO - depth-first)
      Runnable r;
      while ((r = transit.poll()) != null) {
        try { r.run(); } catch (Exception ignored) {}
        didWork = true;
      }

      // 2. Drain ingress using bulk drain
      int drained = ingress.drain(task -> {
        try { task.pipe.deliver(task.value); } catch (Exception ignored) {}
        // Drain transit stack after each (cascading has priority)
        Runnable tr;
        while ((tr = transit.poll()) != null) {
          try { tr.run(); } catch (Exception ignored) {}
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

  @Override
  public Subject<Circuit> subject() {
    return subject;
  }

  @Override
  public void await() {
    if (Thread.currentThread() == thread) {
      throw new IllegalStateException("Cannot await from circuit thread");
    }
    // Submit a sentinel task and wait for it to complete.
    // When it runs, all prior work is done (FIFO ordering).
    var latch = new CountDownLatch(1);
    submit(latch::countDown);
    try {
      latch.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
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
  @SuppressWarnings("unchecked")
  public <E> Pipe<E> pipe(Pipe<E> target) {
    // If target is already an async pipe on this circuit, return it directly
    if (target instanceof FsAsyncPipe<?> asyncTarget && asyncTarget.circuit() == this) {
      return (Pipe<E>) asyncTarget;
    }
    return new FsAsyncPipe<>((FsSubject<?>) subject, null, this, target::emit);
  }

  @Override
  public <E> Pipe<E> pipe(Receptor<E> receptor) {
    return new FsAsyncPipe<>((FsSubject<?>) subject, null, this, receptor::receive);
  }

  @Override
  public <E> Pipe<E> pipe(Pipe<E> target, Configurer<Flow<E>> configurer) {
    FsAsyncPipe<E> asyncPipe = new FsAsyncPipe<>((FsSubject<?>) subject, null, this, target::emit);
    FsFlow<E> flow = new FsFlow<>((FsSubject<?>) subject, null, asyncPipe);
    configurer.configure(flow);
    return flow.pipe();
  }

  @Override
  public <E> Pipe<E> pipe(Receptor<E> receptor, Configurer<Flow<E>> configurer) {
    FsAsyncPipe<E> asyncPipe = new FsAsyncPipe<>((FsSubject<?>) subject, null, this, receptor::receive);
    FsFlow<E> flow = new FsFlow<>((FsSubject<?>) subject, null, asyncPipe);
    configurer.configure(flow);
    return flow.pipe();
  }

  @Override
  public <E> Pipe<E> pipe(Name name, Pipe<E> target) {
    return new FsAsyncPipe<>((FsSubject<?>) subject, name, this, target::emit);
  }

  @Override
  public <E> Pipe<E> pipe(Name name, Receptor<E> receptor) {
    return new FsAsyncPipe<>((FsSubject<?>) subject, name, this, receptor::receive);
  }

  @Override
  public <E> Pipe<E> pipe(Name name, Pipe<E> target, Configurer<Flow<E>> configurer) {
    FsAsyncPipe<E> asyncPipe = new FsAsyncPipe<>((FsSubject<?>) subject, name, this, target::emit);
    FsFlow<E> flow = new FsFlow<>((FsSubject<?>) subject, name, asyncPipe);
    configurer.configure(flow);
    return flow.pipe();
  }

  @Override
  public <E> Pipe<E> pipe(Name name, Receptor<E> receptor, Configurer<Flow<E>> configurer) {
    FsAsyncPipe<E> asyncPipe = new FsAsyncPipe<>((FsSubject<?>) subject, name, this, receptor::receive);
    FsFlow<E> flow = new FsFlow<>((FsSubject<?>) subject, name, asyncPipe);
    configurer.configure(flow);
    return flow.pipe();
  }

  @Override
  public <E> Subscriber<E> subscriber(Name name, BiConsumer<Subject<Channel<E>>, Registrar<E>> callback) {
    return new FsSubscriber<>(new FsSubject<>(name, (FsSubject<?>) subject, Subscriber.class), callback);
  }

  @Override
  @SuppressWarnings("unchecked")
  public Subscription subscribe(Subscriber<State> subscriber) {
    subscribers.add(subscriber);
    return new FsSubscription((Subject<Subscription>) (Subject<?>) subject, () -> subscribers.remove(subscriber));
  }

  @Override
  @SuppressWarnings("unchecked")
  public Reservoir<State> reservoir() {
    return new FsReservoir<>((Subject<Reservoir<State>>) (Subject<?>) subject);
  }
}
