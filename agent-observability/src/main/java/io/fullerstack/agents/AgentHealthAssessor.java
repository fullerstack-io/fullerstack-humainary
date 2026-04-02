package io.fullerstack.agents;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

import io.humainary.substrates.api.Substrates.Channel;
import io.humainary.substrates.api.Substrates.Conduit;
import io.humainary.substrates.api.Substrates.Registrar;
import io.humainary.substrates.api.Substrates.Subject;
import io.humainary.serventis.sdk.Outcomes;
import io.humainary.serventis.sdk.Statuses;

/// Assesses agent health from outcome signals via sliding window.
///
/// Agent-specific thresholds (tighter than payment processing):
///
/// | Failure Rate | Status     | Agent Context                    |
/// |-------------|------------|----------------------------------|
/// | ≤ 1%        | STABLE     | Normal agent operation            |
/// | ≤ 5%        | DIVERGING  | Occasional LLM/tool failures     |
/// | ≤ 15%       | DEGRADED   | Reliability declining            |
/// | ≤ 30%       | DEFECTIVE  | Agent decisions unreliable       |
/// | > 30%       | DOWN       | Agent non-functional             |
///
/// Window size is 10 (agents make fewer decisions than payment systems).
final class AgentHealthAssessor
  implements BiConsumer < Subject < Channel < Outcomes.Sign > >, Registrar < Outcomes.Sign > > {

  private static final int WINDOW_SIZE = 10;

  private final Conduit < Statuses.Status, Statuses.Signal > statuses;
  private final Map < String, WindowState > windows = new HashMap <> ();

  private static final class WindowState {
    final boolean[] samples = new boolean[WINDOW_SIZE];
    int index = 0;
    int count = 0;
    long totalSamples = 0;
  }

  AgentHealthAssessor ( Conduit < Statuses.Status, Statuses.Signal > statuses ) {
    this.statuses = statuses;
  }

  @Override
  public void accept (
    Subject < Channel < Outcomes.Sign > > channelSubject,
    Registrar < Outcomes.Sign > registrar
  ) {
    String channelName = channelSubject.name ().toString ();

    registrar.register ( sign -> {
      var state = windows.computeIfAbsent ( channelName, k -> new WindowState () );

      state.samples[state.index] = sign == Outcomes.Sign.SUCCESS;
      state.index = ( state.index + 1 ) % WINDOW_SIZE;
      state.count = Math.min ( state.count + 1, WINDOW_SIZE );
      state.totalSamples++;

      if ( state.count < WINDOW_SIZE ) return;

      int failures = 0;
      for ( int i = 0; i < WINDOW_SIZE; i++ ) {
        if ( !state.samples[i] ) failures++;
      }

      double failRate = (double) failures / WINDOW_SIZE;

      Statuses.Sign status;
      if ( failRate <= 0.01 ) status = Statuses.Sign.STABLE;
      else if ( failRate <= 0.05 ) status = Statuses.Sign.DIVERGING;
      else if ( failRate <= 0.15 ) status = Statuses.Sign.DEGRADED;
      else if ( failRate <= 0.30 ) status = Statuses.Sign.DEFECTIVE;
      else status = Statuses.Sign.DOWN;

      Statuses.Dimension confidence;
      if ( state.totalSamples < 20 ) confidence = Statuses.Dimension.TENTATIVE;
      else if ( state.totalSamples < 50 ) confidence = Statuses.Dimension.MEASURED;
      else confidence = Statuses.Dimension.CONFIRMED;

      statuses.percept ( channelSubject.name () ).signal ( status, confidence );
    } );
  }
}
