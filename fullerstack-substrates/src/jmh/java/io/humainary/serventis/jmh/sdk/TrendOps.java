// Copyright (c) 2025 William David Louth

package io.humainary.serventis.jmh.sdk;

import io.humainary.substrates.api.Substrates;
import io.humainary.serventis.sdk.Trends;
import io.humainary.serventis.sdk.Trends.Trend;
import io.humainary.serventis.sdk.Trends.Sign;
import org.openjdk.jmh.annotations.*;

import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.openjdk.jmh.annotations.Mode.AverageTime;

///
/// Benchmark for Trends.Trend operations.
///
/// Measures performance of trend sign emissions for statistical
/// pattern detection: STABLE, DRIFT, SPIKE, CYCLE, and CHAOS.
///

@State ( Scope.Benchmark )
@BenchmarkMode ( AverageTime )
@OutputTimeUnit ( NANOSECONDS )
@Fork ( 1 )
@Warmup ( iterations = 3, time = 1 )
@Measurement ( iterations = 5, time = 1 )

public class TrendOps implements Substrates {

  private static final String TREND_NAME = "api.latency.trend";
  private static final int    BATCH_SIZE = 1000;

  private Cortex                  cortex;
  private Circuit                 circuit;
  private Conduit < Sign > conduit;
  private Trend                   trend;
  private Name                    name;

  @Benchmark
  public Trend trend_from_conduit () {

    return
      Trends.pool ( conduit ).get (
        name
      );

  }

  @Benchmark
  @OperationsPerInvocation ( BATCH_SIZE )
  public Trend trend_from_conduit_batch () {

    Trend result = null;

    for (
      var i = 0;
      i < BATCH_SIZE;
      i++
    ) {
      result =
        Trends.pool ( conduit ).get (
        name
        );
    }

    return
      result;

  }

  @Benchmark
  public void emit_stable () {

    trend.stable ();

  }

  @Benchmark
  @OperationsPerInvocation ( BATCH_SIZE )
  public void emit_stable_batch () {

    for (
      var i = 0;
      i < BATCH_SIZE;
      i++
    ) {
      trend.stable ();
    }

  }

  @Benchmark
  public void emit_drift () {

    trend.drift ();

  }

  @Benchmark
  @OperationsPerInvocation ( BATCH_SIZE )
  public void emit_drift_batch () {

    for (
      var i = 0;
      i < BATCH_SIZE;
      i++
    ) {
      trend.drift ();
    }

  }

  @Benchmark
  public void emit_spike () {

    trend.spike ();

  }

  @Benchmark
  public void emit_cycle () {

    trend.cycle ();

  }

  @Benchmark
  public void emit_chaos () {

    trend.chaos ();

  }

  @Benchmark
  public void emit_sign () {

    trend.sign (
      Sign.STABLE
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
      trend.sign (
        Sign.STABLE
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

    trend =
      Trends.pool ( conduit ).get (
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
        TREND_NAME
      );

  }

  @TearDown ( Level.Iteration )
  public void tearDownIteration () {

    circuit.close ();

  }

}
