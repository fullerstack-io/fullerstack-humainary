package io.fullerstack.substrates;

import io.humainary.substrates.api.Substrates;
import io.humainary.substrates.api.Substrates.Cell;
import io.humainary.substrates.api.Substrates.Circuit;
import io.humainary.substrates.api.Substrates.Conduit;
import io.humainary.substrates.api.Substrates.Cortex;
import io.humainary.substrates.api.Substrates.Name;
import io.humainary.substrates.api.Substrates.Pipe;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/// Mirrors the exact pattern ShadowEquityHolon uses:
///   1. external Conduit<Double> (e.g. realizedReturnConduit)
///   2. subscribe handler creates a per-subject Cell<State> lazily
///   3. the receiver in the handler computes new state and emits to cell.pipe()
class ScanPlusCellTest {

  record State(double equity, double lastReturn) {
    static final State INITIAL = new State(10000.0, 0.0);
    State apply(double r) { return new State(equity * (1.0 + r), r); }
  }

  @Test
  void shadowEquityHolonPattern() {
    Cortex cortex = Substrates.cortex();
    Circuit circuit = cortex.circuit();
    try {
      Conduit<Double> realizedReturn = circuit.conduit(cortex.name("realized.return"), Double.class);
      Map<Name, Cell<State>> stateCells = new ConcurrentHashMap<>();
      final Map<Name, State> scanStates = new ConcurrentHashMap<>();

      realizedReturn.subscribe(circuit.subscriber(
          cortex.name("shadow.equity"),
          (subj, reg) -> {
            final Name n = subj.name();
            final Cell<State> cell = stateCells.computeIfAbsent(n, k -> circuit.cell(State.INITIAL));
            final Pipe<State> out = cell.pipe();
            reg.register((Double r) -> {
              State prev = scanStates.computeIfAbsent(n, k -> State.INITIAL);
              State next = prev.apply(r);
              scanStates.put(n, next);
              out.emit(next);
            });
          }));

      Name btc = cortex.name("BTC");
      realizedReturn.get(btc).emit(0.05);
      realizedReturn.get(btc).emit(-0.02);
      realizedReturn.get(btc).emit(0.03);
      circuit.await();

      Cell<State> cell = stateCells.get(btc);
      assertThat((Object) cell).isNotNull();
      State observed = cell.get();
      // 10000 * 1.05 * 0.98 * 1.03 = 10598.7
      assertThat(observed.equity()).isCloseTo(10598.7, org.assertj.core.data.Offset.offset(0.5));
    } finally {
      circuit.close();
    }
  }
}
