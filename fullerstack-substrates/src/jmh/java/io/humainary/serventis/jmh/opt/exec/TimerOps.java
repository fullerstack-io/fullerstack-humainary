// Copyright (c) 2025 William David Louth

package io.humainary.serventis.jmh.opt.exec;

import io.humainary.substrates.api.Substrates;
import io.humainary.serventis.opt.exec.Timers;
import io.humainary.serventis.opt.exec.Timers.Timer;
import io.humainary.serventis.opt.exec.Timers.Signal;
import org.openjdk.jmh.annotations.*;

import static io.humainary.serventis.opt.exec.Timers.Dimension.*;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.openjdk.jmh.annotations.Mode.AverageTime;

///
/// Benchmark for Timers.Timer operations.
///
/// Measures performance of timer signal emissions for time constraint
/// outcomes: MEET/MISS × DEADLINE/THRESHOLD.
///

@State ( Scope.Benchmark )
@BenchmarkMode ( AverageTime )
@OutputTimeUnit ( NANOSECONDS )
@Fork ( 1 )
@Warmup ( iterations = 3, time = 1 )
@Measurement ( iterations = 5, time = 1 )

public class TimerOps implements Substrates {

  private static final String TIMER_NAME = "api.latency";
  private static final int    BATCH_SIZE = 1000;

  private Cortex                    cortex;
  private Circuit                   circuit;
  private Conduit < Signal > conduit;
  private Timer                     timer;
  private Name                      name;

  @Benchmark
  public Timer timer_from_conduit () {

    return
      Timers.pool ( conduit ).get (
        name
      );

  }

  @Benchmark
  @OperationsPerInvocation ( BATCH_SIZE )
  public Timer timer_from_conduit_batch () {

    Timer result = null;

    for (
      var i = 0;
      i < BATCH_SIZE;
      i++
    ) {
      result =
        Timers.pool ( conduit ).get (
        name
        );
    }

    return
      result;

  }

  @Benchmark
  public void emit_meet_deadline () {

    timer.meet ( DEADLINE );

  }

  @Benchmark
  @OperationsPerInvocation ( BATCH_SIZE )
  public void emit_meet_deadline_batch () {

    for (
      var i = 0;
      i < BATCH_SIZE;
      i++
    ) {
      timer.meet ( DEADLINE );
    }

  }

  @Benchmark
  public void emit_miss_deadline () {

    timer.miss ( DEADLINE );

  }

  @Benchmark
  public void emit_meet_threshold () {

    timer.meet ( THRESHOLD );

  }

  @Benchmark
  public void emit_miss_threshold () {

    timer.miss ( THRESHOLD );

  }

  @Benchmark
  public void emit_signal () {

    timer.signal (
      Timers.Sign.MEET,
      THRESHOLD
    );

  }

  @Benchmark
  @OperationsPerInvocation ( BATCH_SIZE )
  public void emit_signal_batch () {

    for (
      var i = 0;
      i < BATCH_SIZE;
      i++
    ) {
      timer.signal (
        Timers.Sign.MEET,
        THRESHOLD
      );
    }

  }

  @Setup ( Level.Iteration )
  public void setupIteration () {

    circuit =
      cortex.circuit ();

    conduit =
      circuit.conduit (
        Signal.class
      );

    timer =
      Timers.pool ( conduit ).get (
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
        TIMER_NAME
      );

  }

  @TearDown ( Level.Iteration )
  public void tearDownIteration () {

    circuit.close ();

  }

}
