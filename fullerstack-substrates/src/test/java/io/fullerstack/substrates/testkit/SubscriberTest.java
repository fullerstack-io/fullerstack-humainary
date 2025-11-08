// Copyright (c) 2025 William David Louth

package io.fullerstack.substrates.testkit;

import io.humainary.substrates.api.Substrates.Circuit;
import io.humainary.substrates.api.Substrates.Composer;
import io.humainary.substrates.api.Substrates.Cortex;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Tests to verify that the rebuild mechanism works correctly
/// when subscriptions are added and removed.
///
/// @author wlouth
/// @since 1.0

final class SubscriberTest
  extends TestSupport {

  private Cortex  cortex;
  private Circuit circuit;

  @BeforeEach
  void setup () {

    cortex = cortex ();

    circuit = cortex.circuit ();

  }

  @AfterEach
  void teardown () {

    circuit.close ();

  }

  @Test
  void testDynamicSubscription () {

    final var conduit =
      circuit.conduit (
        Composer.pipe ( Long.class )
      );

    final var counter = new AtomicInteger ( 0 );

    final var pipe =
      conduit.get (
        cortex.name ( "test" )
      );

    // Emit before subscription
    for ( int i = 0; i < 50; i++ ) {
      pipe.emit ( (long) i );
    }

    circuit.await ();

    assertEquals ( 0, counter.get () );

    // Add subscription
    conduit.subscribe (
      cortex.subscriber (
        cortex.name ( "counter" ),
        ( _, registrar ) ->
          registrar.register (
            _ -> counter.incrementAndGet ()
          )
      )
    );

    // Emit after subscription
    for ( int i = 0; i < 50; i++ ) {
      pipe.emit ( (long) i );
    }

    circuit.await ();

    assertEquals ( 50, counter.get () );

  }

  @Test
  void testEmissionAfterSubscriberRemoved () {

    final var conduit =
      circuit.conduit (
        Composer.pipe ( Long.class )
      );

    final var counter = new AtomicInteger ( 0 );

    final var subscription =
      conduit.subscribe (
        cortex.subscriber (
          cortex.name ( "counter" ),
          ( _, registrar ) ->
            registrar.register (
              _ -> counter.incrementAndGet ()
            )
        )
      );

    final var pipe =
      conduit.get (
        cortex.name ( "test" )
      );

    // Emit with subscriber
    for ( int i = 0; i < 50; i++ ) {
      pipe.emit ( (long) i );
    }

    circuit.await ();

    assertEquals ( 50, counter.get () );

    // Remove subscription
    subscription.close ();

    circuit.await ();

    // Emit after subscriber removed
    for ( int i = 0; i < 50; i++ ) {
      pipe.emit ( (long) i );
    }

    circuit.await ();

    // Counter should not have changed
    assertEquals ( 50, counter.get () );

  }

  @Test
  void testEmissionWithSubscriber () {

    final var conduit =
      circuit.conduit (
        Composer.pipe ( Long.class )
      );

    final var counter = new AtomicInteger ( 0 );

    final var subscription =
      conduit.subscribe (
        cortex.subscriber (
          cortex.name ( "counter" ),
          ( _, registrar ) ->
            registrar.register (
              _ -> counter.incrementAndGet ()
            )
        )
      );

    final var pipe =
      conduit.get (
        cortex.name ( "test" )
      );

    // Emit values with subscriber registered
    for ( int i = 0; i < 100; i++ ) {
      pipe.emit ( (long) i );
    }

    circuit.await ();

    assertEquals ( 100, counter.get () );

    subscription.close ();

  }

  @Test
  void testEmissionWithoutSubscribers () {

    final var conduit =
      circuit.conduit (
        Composer.pipe ( Long.class )
      );

    final var pipe =
      conduit.get (
        cortex.name ( "test" )
      );

    // Emit many values without any subscribers
    for ( int i = 0; i < 1000; i++ ) {
      pipe.emit ( (long) i );
    }

    circuit.await ();

    // No assertions needed - just verify no exceptions

  }

  @Test
  void testMultipleSubscribers () {

    final var conduit =
      circuit.conduit (
        Composer.pipe ( Long.class )
      );

    final var counter1 = new AtomicInteger ( 0 );
    final var counter2 = new AtomicInteger ( 0 );

    conduit.subscribe (
      cortex.subscriber (
        cortex.name ( "counter1" ),
        ( _, registrar ) ->
          registrar.register (
            _ -> counter1.incrementAndGet ()
          )
      )
    );

    conduit.subscribe (
      cortex.subscriber (
        cortex.name ( "counter2" ),
        ( _, registrar ) ->
          registrar.register (
            _ -> counter2.incrementAndGet ()
          )
      )
    );

    final var pipe =
      conduit.get (
        cortex.name ( "test" )
      );

    // Emit values - both subscribers should receive
    for ( int i = 0; i < 100; i++ ) {
      pipe.emit ( (long) i );
    }

    circuit.await ();

    assertEquals ( 100, counter1.get () );
    assertEquals ( 100, counter2.get () );

  }

}
