// Copyright (c) 2025 William David Louth

package io.humainary.substrates.jmh;

import io.humainary.substrates.api.Substrates;
import org.openjdk.jmh.annotations.*;

import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.openjdk.jmh.annotations.Level.Iteration;
import static org.openjdk.jmh.annotations.Level.Trial;
import static org.openjdk.jmh.annotations.Mode.AverageTime;

///
/// Benchmark for cyclic emission patterns.
///
/// Measures the performance of self-reinforcing emission cycles where a subscriber
/// re-registers the same pipe on each emission, creating a feedback loop that
/// continues until a limit is reached via flow.limit().
///
/// This benchmark tests:
/// - Transit queue priority behavior (cascading emissions complete before next external input)
/// - Stack safety for deeply cascading chains (queue-based, not recursive)
/// - Neural-like signal propagation dynamics
///

@State ( Scope.Benchmark )
@BenchmarkMode ( AverageTime )
@OutputTimeUnit ( NANOSECONDS )
@Fork ( 1 )
@Warmup ( iterations = 3, time = 1 )
@Measurement ( iterations = 5, time = 1 )

public class CyclicOps
  implements Substrates {

  private static final int CYCLE_LIMIT = 1000;
  private static final int BATCH_SIZE  = 1000;

  private Cortex          cortex;
  private Name            pipesName;
  private Name            cyclicName;
  private Circuit         circuit;
  private Flow < Integer, Integer > limitFlow;
  private Flow < Integer, Integer > deepLimitFlow;

  ///
  /// Benchmark cyclic emission setup and trigger (no await).
  ///

  @Benchmark
  @OperationsPerInvocation ( CYCLE_LIMIT )
  public void cyclic_emit () {

    final var conduit = circuit.conduit ( Integer.class );

    conduit.subscribe (
      circuit.subscriber (
        pipesName,
        ( subject, registrar ) ->
          registrar.register (
            conduit.get ( subject ).pipe ( limitFlow )
          )
      )
    );

    conduit.get ( cyclicName ).emit ( 0 );

  }

  ///
  /// Benchmark full cyclic emission chain with await.
  ///

  @Benchmark
  @OperationsPerInvocation ( CYCLE_LIMIT )
  public void cyclic_emit_await () {

    final var conduit = circuit.conduit ( Integer.class );

    conduit.subscribe (
      circuit.subscriber (
        pipesName,
        ( subject, registrar ) ->
          registrar.register (
            conduit.get ( subject ).pipe ( limitFlow )
          )
      )
    );

    conduit.get ( cyclicName ).emit ( 0 );
    circuit.await ();

  }

  ///
  /// Benchmark deep cyclic emission chain with await (10x limit).
  ///

  @Benchmark
  @OperationsPerInvocation ( CYCLE_LIMIT * 10 )
  public void cyclic_emit_deep_await () {

    final var conduit = circuit.conduit ( Integer.class );

    conduit.subscribe (
      circuit.subscriber (
        pipesName,
        ( subject, registrar ) ->
          registrar.register (
            conduit.get ( subject ).pipe ( deepLimitFlow )
          )
      )
    );

    conduit.get ( cyclicName ).emit ( 0 );
    circuit.await ();

  }

  ///
  /// Batch cyclic emission (no await).
  ///

  @Benchmark
  @OperationsPerInvocation ( BATCH_SIZE * CYCLE_LIMIT )
  public void cyclic_emit_batch () {

    for ( int i = 0; i < BATCH_SIZE; i++ ) {

      final var conduit = circuit.conduit ( Integer.class );

      conduit.subscribe (
        circuit.subscriber (
          pipesName,
          ( subject, registrar ) ->
            registrar.register (
              conduit.get ( subject ).pipe ( limitFlow )
            )
        )
      );

      conduit.get ( cyclicName ).emit ( 0 );

    }

  }

  ///
  /// Batch cyclic emission with await.
  ///

  @Benchmark
  @OperationsPerInvocation ( BATCH_SIZE * CYCLE_LIMIT )
  public void cyclic_emit_await_batch () {

    for ( int i = 0; i < BATCH_SIZE; i++ ) {

      final var conduit = circuit.conduit ( Integer.class );

      conduit.subscribe (
        circuit.subscriber (
          pipesName,
          ( subject, registrar ) ->
            registrar.register (
              conduit.get ( subject ).pipe ( limitFlow )
            )
        )
      );

      conduit.get ( cyclicName ).emit ( 0 );
      circuit.await ();

    }

  }

  ///
  /// Batch deep cyclic emission with await (10x limit).
  ///

  @Benchmark
  @OperationsPerInvocation ( BATCH_SIZE * CYCLE_LIMIT * 10 )
  public void cyclic_emit_deep_await_batch () {

    for ( int i = 0; i < BATCH_SIZE; i++ ) {

      final var conduit = circuit.conduit ( Integer.class );

      conduit.subscribe (
        circuit.subscriber (
          pipesName,
          ( subject, registrar ) ->
            registrar.register (
              conduit.get ( subject ).pipe ( deepLimitFlow )
            )
        )
      );

      conduit.get ( cyclicName ).emit ( 0 );
      circuit.await ();

    }

  }

  @Setup ( Iteration )
  public void setupIteration () {

    circuit =
      cortex.circuit ();

  }

  @Setup ( Trial )
  public void setupTrial () {

    cortex =
      Substrates.cortex ();

    pipesName =
      cortex.name ( "pipes" );

    cyclicName =
      cortex.name ( "cyclic" );

    limitFlow =
      cortex.flow ( Integer.class ).limit ( CYCLE_LIMIT );

    deepLimitFlow =
      cortex.flow ( Integer.class ).limit ( CYCLE_LIMIT * 10 );

  }

  @TearDown ( Iteration )
  public void tearDownIteration () {

    circuit.await ();
    circuit.close ();

  }

}
