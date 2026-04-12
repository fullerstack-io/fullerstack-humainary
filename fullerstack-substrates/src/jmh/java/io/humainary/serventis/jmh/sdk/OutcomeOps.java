// Copyright (c) 2025 William David Louth

package io.humainary.serventis.jmh.sdk;

import io.humainary.substrates.api.Substrates;
import io.humainary.serventis.sdk.Outcomes;
import io.humainary.serventis.sdk.Outcomes.Outcome;
import io.humainary.serventis.sdk.Outcomes.Sign;
import org.openjdk.jmh.annotations.*;

import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.openjdk.jmh.annotations.Mode.AverageTime;

///
/// Benchmark for Outcomes.Outcome binary verdict emissions.
///
/// Measures performance of SUCCESS/FAIL emissions — the universal
/// binary compression layer in the semiotic ascent.
///

@State ( Scope.Benchmark )
@BenchmarkMode ( AverageTime )
@OutputTimeUnit ( NANOSECONDS )
@Fork ( 1 )
@Warmup ( iterations = 3, time = 1 )
@Measurement ( iterations = 5, time = 1 )

public class OutcomeOps implements Substrates {

  private static final String OUTCOME_NAME = "api.call";
  private static final int    BATCH_SIZE   = 1000;

  private Cortex                    cortex;
  private Circuit                   circuit;
  private Conduit < Sign > conduit;
  private Outcome                   outcome;
  private Name                      name;

  @Benchmark
  public Outcome outcome_from_conduit () {

    return
      conduit.get (
        name
      );

  }

  @Benchmark
  @OperationsPerInvocation ( BATCH_SIZE )
  public Outcome outcome_from_conduit_batch () {

    Outcome result = null;

    for (
      var i = 0;
      i < BATCH_SIZE;
      i++
    ) {
      result =
        conduit.get (
          name
        );
    }

    return
      result;

  }

  @Benchmark
  public void emit_success () {

    outcome.success ();

  }

  @Benchmark
  @OperationsPerInvocation ( BATCH_SIZE )
  public void emit_success_batch () {

    for (
      var i = 0;
      i < BATCH_SIZE;
      i++
    ) {
      outcome.success ();
    }

  }

  @Benchmark
  public void emit_fail () {

    outcome.fail ();

  }

  @Benchmark
  @OperationsPerInvocation ( BATCH_SIZE )
  public void emit_fail_batch () {

    for (
      var i = 0;
      i < BATCH_SIZE;
      i++
    ) {
      outcome.fail ();
    }

  }

  @Benchmark
  public void emit_sign () {

    outcome.sign (
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
      outcome.sign (
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

    outcome =
      Outcomes.pool ( conduit ).get (
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
        OUTCOME_NAME
      );

  }

  @TearDown ( Level.Iteration )
  public void tearDownIteration () {

    circuit.close ();

  }

}
