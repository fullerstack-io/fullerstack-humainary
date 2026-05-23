package io.fullerstack.substrates;

import io.humainary.substrates.api.Substrates.Idempotent;
import io.humainary.substrates.api.Substrates.Name;
import io.humainary.substrates.api.Substrates.Pipe;
import io.humainary.substrates.api.Substrates.Provided;
import io.humainary.substrates.api.Substrates.Queued;
import io.humainary.substrates.api.Substrates.Subject;
import io.humainary.substrates.api.Substrates.Tenure;
import io.humainary.substrates.api.Substrates.Ticker;

import java.time.Duration;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/// Periodic emitter (Substrates 2.8, SPEC §11.5).
///
/// Emits monotonic gap-free `Long` sequence numbers (0, 1, 2, …) into the
/// target pipe at a fixed-rate cadence. Scheduling is anchored to a grid
/// (`anchor + (n+1) * interval`) so late ticks do not shift subsequent
/// grid points. A stall of more than one interval re-anchors the grid
/// (no burst of catch-up ticks), but the sequence remains gap-free.
///
/// Scheduling runs on the circuit's shared [java.util.concurrent.ScheduledExecutorService].
/// Each tick calls `target.emit(seq)` which goes through normal pipe
/// routing — same-circuit targets land on transit, cross-circuit on ingress.
@Provided
@Tenure ( Tenure.ANCHORED )
final class FsTicker implements Ticker {

  private final Subject < Ticker >    subject;
  private final FsCircuit             circuit;
  private final Pipe < ? super Long > target;
  private final long                  intervalNanos;

  /// Volatile because close() may read it from any thread while the
  /// scheduler thread reschedules.
  private volatile long anchorNanos;

  /// Monotonic gap-free sequence. Incremented on the scheduler thread only;
  /// no contention.
  private long seq;

  /// Current scheduled future — cancelled on close(). Volatile so close()
  /// observes the latest pending tick.
  private volatile ScheduledFuture < ? > pending;
  private volatile boolean               closed;

  @SuppressWarnings ( "unchecked" )
  FsTicker ( FsSubject < ? > parent, Name name, FsCircuit circuit,
             Duration interval, Pipe < ? super Long > target ) {
    FsSubject < ? > s = new FsSubject <> ( name, parent, Ticker.class );
    this.subject       = (Subject < Ticker >) (Subject < ? >) s;
    this.circuit       = circuit;
    this.target        = target;
    this.intervalNanos = interval.toNanos ();
    this.anchorNanos   = System.nanoTime ();
    this.seq           = -1L;
    schedule ( intervalNanos );
  }

  private void schedule ( long delayNanos ) {
    if ( closed ) return;
    if ( delayNanos < 0L ) delayNanos = 0L;
    pending = circuit.scheduler ().schedule ( this::tick, delayNanos, TimeUnit.NANOSECONDS );
  }

  private void tick () {
    if ( closed || circuit.isClosed () ) return;
    final long s = ++seq;
    try {
      target.emit ( s );
    } catch ( Throwable ignored ) {
      // SPEC §15.4 — external callback failure isolated. Ticker keeps ticking.
    }
    if ( closed ) return;
    // Re-anchor only if more than one interval behind the grid (SPEC §11.5
    // "bounded catch-up"); otherwise shorten the next wait to compensate.
    final long now      = System.nanoTime ();
    final long nextGrid = anchorNanos + ( s + 2L ) * intervalNanos;
    long       delay    = nextGrid - now;
    if ( delay < -intervalNanos ) {
      anchorNanos = now;
      delay       = intervalNanos;
    }
    schedule ( delay );
  }

  @Override
  public Subject < Ticker > subject () {
    return subject;
  }

  @Override
  @Idempotent
  @Queued
  public void close () {
    if ( closed ) return;
    closed = true;
    final ScheduledFuture < ? > f = pending;
    if ( f != null ) f.cancel ( false );
  }

  @Override
  @Idempotent
  public void closeAwait () {
    circuit.checkExternalCaller ( "closeAwait" );
    close ();
    circuit.await ();
  }
}
