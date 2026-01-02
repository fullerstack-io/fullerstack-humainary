package io.fullerstack.substrates;

import static io.humainary.substrates.api.Substrates.*;
import static org.junit.jupiter.api.Assertions.*;

import io.humainary.substrates.api.Substrates.Circuit;
import io.humainary.substrates.api.Substrates.Composer;
import io.humainary.substrates.api.Substrates.Conduit;
import io.humainary.substrates.api.Substrates.Name;
import io.humainary.substrates.api.Substrates.Pipe;
import io.humainary.substrates.api.Substrates.Receptor;
import io.humainary.substrates.api.Substrates.Scope;
import io.humainary.substrates.api.Substrates.Slot;
import io.humainary.substrates.api.Substrates.State;
import io.humainary.substrates.api.Substrates.Subscriber;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests to verify the @NotNull annotation contract from Substrates API.
 *
 * <p>
 * The @NotNull annotation indicates:
 *
 * <ul>
 * <li>On parameters: the parameter should never be passed a null argument value
 * <li>On methods: the method should never return null
 * </ul>
 *
 * <p>
 * These tests verify:
 *
 * <ul>
 * <li>Methods annotated with @NotNull return non-null values
 * <li>Methods throw NullPointerException when @NotNull parameters are passed
 * null
 * </ul>
 */
@DisplayName("@NotNull Annotation Contract Tests")
class NotNullAnnotationContractTest {

  private Circuit circuit;

  @BeforeEach
  void setUp() {
    circuit = cortex().circuit(cortex().name("test"));
  }

  @AfterEach
  void tearDown() {
    if (circuit != null) {
      circuit.close();
    }
  }

  // ============================================================
  // @NotNull return value contracts
  // ============================================================

  @Nested
  @DisplayName("@NotNull return value contracts")
  class NotNullReturnValues {

    @Test
    @DisplayName("cortex() returns non-null")
    void cortex_returnsNonNull() {
      assertNotNull(cortex(), "cortex() must return non-null");
    }

    @Test
    @DisplayName("cortex().name(String) returns non-null")
    void cortexName_returnsNonNull() {
      assertNotNull(cortex().name("test"), "cortex().name() must return non-null");
    }

    @Test
    @DisplayName("cortex().circuit() returns non-null")
    void cortexCircuit_returnsNonNull() {
      Circuit c = cortex().circuit();
      try {
        assertNotNull(c, "cortex().circuit() must return non-null");
      } finally {
        c.close();
      }
    }

    @Test
    @DisplayName("cortex().scope() returns non-null")
    void cortexScope_returnsNonNull() {
      Scope s = cortex().scope();
      try {
        assertNotNull(s, "cortex().scope() must return non-null");
      } finally {
        s.close();
      }
    }

    @Test
    @DisplayName("cortex().state() returns non-null")
    void cortexState_returnsNonNull() {
      assertNotNull(cortex().state(), "cortex().state() must return non-null");
    }

    @Test
    @DisplayName("circuit.subject() returns non-null")
    void circuitSubject_returnsNonNull() {
      assertNotNull(circuit.subject(), "circuit.subject() must return non-null");
    }

    @Test
    @DisplayName("subject.id() returns non-null")
    void subjectId_returnsNonNull() {
      assertNotNull(circuit.subject().id(), "subject.id() must return non-null");
    }

    @Test
    @DisplayName("subject.name() returns non-null")
    void subjectName_returnsNonNull() {
      assertNotNull(circuit.subject().name(), "subject.name() must return non-null");
    }

    @Test
    @DisplayName("subject.state() returns non-null")
    void subjectState_returnsNonNull() {
      assertNotNull(circuit.subject().state(), "subject.state() must return non-null");
    }

    @Test
    @DisplayName("subject.type() returns non-null")
    void subjectType_returnsNonNull() {
      assertNotNull(circuit.subject().type(), "subject.type() must return non-null");
    }

    @Test
    @DisplayName("circuit.pipe() returns non-null")
    void circuitPipe_returnsNonNull() {
      Receptor<Integer> receptor = v -> {
      };
      Pipe<Integer> pipe = circuit.pipe(receptor);
      assertNotNull(pipe, "circuit.pipe() must return non-null");
    }

    @Test
    @DisplayName("circuit.conduit() returns non-null")
    void circuitConduit_returnsNonNull() {
      Conduit<Pipe<String>, String> conduit = circuit.conduit(cortex().name("conduit"), Composer.pipe());
      assertNotNull(conduit, "circuit.conduit() must return non-null");
    }

    @Test
    @DisplayName("circuit.subscriber() returns non-null")
    void circuitSubscriber_returnsNonNull() {
      Subscriber<State> subscriber = circuit.subscriber(cortex().name("sub"), (subject, registrar) -> {
      });
      assertNotNull(subscriber, "circuit.subscriber() must return non-null");
    }

    @Test
    @DisplayName("circuit.reservoir() returns non-null")
    void circuitReservoir_returnsNonNull() {
      assertNotNull(circuit.reservoir(), "circuit.reservoir() must return non-null");
    }

    @Test
    @DisplayName("name.name(suffix) returns non-null")
    void nameExtension_returnsNonNull() {
      Name parent = cortex().name("parent");
      assertNotNull(parent.name(cortex().name("child")), "name.name(suffix) must return non-null");
    }

    @Test
    @DisplayName("state.state(name, value) returns non-null")
    void stateExtension_returnsNonNull() {
      State state = cortex().state();
      assertNotNull(state.state(cortex().name("key"), 42), "state.state(name, value) must return non-null");
    }

    @Test
    @DisplayName("state.compact() returns non-null")
    void stateCompact_returnsNonNull() {
      State state = cortex().state().state(cortex().name("key"), 42);
      assertNotNull(state.compact(), "state.compact() must return non-null");
    }
  }

  // ============================================================
  // @NotNull parameter contracts - null arguments should throw NPE
  // ============================================================

  @Nested
  @DisplayName("@NotNull parameter contracts")
  class NotNullParameters {

    @Test
    @DisplayName("cortex().name(null) throws NullPointerException")
    void cortexName_nullThrowsNPE() {
      assertThrows(NullPointerException.class, () -> cortex().name((String) null),
          "cortex().name(null) must throw NullPointerException");
    }

    @Test
    @DisplayName("cortex().circuit(null) throws NullPointerException")
    void cortexCircuit_nullThrowsNPE() {
      assertThrows(NullPointerException.class, () -> cortex().circuit(null),
          "cortex().circuit(null) must throw NullPointerException");
    }

    @Test
    @DisplayName("cortex().scope(null) throws NullPointerException")
    void cortexScope_nullThrowsNPE() {
      assertThrows(NullPointerException.class, () -> cortex().scope(null),
          "cortex().scope(null) must throw NullPointerException");
    }

    @Test
    @DisplayName("cortex().slot(null, value) throws NullPointerException")
    void cortexSlot_nullNameThrowsNPE() {
      assertThrows(NullPointerException.class, () -> cortex().slot(null, 42),
          "cortex().slot(null, value) must throw NullPointerException");
    }

    @Test
    @DisplayName("circuit.pipe(null) throws NullPointerException")
    void circuitPipe_nullThrowsNPE() {
      assertThrows(NullPointerException.class, () -> circuit.pipe((Receptor<Object>) null),
          "circuit.pipe(null) must throw NullPointerException");
    }

    @Test
    @DisplayName("circuit.conduit(null, composer) throws NullPointerException")
    void circuitConduit_nullNameThrowsNPE() {
      assertThrows(NullPointerException.class, () -> circuit.conduit(null, Composer.pipe()),
          "circuit.conduit(null, composer) must throw NullPointerException");
    }

    @Test
    @DisplayName("circuit.conduit(name, null) throws NullPointerException")
    void circuitConduit_nullComposerThrowsNPE() {
      assertThrows(NullPointerException.class, () -> circuit.conduit(cortex().name("conduit"), null),
          "circuit.conduit(name, null) must throw NullPointerException");
    }

    @Test
    @DisplayName("circuit.subscriber(null, callback) throws NullPointerException")
    void circuitSubscriber_nullNameThrowsNPE() {
      assertThrows(NullPointerException.class, () -> circuit.subscriber(null, (subject, registrar) -> {
      }), "circuit.subscriber(null, callback) must throw NullPointerException");
    }

    @Test
    @DisplayName("circuit.subscriber(name, null) throws NullPointerException")
    void circuitSubscriber_nullCallbackThrowsNPE() {
      assertThrows(NullPointerException.class, () -> circuit.subscriber(cortex().name("sub"), null),
          "circuit.subscriber(name, null) must throw NullPointerException");
    }

    @Test
    @DisplayName("circuit.subscribe(null) throws NullPointerException")
    void circuitSubscribe_nullThrowsNPE() {
      assertThrows(NullPointerException.class, () -> circuit.subscribe(null),
          "circuit.subscribe(null) must throw NullPointerException");
    }

    @Test
    @DisplayName("name.name(null Name) throws NullPointerException")
    void nameExtension_nullThrowsNPE() {
      Name parent = cortex().name("parent");
      assertThrows(NullPointerException.class, () -> parent.name((Name) null),
          "name.name(null) must throw NullPointerException");
    }

    @Test
    @DisplayName("state.state(null, value) throws NullPointerException")
    void stateExtension_nullNameThrowsNPE() {
      State state = cortex().state();
      assertThrows(NullPointerException.class, () -> state.state(null, 42),
          "state.state(null, value) must throw NullPointerException");
    }

    @Test
    @DisplayName("state.state(name, null String) throws NullPointerException")
    void stateExtension_nullStringValueThrowsNPE() {
      State state = cortex().state();
      assertThrows(NullPointerException.class, () -> state.state(cortex().name("key"), (String) null),
          "state.state(name, null) must throw NullPointerException");
    }

    @Test
    @DisplayName("state.state(null Slot) throws NullPointerException")
    void stateExtension_nullSlotThrowsNPE() {
      State state = cortex().state();
      assertThrows(NullPointerException.class, () -> state.state((Slot<?>) null),
          "state.state(null) must throw NullPointerException");
    }
  }

  // ============================================================
  // Conduit @NotNull contracts
  // ============================================================

  @Nested
  @DisplayName("Conduit @NotNull contracts")
  class ConduitNotNullContracts {

    @Test
    @DisplayName("conduit.percept(null) throws NullPointerException")
    void conduitPercept_nullThrowsNPE() {
      Conduit<Pipe<String>, String> conduit = circuit.conduit(cortex().name("conduit"), Composer.pipe());
      assertThrows(NullPointerException.class, () -> conduit.percept((Name) null),
          "conduit.percept(null) must throw NullPointerException");
    }

    @Test
    @DisplayName("conduit.subscribe(null) throws NullPointerException")
    void conduitSubscribe_nullThrowsNPE() {
      Conduit<Pipe<String>, String> conduit = circuit.conduit(cortex().name("conduit"), Composer.pipe());
      assertThrows(NullPointerException.class, () -> conduit.subscribe(null),
          "conduit.subscribe(null) must throw NullPointerException");
    }

    @Test
    @DisplayName("conduit.subject() returns non-null")
    void conduitSubject_returnsNonNull() {
      Conduit<Pipe<String>, String> conduit = circuit.conduit(cortex().name("conduit"), Composer.pipe());
      assertNotNull(conduit.subject(), "conduit.subject() must return non-null");
    }

    @Test
    @DisplayName("conduit.percept(name) returns non-null")
    void conduitPercept_returnsNonNull() {
      Conduit<Pipe<String>, String> conduit = circuit.conduit(cortex().name("conduit"), Composer.pipe());
      assertNotNull(conduit.percept(cortex().name("pipe")), "conduit.percept(name) must return non-null");
    }
  }

  // ============================================================
  // Scope @NotNull contracts
  // ============================================================

  @Nested
  @DisplayName("Scope @NotNull contracts")
  class ScopeNotNullContracts {

    @Test
    @DisplayName("scope.register(null) throws NullPointerException")
    void scopeRegister_nullThrowsNPE() {
      Scope scope = cortex().scope(cortex().name("scope"));
      try {
        assertThrows(NullPointerException.class, () -> scope.register(null),
            "scope.register(null) must throw NullPointerException");
      } finally {
        scope.close();
      }
    }

    @Test
    @DisplayName("scope.subject() returns non-null")
    void scopeSubject_returnsNonNull() {
      Scope scope = cortex().scope(cortex().name("scope"));
      try {
        assertNotNull(scope.subject(), "scope.subject() must return non-null");
      } finally {
        scope.close();
      }
    }

    @Test
    @DisplayName("scope.scope() returns non-null")
    void scopeScope_returnsNonNull() {
      Scope scope = cortex().scope(cortex().name("parent"));
      try {
        Scope child = scope.scope();
        try {
          assertNotNull(child, "scope.scope() must return non-null");
        } finally {
          child.close();
        }
      } finally {
        scope.close();
      }
    }

    @Test
    @DisplayName("scope.scope(null) throws NullPointerException")
    void scopeScope_nullThrowsNPE() {
      Scope scope = cortex().scope(cortex().name("parent"));
      try {
        assertThrows(NullPointerException.class, () -> scope.scope(null),
            "scope.scope(null) must throw NullPointerException");
      } finally {
        scope.close();
      }
    }

    @Test
    @DisplayName("scope.closure(null) throws NullPointerException")
    void scopeClosure_nullThrowsNPE() {
      Scope scope = cortex().scope(cortex().name("scope"));
      try {
        assertThrows(NullPointerException.class, () -> scope.closure(null),
            "scope.closure(null) must throw NullPointerException");
      } finally {
        scope.close();
      }
    }
  }

  // ============================================================
  // Name @NotNull contracts
  // ============================================================

  @Nested
  @DisplayName("Name @NotNull contracts")
  class NameNotNullContracts {

    @Test
    @DisplayName("name.part() returns non-null")
    void namePart_returnsNonNull() {
      Name name = cortex().name("test");
      assertNotNull(name.part(), "name.part() must return non-null");
    }

    @Test
    @DisplayName("name.path() returns non-null")
    void namePath_returnsNonNull() {
      Name name = cortex().name("a.b.c");
      assertNotNull(name.path(), "name.path() must return non-null");
    }

    @Test
    @DisplayName("name.name(null String) throws NullPointerException")
    void nameNameString_nullThrowsNPE() {
      Name name = cortex().name("parent");
      assertThrows(NullPointerException.class, () -> name.name((String) null),
          "name.name(null String) must throw NullPointerException");
    }

    @Test
    @DisplayName("name.stream() returns non-null")
    void nameStream_returnsNonNull() {
      Name name = cortex().name("a.b.c");
      assertNotNull(name.stream(), "name.stream() must return non-null");
    }

    @Test
    @DisplayName("name.iterator() returns non-null")
    void nameIterator_returnsNonNull() {
      Name name = cortex().name("a.b.c");
      assertNotNull(name.iterator(), "name.iterator() must return non-null");
    }
  }

  // ============================================================
  // State @NotNull contracts
  // ============================================================

  @Nested
  @DisplayName("State @NotNull contracts")
  class StateNotNullContracts {

    @Test
    @DisplayName("state.stream() returns non-null")
    void stateStream_returnsNonNull() {
      State state = cortex().state().state(cortex().name("key"), 42);
      assertNotNull(state.stream(), "state.stream() must return non-null");
    }

    @Test
    @DisplayName("state.value(null) throws NullPointerException")
    void stateValue_nullThrowsNPE() {
      State state = cortex().state();
      assertThrows(NullPointerException.class, () -> state.value(null),
          "state.value(null) must throw NullPointerException");
    }

    @Test
    @DisplayName("state.values(null) throws NullPointerException")
    void stateValues_nullThrowsNPE() {
      State state = cortex().state();
      assertThrows(NullPointerException.class, () -> state.values(null),
          "state.values(null) must throw NullPointerException");
    }

    @Test
    @DisplayName("state.state(null Name, int) throws NullPointerException")
    void stateStateInt_nullNameThrowsNPE() {
      State state = cortex().state();
      assertThrows(NullPointerException.class, () -> state.state(null, 42),
          "state.state(null, int) must throw NullPointerException");
    }

    @Test
    @DisplayName("state.state(name, null Name) throws NullPointerException")
    void stateStateName_nullValueThrowsNPE() {
      State state = cortex().state();
      assertThrows(NullPointerException.class, () -> state.state(cortex().name("key"), (Name) null),
          "state.state(name, null Name) must throw NullPointerException");
    }

    @Test
    @DisplayName("state.state(name, null State) throws NullPointerException")
    void stateStateState_nullValueThrowsNPE() {
      State state = cortex().state();
      assertThrows(NullPointerException.class, () -> state.state(cortex().name("key"), (State) null),
          "state.state(name, null State) must throw NullPointerException");
    }

    @Test
    @DisplayName("state.state(null Enum) throws NullPointerException")
    void stateStateEnum_nullThrowsNPE() {
      State state = cortex().state();
      assertThrows(NullPointerException.class, () -> state.state((Enum<?>) null),
          "state.state(null Enum) must throw NullPointerException");
    }
  }

  // ============================================================
  // Slot @NotNull contracts
  // ============================================================

  @Nested
  @DisplayName("Slot @NotNull contracts")
  class SlotNotNullContracts {

    @Test
    @DisplayName("slot.name() returns non-null")
    void slotName_returnsNonNull() {
      Slot<Integer> slot = cortex().slot(cortex().name("key"), 42);
      assertNotNull(slot.name(), "slot.name() must return non-null");
    }

    @Test
    @DisplayName("slot.value() returns non-null for non-null value")
    void slotValue_returnsNonNull() {
      Slot<Integer> slot = cortex().slot(cortex().name("key"), 42);
      assertNotNull(slot.value(), "slot.value() must return non-null");
    }

    @Test
    @DisplayName("slot.type() returns non-null")
    void slotType_returnsNonNull() {
      Slot<Integer> slot = cortex().slot(cortex().name("key"), 42);
      assertNotNull(slot.type(), "slot.type() must return non-null");
    }
  }

  // ============================================================
  // Pipe @NotNull contracts
  // ============================================================

  @Nested
  @DisplayName("Pipe @NotNull contracts")
  class PipeNotNullContracts {

    @Test
    @DisplayName("pipe.subject() returns non-null")
    void pipeSubject_returnsNonNull() {
      Pipe<Integer> pipe = circuit.pipe(v -> {
      });
      assertNotNull(pipe.subject(), "pipe.subject() must return non-null");
    }
  }

  // ============================================================
  // Cell @NotNull contracts
  // ============================================================

  @Nested
  @DisplayName("Cell @NotNull contracts")
  class CellNotNullContracts {

    @Test
    @DisplayName("circuit.cell(null, ingress, egress, receptor) throws NullPointerException")
    void circuitCell_nullNameThrowsNPE() {
      assertThrows(NullPointerException.class,
          () -> circuit.cell(null, Composer.pipe(), Composer.pipe(), (Receptor<Integer>) v -> {
          }), "circuit.cell(null, ...) must throw NullPointerException");
    }

    @Test
    @DisplayName("circuit.cell(name, null, egress, receptor) throws NullPointerException")
    void circuitCell_nullIngressThrowsNPE() {
      assertThrows(NullPointerException.class,
          () -> circuit.cell(cortex().name("cell"), null, Composer.pipe(), (Receptor<Integer>) v -> {
          }), "circuit.cell(..., null ingress, ...) must throw NullPointerException");
    }

    @Test
    @DisplayName("circuit.cell(name, ingress, null, receptor) throws NullPointerException")
    void circuitCell_nullEgressThrowsNPE() {
      assertThrows(NullPointerException.class,
          () -> circuit.cell(cortex().name("cell"), Composer.pipe(), null, (Receptor<Integer>) v -> {
          }), "circuit.cell(..., null egress, ...) must throw NullPointerException");
    }

    @Test
    @DisplayName("circuit.cell(name, ingress, egress, null) throws NullPointerException")
    void circuitCell_nullReceptorThrowsNPE() {
      assertThrows(NullPointerException.class,
          () -> circuit.cell(cortex().name("cell"), Composer.pipe(), Composer.pipe(), null),
          "circuit.cell(..., null receptor) must throw NullPointerException");
    }
  }

  // ============================================================
  // Reservoir @NotNull contracts
  // ============================================================

  @Nested
  @DisplayName("Reservoir @NotNull contracts")
  class ReservoirNotNullContracts {

    @Test
    @DisplayName("reservoir.subject() returns non-null")
    void reservoirSubject_returnsNonNull() {
      var reservoir = circuit.reservoir();
      assertNotNull(reservoir.subject(), "reservoir.subject() must return non-null");
    }

    @Test
    @DisplayName("reservoir.drain() returns non-null")
    void reservoirDrain_returnsNonNull() {
      var reservoir = circuit.reservoir();
      assertNotNull(reservoir.drain(), "reservoir.drain() must return non-null");
    }
  }

  // ============================================================
  // Subscriber @NotNull contracts
  // ============================================================

  @Nested
  @DisplayName("Subscriber @NotNull contracts")
  class SubscriberNotNullContracts {

    @Test
    @DisplayName("subscriber.subject() returns non-null")
    void subscriberSubject_returnsNonNull() {
      Subscriber<State> subscriber = circuit.subscriber(cortex().name("sub"), (subject, registrar) -> {
      });
      assertNotNull(subscriber.subject(), "subscriber.subject() must return non-null");
    }
  }
}
