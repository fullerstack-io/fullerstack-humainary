// Copyright (c) 2025 William David Louth

package io.humainary.serventis.jmh.sdk.meta;

import io.humainary.substrates.api.Substrates;
import io.humainary.serventis.opt.pool.Resources;
import io.humainary.serventis.sdk.meta.Cycles;
import io.humainary.serventis.sdk.meta.Cycles.Cycle;
import io.humainary.serventis.sdk.meta.Cycles.Signal;
import org.openjdk.jmh.annotations.*;

import static io.humainary.serventis.opt.pool.Resources.Sign.GRANT;
import static io.humainary.serventis.sdk.meta.Cycles.Dimension.*;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.openjdk.jmh.annotations.Mode.AverageTime;

///
/// Benchmark for Cycles.Cycle operations.
/// <p>
/// Measures performance of cycle signal emissions with various
/// dimensions (SINGLE, REPEAT, RETURN) using generic Sign types.
///

@State ( Scope.Benchmark )
@BenchmarkMode ( AverageTime )
@OutputTimeUnit ( NANOSECONDS )
@Fork ( 1 )
@Warmup ( iterations = 3, time = 1 )
@Measurement ( iterations = 5, time = 1 )

public class CycleOps implements Substrates {

  private static final String CYCLE_NAME = "resource.cycles";
  private static final int    BATCH_SIZE = 1000;

  private Cortex                                                          cortex;
  private Circuit                                                         circuit;
  private Conduit < Signal < Resources.Sign > > conduit;
  private Cycle < Resources.Sign >                                        cycle;
  private Name                                                            name;

  ///
  /// Benchmark cycle retrieval from conduit.
  ///

  @Benchmark
  public Cycle < Resources.Sign > cycle_from_conduit () {

    return
      Cycles.pool ( Resources.Sign.class, conduit ).get (
        name
      );

  }

  ///
  /// Benchmark batched cycle retrieval from conduit.
  ///

  @Benchmark
  @OperationsPerInvocation ( BATCH_SIZE )
  public Cycle < Resources.Sign > cycle_from_conduit_batch () {

    Cycle < Resources.Sign > result = null;

    for (
      var i = 0;
      i < BATCH_SIZE;
      i++
    ) {
      result =
        Cycles.pool ( Resources.Sign.class, conduit ).get (
        name
        );
    }

    return
      result;

  }

  ///
  /// Benchmark emitting a REPEAT dimension signal.
  ///

  @Benchmark
  public void emit_repeat () {

    cycle.signal (
      GRANT,
      REPEAT
    );

  }

  ///
  /// Benchmark batched REPEAT emissions.
  ///

  @Benchmark
  @OperationsPerInvocation ( BATCH_SIZE )
  public void emit_repeat_batch () {

    for (
      var i = 0;
      i < BATCH_SIZE;
      i++
    ) {
      cycle.signal (
        GRANT,
        REPEAT
      );
    }

  }

  ///
  /// Benchmark emitting a RETURN dimension signal.
  ///

  @Benchmark
  public void emit_return () {

    cycle.signal (
      GRANT,
      RETURN
    );

  }

  ///
  /// Benchmark batched RETURN emissions.
  ///

  @Benchmark
  @OperationsPerInvocation ( BATCH_SIZE )
  public void emit_return_batch () {

    for (
      var i = 0;
      i < BATCH_SIZE;
      i++
    ) {
      cycle.signal (
        GRANT,
        RETURN
      );
    }

  }

  ///
  /// Benchmark generic signal emission.
  ///

  @Benchmark
  public void emit_signal () {

    cycle.signal (
      GRANT,
      SINGLE
    );

  }

  ///
  /// Benchmark batched generic signal emissions.
  ///

  @Benchmark
  @OperationsPerInvocation ( BATCH_SIZE )
  public void emit_signal_batch () {

    for (
      var i = 0;
      i < BATCH_SIZE;
      i++
    ) {
      cycle.signal (
        GRANT,
        SINGLE
      );
    }

  }

  ///
  /// Benchmark emitting a SINGLE dimension signal.
  ///

  @Benchmark
  public void emit_single () {

    cycle.signal (
      GRANT,
      SINGLE
    );

  }

  ///
  /// Benchmark batched SINGLE emissions.
  ///

  @Benchmark
  @OperationsPerInvocation ( BATCH_SIZE )
  public void emit_single_batch () {

    for (
      var i = 0;
      i < BATCH_SIZE;
      i++
    ) {
      cycle.signal (
        GRANT,
        SINGLE
      );
    }

  }

  @Setup ( Level.Iteration )
  public void setupIteration () {

    circuit =
      cortex.circuit ();

    @SuppressWarnings ( "unchecked" )
    Conduit < Signal < Resources.Sign > > c =
      (Conduit < Signal < Resources.Sign > >) (Conduit < ? >) circuit.conduit (
        Signal.class
      );
    conduit = c;

    cycle =
      Cycles.pool ( Resources.Sign.class, conduit ).get (
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
        CYCLE_NAME
      );

  }

  @TearDown ( Level.Iteration )
  public void tearDownIteration () {

    circuit.close ();

  }

}
