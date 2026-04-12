// Copyright (c) 2025 William David Louth

package io.humainary.serventis.jmh.sdk;

import io.humainary.substrates.api.Substrates;
import io.humainary.serventis.sdk.Outcomes;
import io.humainary.serventis.sdk.Surveys;
import io.humainary.serventis.sdk.Surveys.Survey;
import io.humainary.serventis.sdk.Surveys.Signal;
import io.humainary.serventis.sdk.Surveys.Dimension;
import org.openjdk.jmh.annotations.*;

import static io.humainary.serventis.sdk.Surveys.Dimension.*;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.openjdk.jmh.annotations.Mode.AverageTime;

///
/// Benchmark for Surveys.Survey operations.
///
/// Measures performance of survey signal emissions for collective
/// assessment: Sign × DIVIDED/MAJORITY/UNANIMOUS.
///
/// Uses Outcomes.Sign as the generic type parameter.
///

@State ( Scope.Benchmark )
@BenchmarkMode ( AverageTime )
@OutputTimeUnit ( NANOSECONDS )
@Fork ( 1 )
@Warmup ( iterations = 3, time = 1 )
@Measurement ( iterations = 5, time = 1 )

public class SurveyOps implements Substrates {

  private static final String SURVEY_NAME = "cluster.consensus";
  private static final int    BATCH_SIZE  = 1000;

  private Cortex                                                         cortex;
  private Circuit                                                        circuit;
  private Conduit < Signal < Outcomes.Sign > > conduit;
  private Survey < Outcomes.Sign >                                       survey;
  private Name                                                           name;

  @Benchmark
  public Survey < Outcomes.Sign > survey_from_conduit () {

    return
      conduit.get (
        name
      );

  }

  @Benchmark
  @OperationsPerInvocation ( BATCH_SIZE )
  public Survey < Outcomes.Sign > survey_from_conduit_batch () {

    Survey < Outcomes.Sign > result = null;

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
  public void emit_success_unanimous () {

    survey.signal (
      Outcomes.Sign.SUCCESS,
      UNANIMOUS
    );

  }

  @Benchmark
  @OperationsPerInvocation ( BATCH_SIZE )
  public void emit_success_unanimous_batch () {

    for (
      var i = 0;
      i < BATCH_SIZE;
      i++
    ) {
      survey.signal (
        Outcomes.Sign.SUCCESS,
        UNANIMOUS
      );
    }

  }

  @Benchmark
  public void emit_fail_divided () {

    survey.signal (
      Outcomes.Sign.FAIL,
      DIVIDED
    );

  }

  @Benchmark
  public void emit_success_majority () {

    survey.signal (
      Outcomes.Sign.SUCCESS,
      MAJORITY
    );

  }

  @Benchmark
  public void emit_signal () {

    survey.signal (
      Outcomes.Sign.SUCCESS,
      UNANIMOUS
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
      survey.signal (
        Outcomes.Sign.SUCCESS,
        UNANIMOUS
      );
    }

  }

  @Setup ( Level.Iteration )
  public void setupIteration () {

    circuit =
      cortex.circuit ();

    conduit =
      circuit.conduit (
        Surveys.composer (
          Outcomes.Sign.class
        )
      );

    survey =
      conduit.get (
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
        SURVEY_NAME
      );

  }

  @TearDown ( Level.Iteration )
  public void tearDownIteration () {

    circuit.close ();

  }

}
