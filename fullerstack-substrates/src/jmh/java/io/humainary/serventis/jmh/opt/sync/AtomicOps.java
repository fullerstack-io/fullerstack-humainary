// Copyright (c) 2025 William David Louth

package io.humainary.serventis.jmh.opt.sync;

import io.humainary.substrates.api.Substrates;
import io.humainary.serventis.opt.sync.Atomics;
import io.humainary.serventis.opt.sync.Atomics.Atomic;
import io.humainary.serventis.opt.sync.Atomics.Sign;
import org.openjdk.jmh.annotations.*;

import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.openjdk.jmh.annotations.Mode.AverageTime;

///
/// Benchmark for Atomics.Atomic operations.
///
/// Measures performance of atomic CAS operation sign emissions:
/// ATTEMPT, SUCCESS, FAIL, SPIN, YIELD, BACKOFF, PARK, and EXHAUST.
///

@State ( Scope.Benchmark )
@BenchmarkMode ( AverageTime )
@OutputTimeUnit ( NANOSECONDS )
@Fork ( 1 )
@Warmup ( iterations = 3, time = 1 )
@Measurement ( iterations = 5, time = 1 )

public class AtomicOps implements Substrates {

  private static final String ATOMIC_NAME = "cas.counter";
  private static final int    BATCH_SIZE  = 1000;

  private Cortex                   cortex;
  private Circuit                  circuit;
  private Conduit < Sign > conduit;
  private Atomic                   atomic;
  private Name                     name;

  @Benchmark
  public Atomic atomic_from_conduit () {

    return
      Atomics.pool ( conduit ).get (
        name
      );

  }

  @Benchmark
  @OperationsPerInvocation ( BATCH_SIZE )
  public Atomic atomic_from_conduit_batch () {

    Atomic result = null;

    for (
      var i = 0;
      i < BATCH_SIZE;
      i++
    ) {
      result =
        Atomics.pool ( conduit ).get (
        name
        );
    }

    return
      result;

  }

  @Benchmark
  public void emit_attempt () {

    atomic.attempt ();

  }

  @Benchmark
  @OperationsPerInvocation ( BATCH_SIZE )
  public void emit_attempt_batch () {

    for (
      var i = 0;
      i < BATCH_SIZE;
      i++
    ) {
      atomic.attempt ();
    }

  }

  @Benchmark
  public void emit_success () {

    atomic.success ();

  }

  @Benchmark
  @OperationsPerInvocation ( BATCH_SIZE )
  public void emit_success_batch () {

    for (
      var i = 0;
      i < BATCH_SIZE;
      i++
    ) {
      atomic.success ();
    }

  }

  @Benchmark
  public void emit_fail () {

    atomic.fail ();

  }

  @Benchmark
  @OperationsPerInvocation ( BATCH_SIZE )
  public void emit_fail_batch () {

    for (
      var i = 0;
      i < BATCH_SIZE;
      i++
    ) {
      atomic.fail ();
    }

  }

  @Benchmark
  public void emit_spin () {

    atomic.spin ();

  }

  @Benchmark
  @OperationsPerInvocation ( BATCH_SIZE )
  public void emit_spin_batch () {

    for (
      var i = 0;
      i < BATCH_SIZE;
      i++
    ) {
      atomic.spin ();
    }

  }

  @Benchmark
  public void emit_backoff () {

    atomic.backoff ();

  }

  @Benchmark
  public void emit_park () {

    atomic.park ();

  }

  @Benchmark
  public void emit_exhaust () {

    atomic.exhaust ();

  }

  @Benchmark
  public void emit_sign () {

    atomic.sign (
      Sign.SUCCESS
    );

  }

  @Benchmark
  @OperationsPerInvocation ( BATCH_SIZE )
  public void emit_sign_batch () {

    for (
      var i = 0;
      i < BATCH_SIZE;
      i++
    ) {
      atomic.sign (
        Sign.SUCCESS
      );
    }

  }

  @Setup ( Level.Iteration )
  public void setupIteration () {

    circuit =
      cortex.circuit ();

    conduit =
      circuit.conduit (
        Sign.class
      );

    atomic =
      Atomics.pool ( conduit ).get (
        name
      );

    circuit.await ();

  }

  @Setup ( Level.Trial )
  public void setupTrial () {

    cortex =
      Substrates.cortex ();

    name =
      cortex.name (
        ATOMIC_NAME
      );

  }

  @TearDown ( Level.Iteration )
  public void tearDownIteration () {

    circuit.close ();

  }

}
