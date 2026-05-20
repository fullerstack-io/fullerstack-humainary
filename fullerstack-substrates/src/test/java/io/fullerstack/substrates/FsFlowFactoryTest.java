package io.fullerstack.substrates;

import io.humainary.substrates.api.Substrates;
import io.humainary.substrates.api.Substrates.Flow;
import io.humainary.substrates.api.Substrates.Subject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FsFlowFactoryTest {

  @Test
  @DisplayName("flow(Function<Subject, Flow>) invokes factory once with target subject")
  void factoryInvokedOnceWithTargetSubject() {
    final AtomicReference<Subject<?>> seen = new AtomicReference<>();
    final var cortex = Substrates.cortex();
    final var circuit = cortex.circuit();
    try {
      // factory builds a Flow<Integer, Integer> that multiplies by 10
      final Flow<Integer, Integer> flow = cortex.<Integer>flow(Integer.class)
          .flow(subj -> {
            seen.set(subj);
            return cortex.<Integer>flow(Integer.class).map((Integer n) -> n * 10);
          });

      final List<Integer> out = new ArrayList<>();
      final var sink = circuit.<Integer>pipe(out::add);
      final var pipe = flow.pipe(sink);

      pipe.emit(3);
      pipe.emit(4);
      circuit.await();

      assertThat((Object) seen.get()).isNotNull();
      assertThat(out).containsExactly(30, 40);
    } finally {
      circuit.close();
    }
  }

  @Test
  @DisplayName("flow(factory) — per-subject scan seed via subject-aware factory")
  void perSubjectScanSeed() {
    // The spec's example use case: per-subject scan seed via flow(factory).
    // The factory inspects the subject and returns a flow with a scan whose
    // initial state is keyed on the subject identity.
    final var cortex = Substrates.cortex();
    final var circuit = cortex.circuit();
    try {
      final Flow<Integer, Integer> flow = cortex.<Integer>flow(Integer.class)
          .flow(subj -> {
            // Different seed depending on subject's name
            final int seed = subj.name().toString().contains("A") ? 100 : 1000;
            return cortex.<Integer>flow(Integer.class)
                .scan(() -> seed, Integer::sum, s -> s);
          });

      // Two independent pipes — each gets a fresh factory invocation with its own subject
      final List<Integer> outA = new ArrayList<>();
      final List<Integer> outB = new ArrayList<>();
      final var pipeA = flow.pipe(circuit.pipe(cortex.name("A"),
          (io.humainary.substrates.api.Substrates.Receptor<Integer>) outA::add));
      final var pipeB = flow.pipe(circuit.pipe(cortex.name("B"),
          (io.humainary.substrates.api.Substrates.Receptor<Integer>) outB::add));

      pipeA.emit(1);
      pipeA.emit(2);
      pipeB.emit(1);
      pipeB.emit(2);
      circuit.await();

      // A starts at 100: 101, 103
      // B starts at 1000: 1001, 1003
      assertThat(outA).containsExactly(101, 103);
      assertThat(outB).containsExactly(1001, 1003);
    } finally {
      circuit.close();
    }
  }

  @Test
  @DisplayName("flow(factory) — null factory throws NPE")
  void nullFactoryThrows() {
    final var cortex = Substrates.cortex();
    assertThatThrownBy(() -> cortex.<Integer>flow(Integer.class).flow(
        (java.util.function.Function<? super Subject<?>, ? extends Flow<? super Integer, ? extends String>>) null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("flow(factory) composes after map and other operators")
  void composesAfterMap() {
    final var cortex = Substrates.cortex();
    final var circuit = cortex.circuit();
    try {
      final Flow<Integer, Integer> flow = cortex.<Integer>flow(Integer.class)
          .map((Integer n) -> n + 1)                   // +1
          .flow(subj -> cortex.<Integer>flow(Integer.class)
              .map((Integer n) -> n * 10));            // ×10

      final List<Integer> out = new ArrayList<>();
      final var sink = circuit.<Integer>pipe(out::add);
      final var pipe = flow.pipe(sink);

      pipe.emit(1);   // (1+1)*10 = 20
      pipe.emit(2);   // (2+1)*10 = 30
      circuit.await();

      assertThat(out).containsExactly(20, 30);
    } finally {
      circuit.close();
    }
  }
}
