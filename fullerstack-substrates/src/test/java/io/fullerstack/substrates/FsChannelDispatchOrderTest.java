package io.fullerstack.substrates;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.humainary.substrates.api.Substrates;
import io.humainary.substrates.api.Substrates.Circuit;
import io.humainary.substrates.api.Substrates.Conduit;
import io.humainary.substrates.api.Substrates.Cortex;
import io.humainary.substrates.api.Substrates.Name;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/// Regression test for receptor dispatch order. When multiple subscribers
/// are registered on the same conduit, their receptors must fire in
/// registration order on each emission.
///
/// Background: prior to the fix at {@code FsChannel.rebuild()}, dispatch
/// iterated {@code subscriberReceptors.values()} on an {@link
/// java.util.IdentityHashMap}, whose iteration order depends on
/// {@link System#identityHashCode}, which the JVM randomises per process
/// start. So receptor order was non-deterministic across runs and the
/// equity output of a 8-year backtest varied by ~2% between JVM invocations.
///
/// The fix iterates via the subscriber snapshot from {@link FsHub}
/// (an {@link ArrayList} in subscription order). This test pins that
/// invariant: with N subscribers on one conduit, each emission produces
/// the receptors in 0, 1, ..., N-1 order.
@DisplayName ( "FsChannel dispatches subscribers in registration order" )
class FsChannelDispatchOrderTest {

  private static final int N         = 32;
  private static final int EMISSIONS = 4;

  /// Many subscribers + many emissions: every emission must produce the
  /// same registration-ordered sequence, regardless of JVM identity-hash
  /// randomisation. Repeated to give the test a chance to catch any
  /// residual ordering instability.
  @RepeatedTest ( 8 )
  @DisplayName ( "N subscribers on one conduit fire in registration order on every emission" )
  void fireOrderMatchesRegistrationOrder () {
    final Cortex cortex = Substrates.cortex ();
    final Circuit circuit = cortex.circuit ( cortex.name ( "test.channel.dispatch" ) );
    final Conduit < Integer > conduit = circuit.conduit ( cortex.name ( "test.signal" ), Integer.class );
    final Name subject = cortex.name ( "subj" );
    final List < Integer > observed = new ArrayList <> ();

    try {
      // Register subscribers 0..N-1. Each records its own index to `observed`
      // when its receptor fires. The IdentityHashMap key for each is a
      // distinct subscriber object, so identity-hash order is essentially
      // random across JVM starts.
      IntStream.range ( 0, N ).forEach ( i ->
        conduit.subscribe (
          circuit.subscriber (
            cortex.name ( "sub." + i ),
            ( subj, reg ) -> reg.register ( v -> observed.add ( i ) )
          )
        )
      );

      // Emit a few times. After draining, observed must contain
      // [0, 1, ..., N-1] repeated EMISSIONS times.
      for ( int e = 0; e < EMISSIONS; e++ ) {
        conduit.get ( subject ).emit ( e );
      }
      circuit.await ();

      final List < Integer > expected = new ArrayList <> ( N * EMISSIONS );
      for ( int e = 0; e < EMISSIONS; e++ ) {
        for ( int i = 0; i < N; i++ ) expected.add ( i );
      }
      assertEquals ( expected, observed,
        "receptor invocation order must match subscriber registration order" );
    } finally {
      circuit.close ();
    }
  }

  /// Stem-routing path uses the same dispatch builder. Pin the same
  /// invariant for the receptor portion of cascadeDispatch.
  @Test
  @DisplayName ( "registration order preserved across multiple emissions in one burst" )
  void orderStableWithinAndAcrossEmissions () {
    final Cortex cortex = Substrates.cortex ();
    final Circuit circuit = cortex.circuit ( cortex.name ( "test.channel.burst" ) );
    final Conduit < Integer > conduit = circuit.conduit ( cortex.name ( "test.signal" ), Integer.class );
    final Name subject = cortex.name ( "subj" );
    final List < String > seen = new ArrayList <> ();

    try {
      // Three subscribers with deliberately reverse-sorted names to discourage
      // any accidental name-based ordering — only registration order should win.
      conduit.subscribe ( circuit.subscriber ( cortex.name ( "z.first" ),
        ( s, r ) -> r.register ( v -> seen.add ( "z:" + v ) ) ) );
      conduit.subscribe ( circuit.subscriber ( cortex.name ( "m.second" ),
        ( s, r ) -> r.register ( v -> seen.add ( "m:" + v ) ) ) );
      conduit.subscribe ( circuit.subscriber ( cortex.name ( "a.third" ),
        ( s, r ) -> r.register ( v -> seen.add ( "a:" + v ) ) ) );

      conduit.get ( subject ).emit ( 1 );
      conduit.get ( subject ).emit ( 2 );
      circuit.await ();

      assertEquals (
        List.of ( "z:1", "m:1", "a:1", "z:2", "m:2", "a:2" ),
        seen,
        "registration order — not name order — must determine dispatch order"
      );
    } finally {
      circuit.close ();
    }
  }
}
