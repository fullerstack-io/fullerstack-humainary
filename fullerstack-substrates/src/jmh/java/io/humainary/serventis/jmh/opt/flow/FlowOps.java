// Copyright (c) 2025 William David Louth

package io.humainary.serventis.jmh.opt.flow;

import io.humainary.substrates.api.Substrates;
import io.humainary.serventis.opt.flow.Flows;
import org.openjdk.jmh.annotations.*;

import static io.humainary.serventis.opt.flow.Flows.Dimension.*;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.openjdk.jmh.annotations.Mode.AverageTime;

///
/// Benchmark for Flows.Flow operations (Serventis flow instrument).
///
/// Measures performance of flow signal emissions for data movement
/// through staged systems: SUCCESS/FAIL × INGRESS/TRANSIT/EGRESS.
///

@State ( Scope.Benchmark )
@BenchmarkMode ( AverageTime )
@OutputTimeUnit ( NANOSECONDS )
@Fork ( 1 )
@Warmup ( iterations = 3, time = 1 )
@Measurement ( iterations = 5, time = 1 )

public class FlowOps implements Substrates {

  private static final String FLOW_NAME  = "data.pipeline";
  private static final int    BATCH_SIZE = 1000;

  private Cortex                               cortex;
  private Circuit                              circuit;
  private Conduit < Flows.Signal > conduit;
  private Flows.Flow                           flow;
  private Name                                 name;

  @Benchmark
  public Flows.Flow flow_from_conduit () {

    return
      io.humainary.serventis.opt.flow.Flows.pool ( conduit ).get (
        name
      );

  }

  @Benchmark
  @OperationsPerInvocation ( BATCH_SIZE )
  public Flows.Flow flow_from_conduit_batch () {

    Flows.Flow result = null;

    for (
      var i = 0;
      i < BATCH_SIZE;
      i++
    ) {
      result =
        io.humainary.serventis.opt.flow.Flows.pool ( conduit ).get (
        name
        );
    }

    return
      result;

  }

  @Benchmark
  public void emit_success_ingress () {

    flow.success ( INGRESS );

  }

  @Benchmark
  @OperationsPerInvocation ( BATCH_SIZE )
  public void emit_success_ingress_batch () {

    for (
      var i = 0;
      i < BATCH_SIZE;
      i++
    ) {
      flow.success ( INGRESS );
    }

  }

  @Benchmark
  public void emit_fail_ingress () {

    flow.fail ( INGRESS );

  }

  @Benchmark
  public void emit_success_transit () {

    flow.success ( TRANSIT );

  }

  @Benchmark
  public void emit_fail_transit () {

    flow.fail ( TRANSIT );

  }

  @Benchmark
  public void emit_success_egress () {

    flow.success ( EGRESS );

  }

  @Benchmark
  public void emit_fail_egress () {

    flow.fail ( EGRESS );

  }

  @Benchmark
  public void emit_signal () {

    flow.signal (
      Flows.Sign.SUCCESS,
      INGRESS
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
      flow.signal (
        Flows.Sign.SUCCESS,
        INGRESS
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

    flow =
      io.humainary.serventis.opt.flow.Flows.pool ( conduit ).get (
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
        FLOW_NAME
      );

  }

  @TearDown ( Level.Iteration )
  public void tearDownIteration () {

    circuit.close ();

  }

}
