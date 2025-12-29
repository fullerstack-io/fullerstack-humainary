package io.fullerstack.substrates;

import static java.util.Objects.requireNonNull;

import io.humainary.substrates.api.Substrates.Cell;
import io.humainary.substrates.api.Substrates.Channel;
import io.humainary.substrates.api.Substrates.Circuit;
import io.humainary.substrates.api.Substrates.Composer;
import io.humainary.substrates.api.Substrates.Conduit;
import io.humainary.substrates.api.Substrates.Configurer;
import io.humainary.substrates.api.Substrates.Flow;
import io.humainary.substrates.api.Substrates.Idempotent;
import io.humainary.substrates.api.Substrates.Name;
import io.humainary.substrates.api.Substrates.New;
import io.humainary.substrates.api.Substrates.NotNull;
import io.humainary.substrates.api.Substrates.Percept;
import io.humainary.substrates.api.Substrates.Pipe;
import io.humainary.substrates.api.Substrates.Provided;
import io.humainary.substrates.api.Substrates.Receptor;
import io.humainary.substrates.api.Substrates.Registrar;
import io.humainary.substrates.api.Substrates.Reservoir;
import io.humainary.substrates.api.Substrates.State;
import io.humainary.substrates.api.Substrates.Subject;
import io.humainary.substrates.api.Substrates.Subscriber;
import io.humainary.substrates.api.Substrates.Subscription;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BiConsumer;

/**
 * Circuit implementation using dual-queue architecture per Humainary specification.
 *
 * <p>Uses William Louth's abstract job pattern: jobs have an intrusive `next` field for queue
 * linkage, eliminating separate queue node allocation.
 *
 * <p>Key design:
 * <ul>
 *   <li>External emissions: create job, submit to ingress MPSC queue (write-to-head)
 *   <li>Cascade emissions (on circuit thread): enqueue to transit queue (FIFO, no recursion)
 *   <li>Transit queue drains completely before returning to ingress (priority processing)
 *   <li>Stack-safe: deep cascading chains use iteration, never recursion
 * </ul>
 */
@Provided
public final class FsCircuit implements Circuit {

  /** Sentinel job for queue initialization (never executed). */
  private static final class SentinelJob extends Job {
    @Override void run() {}
  }

  /** Shared sentinel instance - immutable, no state. */
  private static final Job SENTINEL = new SentinelJob();

  // Ingress queue: write-to-head MPSC for external emissions
  private final AtomicReference<Job> stackHead;

  // Transit queue: intrusive FIFO for cascading emissions (circuit-thread only, no sync needed)
  // Uses the same Job.next field as ingress - jobs move between queues, never in both
  private Job transitHead;
  private Job transitTail;

  // Circuit state
  private final Subject<Circuit> subject;
  private final Thread thread;
  private volatile boolean running = true;  // Volatile for visibility across threads
  private final List<Subscriber<State>> subscribers = new ArrayList<>();

  public FsCircuit(Subject<Circuit> subject) {
    this.subject = subject;
    // Initialize write-to-head queue with sentinel
    this.stackHead = new AtomicReference<>(SENTINEL);
    // Start thread eagerly
    this.thread = Thread.ofVirtual().name("circuit-" + subject.name()).start(this::loop);
  }

  /**
   * Submit a job to the circuit. Transit queue is the hot path (inline).
   */
  public void submit(Job job) {
    if (Thread.currentThread() == thread) {
      // Hot path: cascade via transit queue (inline, no method call)
      if (!running) return;
      job.next = null;
      if (transitTail != null) {
        transitTail.next = job;
      } else {
        transitHead = job;
      }
      transitTail = job;
    } else {
      // Cold path: external submission (extracted)
      submitExternal(job);
    }
  }

  /** Cold path: external submission via write-to-head MPSC queue. */
  private void submitExternal(Job job) {
    if (!running) return;
    Job prev = stackHead.getAndSet(job);
    job.next = prev;
    if (prev == SENTINEL) {
      // Queue was empty - circuit may be parked, wake it
      LockSupport.unpark(thread);
    }
  }

  /** Returns true if the current thread is the circuit's processing thread. */
  public boolean isCircuitThread() {
    return Thread.currentThread() == thread;
  }

  /** Returns true if the circuit is still running. */
  public boolean isRunning() {
    return running;
  }

  /** Returns the circuit's thread for direct comparison. */
  public Thread thread() {
    return thread;
  }

  /** Returns the head reference for write-to-head MPSC queue access. */
  public AtomicReference<Job> head() {
    return stackHead;
  }

  /** Spin iterations before parking. */
  private static final int SPIN_LIMIT =
      Integer.getInteger("io.fullerstack.substrates.spinLimit", 128);

  /**
   * Core execution loop - drains transit (priority), then ingress.
   */
  private void loop() {
    int spins = 0;

    for (; ; ) {
      // Priority 1: Drain transit queue completely (cascading emissions)
      if (drainTransit()) {
        spins = 0;
        continue;
      }

      // Priority 2: Grab batch from ingress and process
      if (drainIngress()) {
        spins = 0;
        continue;
      }

      // Check for shutdown (both queues empty)
      if (!running && stackHead.get() == SENTINEL && transitHead == null) {
        break;
      }

      // Spin before parking
      if (spins < SPIN_LIMIT) {
        spins++;
        Thread.onSpinWait();
      } else {
        LockSupport.park();
        spins = 0;
      }
    }
  }

  /**
   * Drain transit queue completely (FIFO order via intrusive linked list).
   * Returns true if any work was done.
   */
  private boolean drainTransit() {
    if (transitHead == null) {
      return false;
    }
    // Drain all transit jobs - cascading may add more, so loop until empty
    while (transitHead != null) {
      Job job = transitHead;
      transitHead = job.next;
      if (transitHead == null) {
        transitTail = null;
      }
      try {
        job.run();  // May add more to transit via submit()
      } catch (Exception ignored) {}
    }
    return true;
  }

  /**
   * Grab batch from ingress, reverse to FIFO, process each job with transit drain after each.
   */
  private boolean drainIngress() {
    // Read first to avoid cache contention when queue is empty
    if (stackHead.get() == SENTINEL) {
      return false;
    }
    // Atomically grab entire batch from ingress
    Job batch = stackHead.getAndSet(SENTINEL);
    if (batch == SENTINEL) {
      return false;  // Race: another thread grabbed it
    }

    // Reverse to FIFO order (batch is currently LIFO)
    Job current = reverse(batch);

    // Process each ingress job, draining transit after each (causality preservation)
    while (current != null && current != SENTINEL) {
      // Save next BEFORE running - job.run() may trigger cascades that reuse next field
      Job nextIngress = current.next;

      try {
        current.run();  // May add to transit, which will use current.next
      } catch (Exception ignored) {}

      // Drain transit completely before next ingress job
      drainTransit();

      current = nextIngress;
    }

    return true;
  }

  /**
   * Reverse linked list to convert LIFO to FIFO order.
   */
  private static Job reverse(Job head) {
    Job prev = null;
    Job current = head;
    while (current != null && current != SENTINEL) {
      Job next = current.next;
      current.next = prev;
      prev = current;
      current = next;
    }
    return prev;
  }

  @Override
  public Subject<Circuit> subject() {
    return subject;
  }

  @Override
  public void await() {
    if (Thread.currentThread() == thread) {
      throw new IllegalStateException("Cannot call Circuit::await from within a circuit's thread");
    }
    if (!running) {
      try {
        thread.join();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      return;
    }
    var latch = new CountDownLatch(1);
    submit(new EmitJob(ignored -> latch.countDown(), null));
    try {
      latch.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  @Idempotent
  @Override
  public void close() {
    running = false;
    LockSupport.unpark(thread);
  }

  @New
  @NotNull
  @Override
  public <I, E> Cell<I, E> cell(
      @NotNull Name name, @NotNull Composer<E, Pipe<I>> ingress, @NotNull Composer<E, Pipe<E>> egress, @NotNull Receptor<? super E> receptor) {
    requireNonNull(name);
    requireNonNull(ingress);
    requireNonNull(egress);
    requireNonNull(receptor);
    return new FsCell<>(
        new FsSubject<>(name, (FsSubject<?>) subject, Cell.class), this, ingress, egress, receptor);
  }

  @New
  @NotNull
  @Override
  public <P extends Percept, E> Conduit<P, E> conduit(@NotNull Name name, @NotNull Composer<E, ? extends P> composer) {
    requireNonNull(name);
    requireNonNull(composer);
    return new FsConduit<>((FsSubject<?>) subject, name, channel -> composer.compose(channel), this);
  }

  @New
  @NotNull
  @Override
  public <P extends Percept, E> Conduit<P, E> conduit(
      @NotNull Name name, @NotNull Composer<E, ? extends P> composer, @NotNull Configurer<Flow<E>> configurer) {
    requireNonNull(name);
    requireNonNull(composer);
    requireNonNull(configurer);
    return new FsConduit<>(
        (FsSubject<?>) subject, name, channel -> composer.compose(channel), this, configurer);
  }

  @New
  @NotNull
  @Override
  public <E> Pipe<E> pipe(@NotNull Pipe<E> target) {
    requireNonNull(target);
    if (target instanceof FsPipe<E> fsPipe) {
      return new FsPipe<>(target.subject(), this, fsPipe.receiver());
    }
    return new FsPipe<>(target.subject(), this, target::emit);
  }

  @New
  @NotNull
  @Override
  public <E> Pipe<E> pipe(@NotNull Receptor<E> receptor) {
    Subject<Pipe<E>> pipeSubject = new FsSubject<>(null, (FsSubject<?>) subject, Pipe.class);
    return new FsPipe<>(pipeSubject, this, receptor::receive);
  }

  @New
  @NotNull
  @Override
  public <E> Pipe<E> pipe(@NotNull Pipe<E> target, @NotNull Configurer<Flow<E>> configurer) {
    FsPipe<E> fsPipe;
    if (target instanceof FsPipe<E> fp) {
      fsPipe = new FsPipe<>(target.subject(), this, fp.receiver());
    } else {
      fsPipe = new FsPipe<>(target.subject(), this, target::emit);
    }
    FsFlow<E> flow = new FsFlow<>(target.subject(), this, fsPipe);
    configurer.configure(flow);
    return flow.pipe();
  }

  @New
  @NotNull
  @Override
  public <E> Pipe<E> pipe(@NotNull Receptor<E> receptor, @NotNull Configurer<Flow<E>> configurer) {
    Subject<Pipe<E>> pipeSubject = new FsSubject<>(null, (FsSubject<?>) subject, Pipe.class);
    FsPipe<E> fsPipe = new FsPipe<>(pipeSubject, this, receptor::receive);
    FsFlow<E> flow = new FsFlow<>(pipeSubject, this, fsPipe);
    configurer.configure(flow);
    return flow.pipe();
  }

  @New
  @NotNull
  @Override
  public <E> Pipe<E> pipe(Name name, @NotNull Pipe<E> target) {
    requireNonNull(target);
    if (target instanceof FsPipe<E> fp) {
      return new FsPipe<>(target.subject(), this, fp.receiver());
    }
    return new FsPipe<>(target.subject(), this, target::emit);
  }

  @New
  @NotNull
  @Override
  public <E> Pipe<E> pipe(Name name, @NotNull Receptor<E> receptor) {
    Subject<Pipe<E>> pipeSubject = new FsSubject<>(name, (FsSubject<?>) subject, Pipe.class);
    return new FsPipe<>(pipeSubject, this, receptor::receive);
  }

  @New
  @NotNull
  @Override
  public <E> Pipe<E> pipe(Name name, @NotNull Pipe<E> target, @NotNull Configurer<Flow<E>> configurer) {
    FsPipe<E> fsPipe;
    if (target instanceof FsPipe<E> fp) {
      fsPipe = new FsPipe<>(target.subject(), this, fp.receiver());
    } else {
      fsPipe = new FsPipe<>(target.subject(), this, target::emit);
    }
    FsFlow<E> flow = new FsFlow<>(target.subject(), this, fsPipe);
    configurer.configure(flow);
    return flow.pipe();
  }

  @New
  @NotNull
  @Override
  public <E> Pipe<E> pipe(Name name, @NotNull Receptor<E> receptor, @NotNull Configurer<Flow<E>> configurer) {
    Subject<Pipe<E>> pipeSubject = new FsSubject<>(name, (FsSubject<?>) subject, Pipe.class);
    FsPipe<E> fsPipe = new FsPipe<>(pipeSubject, this, receptor::receive);
    FsFlow<E> flow = new FsFlow<>(pipeSubject, this, fsPipe);
    configurer.configure(flow);
    return flow.pipe();
  }

  @New
  @NotNull
  @Override
  public <E> Subscriber<E> subscriber(
      @NotNull Name name, @NotNull BiConsumer<Subject<Channel<E>>, Registrar<E>> callback) {
    requireNonNull(name);
    requireNonNull(callback);
    return new FsSubscriber<>(
        new FsSubject<>(name, (FsSubject<?>) subject, Subscriber.class), callback);
  }

  @New
  @NotNull
  @Override
  @SuppressWarnings("unchecked")
  public Subscription subscribe(@NotNull Subscriber<State> subscriber) {
    requireNonNull(subscriber);
    if (subscriber.subject() instanceof FsSubject<?> subSubject) {
      FsSubject<?> subscriberCircuit = subSubject.findCircuitAncestor();
      if (subscriberCircuit != null && subscriberCircuit != subject) {
        throw new FsException("Subscriber belongs to a different circuit");
      }
    }
    subscribers.add(subscriber);
    return new FsSubscription(
        (Subject<Subscription>) (Subject<?>) subject, () -> subscribers.remove(subscriber));
  }

  @New
  @NotNull
  @Override
  @SuppressWarnings("unchecked")
  public Reservoir<State> reservoir() {
    return new FsReservoir<>((Subject<Reservoir<State>>) (Subject<?>) subject);
  }
}
