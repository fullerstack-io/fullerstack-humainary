// Copyright (c) 2025 William David Louth

package io.fullerstack.substrates.testkit;

import io.humainary.substrates.api.Substrates.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.*;

final class FlowTest
  extends TestSupport {

  private Cortex cortex;

  @BeforeEach
  void setup () {

    cortex = cortex ();

  }

  @Test
  void testDiffAndGuardOperators () {

    final var circuit = cortex.circuit ();

    try {

      final List < Integer > forwarded = new ArrayList <> ();

      // RC3: Flow.forward() takes Pipe<? super E >, not Consumer< E >
      final Pipe < Integer > forwardPipe = new Pipe <> () {
        @Override
        public void emit ( Integer value ) {
          forwarded.add ( value );
        }

        @Override
        public void flush () { }
      };

      final Conduit < Pipe < Integer >, Integer > conduit =
        circuit.conduit (
          cortex.name ( "flow.diff.conduit" ),
          channel -> channel.pipe (
            flow -> flow
              .diff ()
              .diff ( 0 )
              .guard ( value -> ( value & 1 ) == 0 )
              .guard ( 0, ( previous, next ) -> next > previous )
              .forward ( forwardPipe )
          )
        );

      final Sink < Integer > sink = cortex.sink ( conduit );

      final Pipe < Integer > pipe =
        conduit.get ( cortex.name ( "flow.diff.channel" ) );

      pipe.emit ( 2 );
      pipe.emit ( 2 ); // filtered by diff()
      pipe.emit ( 4 );
      pipe.emit ( 3 ); // filtered by guard even
      pipe.emit ( 6 );
      pipe.emit ( 5 ); // filtered by guard even
      pipe.emit ( 8 );

      circuit.await ();

      final List < Integer > drained =
        sink.drain ()
          .map ( Capture::emission )
          .collect ( toList () );

      assertEquals (
        List.of ( 2, 4, 6, 8 ),
        forwarded
      );

      assertEquals (
        forwarded,
        drained
      );

      sink.close ();

    } finally {

      circuit.close ();

    }

  }

  @Test
  void testLimitSamplePeekForwardReduceReplace () {

    final var circuit = cortex.circuit ();

    try {

      final var frequencyCount = new AtomicInteger ();
      final var rateCount = new AtomicInteger ();
      final var reduceCapture = new AtomicInteger ();

      final List < Integer > forwarded = new ArrayList <> ();

      // RC3: Flow.forward() takes Pipe<? super E >, not Consumer< E >
      final Pipe < Integer > forwardPipe = new Pipe <> () {
        @Override
        public void emit ( Integer value ) {
          forwarded.add ( value );
        }

        @Override
        public void flush () { }
      };

      final Conduit < Pipe < Integer >, Integer > conduit =
        circuit.conduit (
          cortex.name ( "flow.limit.conduit" ),
          channel -> channel.pipe (
            flow -> flow
              .sample ( 2 )
              .peek ( ignored -> frequencyCount.incrementAndGet () )
              .sample ( 0.5 )
              .peek ( ignored -> rateCount.incrementAndGet () )
              .forward ( forwardPipe )
              .limit ( 10 )
              .limit ( 3L )
              .reduce ( 0, Integer::sum )
              .peek ( reduceCapture::set )
              .replace ( value -> value + 100 )
          )
        );

      final Sink < Integer > sink = cortex.sink ( conduit );

      final Pipe < Integer > pipe =
        conduit.get ( cortex.name ( "flow.limit.channel" ) );

      for ( int i = 1; i <= 200; i++ ) {
        pipe.emit ( i );
      }

      circuit.await ();

      final List < Integer > finalValues =
        sink.drain ()
          .map ( Capture::emission )
          .toList ();

      assertEquals ( 100, frequencyCount.get (), "Frequency filter should pass half the emissions" );

      assertTrue (
        rateCount.get () > 0,
        "Rate filter should allow at least one emission"
      );

      assertTrue (
        rateCount.get () <= frequencyCount.get (),
        "Rate filter cannot allow more emissions than the frequency filter"
      );

      assertEquals (
        rateCount.get (),
        forwarded.size (),
        "Forward operator should observe every emission that passed the rate filter"
      );

      assertFalse ( finalValues.isEmpty (), "Limiter should still allow some emissions through" );
      assertTrue ( finalValues.size () <= 3, "Long limiter restricts the downstream emissions" );
      assertTrue (
        forwarded.size () >= finalValues.size (),
        "Limiters can only decrease the number of emissions"
      );

      assertEquals (
        reduceCapture.get () + 100,
        finalValues.getLast (),
        "Replacement happens after reduce execution"
      );

      sink.close ();

    } finally {

      circuit.close ();

    }

  }

  @Test
  void testSiftOperators () {

    final var circuit = cortex.circuit ();

    try {

      final List < Integer > preLow = new ArrayList <> ();

      final Conduit < Pipe < Integer >, Integer > conduit =
        circuit.conduit (
          cortex.name ( "flow.sift.conduit" ),
          channel -> channel.pipe (
            flow -> flow
              .sift (
                Integer::compareTo,
                Sift::high
              )
              .sift (
                Integer::compareTo,
                sift -> sift.min ( 2 )
              )
              .sift (
                Integer::compareTo,
                sift -> sift.max ( 8 )
              )
              .sift (
                Integer::compareTo,
                sift -> sift.range ( 3, 7 )
              )
              .sift (
                Integer::compareTo,
                sift -> sift.above ( 4 )
              )
              .sift (
                Integer::compareTo,
                sift -> sift.below ( 7 )
              )
              .peek ( preLow::add )
              .sift (
                Integer::compareTo,
                Sift::low
              )
          )
        );

      final Sink < Integer > sink = cortex.sink ( conduit );

      final Pipe < Integer > pipe =
        conduit.get ( cortex.name ( "flow.sift.channel" ) );

      final int[] emissions = {1, 2, 2, 3, 4, 5, 6, 7, 8, 9};

      for ( final int emission : emissions ) {
        pipe.emit ( emission );
      }

      circuit.await ();

      assertEquals (
        List.of ( 5, 6 ),
        preLow,
        "Peek should observe the values surviving the preceding filters"
      );

      final List < Integer > finalValues =
        sink.drain ()
          .map ( Capture::emission )
          .collect ( toList () );

      assertEquals (
        List.of ( 5 ),
        finalValues
      );

      sink.close ();

    } finally {

      circuit.close ();

    }

  }

  @Test
  void testSkipOperator () {

    final var circuit = cortex.circuit ();

    try {

      final List < Integer > captured = new ArrayList <> ();

      // RC3: Flow.forward() takes Pipe<? super E >, not Consumer< E >
      final Pipe < Integer > forwardPipe = new Pipe <> () {
        @Override
        public void emit ( Integer value ) {
          captured.add ( value );
        }

        @Override
        public void flush () { }
      };

      final Conduit < Pipe < Integer >, Integer > conduit =
        circuit.conduit (
          cortex.name ( "flow.skip.conduit" ),
          channel -> channel.pipe (
            flow -> flow
              .skip ( 3L )
              .forward ( forwardPipe )
          )
        );

      final Sink < Integer > sink = cortex.sink ( conduit );

      final Pipe < Integer > pipe =
        conduit.get ( cortex.name ( "flow.skip.channel" ) );

      for ( int i = 1; i <= 10; i++ ) {
        pipe.emit ( i );
      }

      circuit.await ();

      final List < Integer > drained =
        sink.drain ()
          .map ( Capture::emission )
          .collect ( toList () );

      assertEquals (
        List.of ( 4, 5, 6, 7, 8, 9, 10 ),
        captured,
        "Skip should skip first 3 emissions"
      );

      assertEquals (
        captured,
        drained
      );

      sink.close ();

    } finally {

      circuit.close ();

    }

  }

  @Test
  void testSkipZeroPassesAll () {

    final var circuit = cortex.circuit ();

    try {

      final List < Integer > captured = new ArrayList <> ();

      // RC3: Flow.forward() takes Pipe<? super E >, not Consumer< E >
      final Pipe < Integer > forwardPipe = new Pipe <> () {
        @Override
        public void emit ( Integer value ) {
          captured.add ( value );
        }

        @Override
        public void flush () { }
      };

      final Conduit < Pipe < Integer >, Integer > conduit =
        circuit.conduit (
          cortex.name ( "flow.skip.zero.conduit" ),
          channel -> channel.pipe (
            flow -> flow
              .skip ( 0L )
              .forward ( forwardPipe )
          )
        );

      final Pipe < Integer > pipe =
        conduit.get ( cortex.name ( "flow.skip.zero.channel" ) );

      for ( int i = 1; i <= 5; i++ ) {
        pipe.emit ( i );
      }

      circuit.await ();

      assertEquals (
        List.of ( 1, 2, 3, 4, 5 ),
        captured,
        "Skip(0) should pass all emissions"
      );

    } finally {

      circuit.close ();

    }

  }

}
