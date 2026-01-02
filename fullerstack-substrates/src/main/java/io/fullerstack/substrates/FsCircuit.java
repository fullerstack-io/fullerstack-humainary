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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BiConsumer;

import org.jctools.queues.MpscLinkedQueue;

/**
 * Circuit implementation using dual-queue architecture with JCTools MpscLinkedQueue.
 *
 * <p>Key design:
 * <ul>
 *   <li>Ingress queue (MpscLinkedQueue): external emissions from multiple threads
 *   <li>Transit queue (ArrayDeque): cascade emissions on circuit thread
 *   <li>Priority: drain transit completely, then process ingress with transit drain after each
 *   <li>Park/unpark for efficient thread synchronization
 * </ul>
 */
@Provided
public final class FsCircuit implements Circuit {

  /** Batch size for ingress drain operations. */
  private static final int DRAIN_BATCH = 64;

  /** JCTools MPSC linked queue for external submissions (ingress). */
  private final MpscLinkedQueue < Job > ingress = new MpscLinkedQueue <> ();

  /** Simple queue for cascade emissions on circuit thread (transit). */
  private final ArrayDeque < Job > transit = new java.util.ArrayDeque <> ();

  // ===== SHARED STATE (read by producers, written by consumer) =====
  private volatile boolean running = true;  // Volatile for visibility across threads
  private volatile boolean parked  = false; // True only when thread is parked

  // ===== IMMUTABLE FIELDS =====
  private final Subject < Circuit >           subject;
  private final Thread                        thread;
  private final List < Subscriber < State > > subscribers = new ArrayList <> ();

  public FsCircuit ( Subject < Circuit > subject ) {
    this.subject = subject;
    // Start thread eagerly
    this.thread = Thread.ofVirtual ().name ( "circuit-" + subject.name () ).start ( this::loop );
  }

  /**
   * Submit a job to the circuit.
   */
  public void submit ( Job job ) {
    if ( Thread.currentThread () == thread ) {
      // Hot path: cascade - add to transit queue (processed before next ingress job)
      if ( !running ) return;
      transit.addLast ( job );
    } else {
      // Cold path: external submission via JCTools MPSC queue
      submitExternal ( job );
    }
  }

  /** Cold path: external submission via JCTools MPSC queue. */
  private void submitExternal ( Job job ) {
    if ( !running ) return;
    ingress.offer ( job );
    // Only unpark if thread is actually parked
    if ( parked ) {
      LockSupport.unpark ( thread );
    }
  }

  /** Returns true if the current thread is the circuit's processing thread. */
  public boolean isCircuitThread () {
    return Thread.currentThread () == thread;
  }

  /** Returns true if the circuit is still running. */
  public boolean isRunning () {
    return running;
  }

  /** Returns the circuit's thread for direct comparison. */
  public Thread thread () {
    return thread;
  }

  /** Spin iterations before parking (0 = park immediately). */
  private static final int SPIN_LIMIT = Integer.getInteger ( "io.fullerstack.substrates.spinLimit", 512 );

  /**
   * Core execution loop - drains transit (priority), then ingress with transit drain after each.
   */
  private void loop () {
    int spins = 0;

    for ( ; ; ) {
      // Priority 1: Drain transit queue completely (cascading emissions)
      if ( drainTransit () ) {
        spins = 0;
        continue;
      }

      // Priority 2: Drain batch from ingress, with transit drain after each job
      if ( drainIngress () ) {
        spins = 0;
        continue;
      }

      // Check for shutdown (both queues empty)
      if ( !running && ingress.isEmpty () && transit.isEmpty () ) {
        break;
      }

      // Spin before parking
      if ( spins < SPIN_LIMIT ) {
        spins++;
        Thread.onSpinWait ();
      } else {
        // Set parked flag before parking so external submitters know to unpark
        parked = true;
        if ( ingress.isEmpty () && transit.isEmpty () ) {
          LockSupport.park ();
        }
        parked = false;
        spins = 0;
      }
    }
  }

  /**
   * Drain transit queue completely. Returns true if any work was done.
   */
  private boolean drainTransit () {
    if ( transit.isEmpty () ) {
      return false;
    }
    while ( !transit.isEmpty () ) {
      Job job = transit.pollFirst ();
      try {
        job.run (); // May add more to transit
      } catch ( Exception ignored ) { }
    }
    return true;
  }

  /**
   * Drain batch from ingress, with transit drain after each job for causality.
   * Returns true if any work was done.
   */
  private boolean drainIngress () {
    Job job = ingress.relaxedPoll ();
    if ( job == null ) {
      return false;
    }

    int count = 0;
    do {
      try {
        job.run (); // May add to transit
      } catch ( Exception ignored ) { }

      // Drain transit after each ingress job (causality preservation)
      drainTransit ();

      count++;
      if ( count >= DRAIN_BATCH ) {
        break;
      }
      job = ingress.relaxedPoll ();
    } while ( job != null );
    return true;
  }

  @Override
  public Subject < Circuit > subject () {
    return subject;
  }

  @Override
  public void await () {
    if ( Thread.currentThread () == thread ) {
      throw new IllegalStateException ( "Cannot call Circuit::await from within a circuit's thread" );
    }
    if ( !running ) {
      try {
        thread.join ();
      } catch ( InterruptedException e ) {
        Thread.currentThread ().interrupt ();
      }
      return;
    }
    var latch = new CountDownLatch ( 1 );
    submit ( new EmitJob ( ignored -> latch.countDown (), null ) );
    try {
      latch.await ();
    } catch ( InterruptedException e ) {
      Thread.currentThread ().interrupt ();
    }
  }

  @Idempotent
  @Override
  public void close () {
    running = false;
    LockSupport.unpark ( thread );
  }

  @New
  @NotNull
  @Override
  public < I, E > Cell < I, E > cell ( @NotNull Name name, @NotNull Composer < E, Pipe < I > > ingress, @NotNull Composer < E, Pipe < E > > egress, @NotNull Receptor < ? super E > receptor ) {
    requireNonNull ( name );
    requireNonNull ( ingress );
    requireNonNull ( egress );
    requireNonNull ( receptor );
    return new FsCell <> ( new FsSubject <> ( name, (FsSubject < ? >) subject, Cell.class ), this, ingress, egress, receptor );
  }

  @New
  @NotNull
  @Override
  public < P extends Percept, E > Conduit < P, E > conduit ( @NotNull Name name, @NotNull Composer < E, ? extends P > composer ) {
    requireNonNull ( name );
    requireNonNull ( composer );
    return new FsConduit <> ( (FsSubject < ? >) subject, name, channel -> composer.compose ( channel ), this );
  }

  @New
  @NotNull
  @Override
  public < P extends Percept, E > Conduit < P, E > conduit ( @NotNull Name name, @NotNull Composer < E, ? extends P > composer, @NotNull Configurer < Flow < E > > configurer ) {
    requireNonNull ( name );
    requireNonNull ( composer );
    requireNonNull ( configurer );
    return new FsConduit <> ( (FsSubject < ? >) subject, name, channel -> composer.compose ( channel ), this, configurer );
  }

  @New
  @NotNull
  @Override
  public < E > Pipe < E > pipe ( @NotNull Pipe < E > target ) {
    var fsPipe = (FsPipe < E >) requireNonNull ( target );
    return new FsPipe <> ( fsPipe.subject (), this, fsPipe.receiver () );
  }

  @New
  @NotNull
  @Override
  public < E > Pipe < E > pipe ( @NotNull Receptor < E > receptor ) {
    Subject < Pipe < E > > pipeSubject = new FsSubject <> ( null, (FsSubject < ? >) subject, Pipe.class );
    return new FsPipe <> ( pipeSubject, this, receptor::receive );
  }

  @New
  @NotNull
  @Override
  public < E > Pipe < E > pipe ( @NotNull Pipe < E > target, @NotNull Configurer < Flow < E > > configurer ) {
    var fp = (FsPipe < E >) requireNonNull ( target );
    var fsPipe = new FsPipe <> ( fp.subject (), this, fp.receiver () );
    var flow = new FsFlow <> ( fp.subject (), this, fsPipe );
    configurer.configure ( flow );
    return flow.pipe ();
  }

  @New
  @NotNull
  @Override
  public < E > Pipe < E > pipe ( @NotNull Receptor < E > receptor, @NotNull Configurer < Flow < E > > configurer ) {
    Subject < Pipe < E > > pipeSubject = new FsSubject <> ( null, (FsSubject < ? >) subject, Pipe.class );
    FsPipe < E > fsPipe = new FsPipe <> ( pipeSubject, this, receptor::receive );
    FsFlow < E > flow = new FsFlow <> ( pipeSubject, this, fsPipe );
    configurer.configure ( flow );
    return flow.pipe ();
  }

  @New
  @NotNull
  @Override
  public < E > Pipe < E > pipe ( Name name, @NotNull Pipe < E > target ) {
    var fsPipe = (FsPipe < E >) requireNonNull ( target );
    return new FsPipe <> ( fsPipe.subject (), this, fsPipe.receiver () );
  }

  @New
  @NotNull
  @Override
  public < E > Pipe < E > pipe ( Name name, @NotNull Receptor < E > receptor ) {
    Subject < Pipe < E > > pipeSubject = new FsSubject <> ( name, (FsSubject < ? >) subject, Pipe.class );
    return new FsPipe <> ( pipeSubject, this, receptor::receive );
  }

  @New
  @NotNull
  @Override
  public < E > Pipe < E > pipe ( Name name, @NotNull Pipe < E > target, @NotNull Configurer < Flow < E > > configurer ) {
    var fp = (FsPipe < E >) requireNonNull ( target );
    var fsPipe = new FsPipe <> ( fp.subject (), this, fp.receiver () );
    var flow = new FsFlow <> ( fp.subject (), this, fsPipe );
    configurer.configure ( flow );
    return flow.pipe ();
  }

  @New
  @NotNull
  @Override
  public < E > Pipe < E > pipe ( Name name, @NotNull Receptor < E > receptor, @NotNull Configurer < Flow < E > > configurer ) {
    Subject < Pipe < E > > pipeSubject = new FsSubject <> ( name, (FsSubject < ? >) subject, Pipe.class );
    FsPipe < E > fsPipe = new FsPipe <> ( pipeSubject, this, receptor::receive );
    FsFlow < E > flow = new FsFlow <> ( pipeSubject, this, fsPipe );
    configurer.configure ( flow );
    return flow.pipe ();
  }

  @New
  @NotNull
  @Override
  public < E > Subscriber < E > subscriber ( @NotNull Name name, @NotNull BiConsumer < Subject < Channel < E > >, Registrar < E > > callback ) {
    requireNonNull ( name );
    requireNonNull ( callback );
    return new FsSubscriber <> ( new FsSubject <> ( name, (FsSubject < ? >) subject, Subscriber.class ), callback );
  }

  @New
  @NotNull
  @Override
  @SuppressWarnings ( "unchecked" )
  public Subscription subscribe ( @NotNull Subscriber < State > subscriber ) {
    requireNonNull ( subscriber );
    if ( subscriber.subject () instanceof FsSubject < ? > subSubject ) {
      FsSubject < ? > subscriberCircuit = subSubject.findCircuitAncestor ();
      if ( subscriberCircuit != null && subscriberCircuit != subject ) {
        throw new FsException ( "Subscriber belongs to a different circuit" );
      }
    }
    subscribers.add ( subscriber );
    return new FsSubscription ( (Subject < Subscription >) (Subject < ? >) subject, () -> subscribers.remove ( subscriber ) );
  }

  @New
  @NotNull
  @Override
  @SuppressWarnings ( "unchecked" )
  public Reservoir < State > reservoir () {
    return new FsReservoir <> ( (Subject < Reservoir < State > >) (Subject < ? >) subject );
  }
}
