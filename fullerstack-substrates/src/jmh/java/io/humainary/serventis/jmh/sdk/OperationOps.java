// Copyright (c) 2025 William David Louth

package io.humainary.serventis.jmh.sdk;

import io.humainary.substrates.api.Substrates;
import io.humainary.serventis.sdk.Operations;
import io.humainary.serventis.sdk.Operations.Operation;
import io.humainary.serventis.sdk.Operations.Sign;
import org.openjdk.jmh.annotations.*;

import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.openjdk.jmh.annotations.Mode.AverageTime;

///
/// Benchmark for Operations.Operation span bracketing.
///
/// Measures performance of BEGIN/END emissions for span tracking
/// and duration measurement.
///

@State ( Scope.Benchmark )
@BenchmarkMode ( AverageTime )
@OutputTimeUnit ( NANOSECONDS )
@Fork ( 1 )
@Warmup ( iterations = 3, time = 1 )
@Measurement ( iterations = 5, time = 1 )

public class OperationOps implements Substrates {

  private static final String OP_NAME    = "db.query";
  private static final int    BATCH_SIZE = 1000;

  private Cortex                      cortex;
  private Circuit                     circuit;
  private Conduit < Sign > conduit;
  private Operation                   operation;
  private Name                        name;

  @Benchmark
  public Operation operation_from_conduit () {

    return
      conduit.get (
        name
      );

  }

  @Benchmark
  @OperationsPerInvocation ( BATCH_SIZE )
  public Operation operation_from_conduit_batch () {

    Operation result = null;

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
  public void emit_begin () {

    operation.begin ();

  }

  @Benchmark
  @OperationsPerInvocation ( BATCH_SIZE )
  public void emit_begin_batch () {

    for (
      var i = 0;
      i < BATCH_SIZE;
      i++
    ) {
      operation.begin ();
    }

  }

  @Benchmark
  public void emit_end () {

    operation.end ();

  }

  @Benchmark
  @OperationsPerInvocation ( BATCH_SIZE )
  public void emit_end_batch () {

    for (
      var i = 0;
      i < BATCH_SIZE;
      i++
    ) {
      operation.end ();
    }

  }

  @Benchmark
  public void emit_sign () {

    operation.sign (
      Sign.BEGIN
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
      operation.sign (
        Sign.BEGIN
      );
    }

  }

  @Setup ( Level.Iteration )
  public void setupIteration () {

    circuit =
      cortex.circuit ();

    conduit =
      circuit.conduit (
        Operations::composer
      );

    operation =
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
        OP_NAME
      );

  }

  @TearDown ( Level.Iteration )
  public void tearDownIteration () {

    circuit.close ();

  }

}
