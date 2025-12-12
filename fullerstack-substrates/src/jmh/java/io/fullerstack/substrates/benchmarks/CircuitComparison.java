package io.fullerstack.substrates.benchmarks;

import static io.humainary.substrates.api.Substrates.cortex;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.openjdk.jmh.annotations.Level.Iteration;
import static org.openjdk.jmh.annotations.Level.Trial;
import static org.openjdk.jmh.annotations.Mode.AverageTime;

import io.fullerstack.substrates.FsCircuit;
import io.fullerstack.substrates.FsName;
import io.fullerstack.substrates.FsSubject;
import io.fullerstack.substrates.batch.FsBatchCircuit;
import io.fullerstack.substrates.disruptor.FsDisruptorCircuit;
import io.fullerstack.substrates.ring.FsRingCircuit;
import io.fullerstack.substrates.valve.FsValveCircuit;
import io.humainary.substrates.api.Substrates;
import io.humainary.substrates.api.Substrates.Circuit;
import io.humainary.substrates.api.Substrates.Composer;
import io.humainary.substrates.api.Substrates.Conduit;
import io.humainary.substrates.api.Substrates.Cortex;
import io.humainary.substrates.api.Substrates.Current;
import io.humainary.substrates.api.Substrates.Name;
import io.humainary.substrates.api.Substrates.Pipe;
import io.humainary.substrates.api.Substrates.Receptor;
import io.humainary.substrates.api.Substrates.Scope;
import io.humainary.substrates.api.Substrates.Slot;
import io.humainary.substrates.api.Substrates.Subject;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Comprehensive benchmark comparing all circuit implementations.
 *
 * <p>Run specific circuit types to avoid memory issues:
 *
 * <ul>
 *   <li>java -jar target/benchmarks.jar "CircuitComparison.cortex" - CortexOps only
 *   <li>java -jar target/benchmarks.jar "CircuitComparison.baseline" - Baseline circuit
 *   <li>java -jar target/benchmarks.jar "CircuitComparison.valve" - Valve circuit
 *   <li>java -jar target/benchmarks.jar "CircuitComparison.ring" - Ring circuit
 *   <li>java -jar target/benchmarks.jar "CircuitComparison.batch" - Batch circuit
 *   <li>java -jar target/benchmarks.jar "CircuitComparison.disruptor" - Disruptor circuit
 * </ul>
 */
@BenchmarkMode(AverageTime)
@OutputTimeUnit(NANOSECONDS)
@Fork(1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
public class CircuitComparison {

  // Constants
  private static final String NAME_STR = "test";
  private static final String PATH = "parent.child.leaf";
  private static final List<String> NAME_LIST = List.of("parent", "child", "leaf");
  private static final int INT_VAL = 42;
  private static final long LONG_VAL = 42L;
  private static final double DBL_VAL = 42.0;
  private static final String STR_VAL = "value";
  private static final int BATCH_SIZE = 1000;

  // =========================================================================
  // CORTEX STATE - For circuit-independent operations
  // =========================================================================

  @org.openjdk.jmh.annotations.State(org.openjdk.jmh.annotations.Scope.Benchmark)
  public static class CortexState {
    Cortex cortex;
    Name name;
    AtomicInteger counter;

    @Setup(Trial)
    public void setup() {
      cortex = cortex();
      name = FsName.parse(NAME_STR);
      counter = new AtomicInteger();
    }
  }

  // =========================================================================
  // BASELINE STATE - FsCircuit
  // =========================================================================

  @org.openjdk.jmh.annotations.State(org.openjdk.jmh.annotations.Scope.Benchmark)
  public static class BaselineState {
    Name name;
    Circuit hotCircuit;
    Pipe<Integer> pipe;
    Pipe<Integer> pipeWithFlow;
    Pipe<Integer> chainedPipe;
    Conduit<Pipe<Integer>, Integer> conduit;
    Name channelName;

    @Setup(Trial)
    public void setupTrial() {
      name = FsName.parse(NAME_STR);
    }

    @Setup(Iteration)
    public void setup() {
      hotCircuit = new FsCircuit(new FsSubject<>(FsName.parse("baseline"), Circuit.class));
      pipe = hotCircuit.pipe(Receptor.of(Integer.class));
      pipeWithFlow =
          hotCircuit.pipe(Receptor.of(Integer.class), flow -> flow.guard(v -> v > 0).diff());
      Pipe<Integer> sink = hotCircuit.pipe(Receptor.of(Integer.class));
      Pipe<Integer> intermediate = hotCircuit.pipe(sink);
      chainedPipe = hotCircuit.pipe(intermediate);
      channelName = FsName.parse("channel");
      conduit = hotCircuit.conduit(FsName.parse("conduit"), Composer.pipe());
      hotCircuit.await();
    }

    @TearDown(Iteration)
    public void teardown() {
      hotCircuit.close();
    }
  }

  // =========================================================================
  // VALVE STATE - FsValveCircuit
  // =========================================================================

  @org.openjdk.jmh.annotations.State(org.openjdk.jmh.annotations.Scope.Benchmark)
  public static class ValveState {
    Name name;
    Circuit hotCircuit;
    Pipe<Integer> pipe;
    Pipe<Integer> pipeWithFlow;
    Pipe<Integer> chainedPipe;
    Conduit<Pipe<Integer>, Integer> conduit;
    Name channelName;

    @Setup(Trial)
    public void setupTrial() {
      name = FsName.parse(NAME_STR);
    }

    @Setup(Iteration)
    public void setup() {
      hotCircuit = new FsValveCircuit(new FsSubject<>(FsName.parse("valve"), Circuit.class));
      pipe = hotCircuit.pipe(Receptor.of(Integer.class));
      pipeWithFlow =
          hotCircuit.pipe(Receptor.of(Integer.class), flow -> flow.guard(v -> v > 0).diff());
      Pipe<Integer> sink = hotCircuit.pipe(Receptor.of(Integer.class));
      Pipe<Integer> intermediate = hotCircuit.pipe(sink);
      chainedPipe = hotCircuit.pipe(intermediate);
      channelName = FsName.parse("channel");
      conduit = hotCircuit.conduit(FsName.parse("conduit"), Composer.pipe());
      hotCircuit.await();
    }

    @TearDown(Iteration)
    public void teardown() {
      hotCircuit.close();
    }
  }

  // =========================================================================
  // RING STATE - FsRingCircuit
  // =========================================================================

  @org.openjdk.jmh.annotations.State(org.openjdk.jmh.annotations.Scope.Benchmark)
  public static class RingState {
    Name name;
    Circuit hotCircuit;
    Pipe<Integer> pipe;
    Pipe<Integer> pipeWithFlow;
    Pipe<Integer> chainedPipe;
    Conduit<Pipe<Integer>, Integer> conduit;
    Name channelName;

    @Setup(Trial)
    public void setupTrial() {
      name = FsName.parse(NAME_STR);
    }

    @Setup(Iteration)
    public void setup() {
      hotCircuit = new FsRingCircuit(new FsSubject<>(FsName.parse("ring"), Circuit.class));
      pipe = hotCircuit.pipe(Receptor.of(Integer.class));
      pipeWithFlow =
          hotCircuit.pipe(Receptor.of(Integer.class), flow -> flow.guard(v -> v > 0).diff());
      Pipe<Integer> sink = hotCircuit.pipe(Receptor.of(Integer.class));
      Pipe<Integer> intermediate = hotCircuit.pipe(sink);
      chainedPipe = hotCircuit.pipe(intermediate);
      channelName = FsName.parse("channel");
      conduit = hotCircuit.conduit(FsName.parse("conduit"), Composer.pipe());
      hotCircuit.await();
    }

    @TearDown(Iteration)
    public void teardown() {
      hotCircuit.close();
    }
  }

  // =========================================================================
  // BATCH STATE - FsBatchCircuit
  // =========================================================================

  @org.openjdk.jmh.annotations.State(org.openjdk.jmh.annotations.Scope.Benchmark)
  public static class BatchState {
    Name name;
    Circuit hotCircuit;
    Pipe<Integer> pipe;
    Pipe<Integer> pipeWithFlow;
    Pipe<Integer> chainedPipe;
    Conduit<Pipe<Integer>, Integer> conduit;
    Name channelName;

    @Setup(Trial)
    public void setupTrial() {
      name = FsName.parse(NAME_STR);
    }

    @Setup(Iteration)
    public void setup() {
      hotCircuit = new FsBatchCircuit(new FsSubject<>(FsName.parse("batch"), Circuit.class));
      pipe = hotCircuit.pipe(Receptor.of(Integer.class));
      pipeWithFlow =
          hotCircuit.pipe(Receptor.of(Integer.class), flow -> flow.guard(v -> v > 0).diff());
      Pipe<Integer> sink = hotCircuit.pipe(Receptor.of(Integer.class));
      Pipe<Integer> intermediate = hotCircuit.pipe(sink);
      chainedPipe = hotCircuit.pipe(intermediate);
      channelName = FsName.parse("channel");
      conduit = hotCircuit.conduit(FsName.parse("conduit"), Composer.pipe());
      hotCircuit.await();
    }

    @TearDown(Iteration)
    public void teardown() {
      hotCircuit.close();
    }
  }

  // =========================================================================
  // DISRUPTOR STATE - FsDisruptorCircuit
  // =========================================================================

  @org.openjdk.jmh.annotations.State(org.openjdk.jmh.annotations.Scope.Benchmark)
  public static class DisruptorState {
    Name name;
    Circuit hotCircuit;
    Pipe<Integer> pipe;
    Pipe<Integer> pipeWithFlow;
    Pipe<Integer> chainedPipe;
    Conduit<Pipe<Integer>, Integer> conduit;
    Name channelName;

    @Setup(Trial)
    public void setupTrial() {
      name = FsName.parse(NAME_STR);
    }

    @Setup(Iteration)
    public void setup() {
      hotCircuit =
          new FsDisruptorCircuit(new FsSubject<>(FsName.parse("disruptor"), Circuit.class));
      pipe = hotCircuit.pipe(Receptor.of(Integer.class));
      pipeWithFlow =
          hotCircuit.pipe(Receptor.of(Integer.class), flow -> flow.guard(v -> v > 0).diff());
      Pipe<Integer> sink = hotCircuit.pipe(Receptor.of(Integer.class));
      Pipe<Integer> intermediate = hotCircuit.pipe(sink);
      chainedPipe = hotCircuit.pipe(intermediate);
      channelName = FsName.parse("channel");
      conduit = hotCircuit.conduit(FsName.parse("conduit"), Composer.pipe());
      hotCircuit.await();
    }

    @TearDown(Iteration)
    public void teardown() {
      hotCircuit.close();
    }
  }

  // =========================================================================
  // CORTEX OPS - Circuit-independent operations
  // =========================================================================

  @Benchmark
  public Name cortex_name_string(CortexState s) {
    return s.cortex.name(NAME_STR);
  }

  @Benchmark
  @OperationsPerInvocation(BATCH_SIZE)
  public Name cortex_name_string_batch(CortexState s) {
    Name result = null;
    for (int i = 0; i < BATCH_SIZE; i++) result = s.cortex.name(NAME_STR);
    return result;
  }

  @Benchmark
  public Name cortex_name_path(CortexState s) {
    return s.cortex.name(PATH);
  }

  @Benchmark
  @OperationsPerInvocation(BATCH_SIZE)
  public Name cortex_name_path_batch(CortexState s) {
    Name result = null;
    for (int i = 0; i < BATCH_SIZE; i++) result = s.cortex.name(PATH);
    return result;
  }

  @Benchmark
  public Name cortex_name_iterable(CortexState s) {
    return s.cortex.name(NAME_LIST);
  }

  @Benchmark
  public Name cortex_name_class(CortexState s) {
    return s.cortex.name(String.class);
  }

  @Benchmark
  public Current cortex_current(CortexState s) {
    return s.cortex.current();
  }

  @Benchmark
  public Scope cortex_scope(CortexState s) {
    Scope result = s.cortex.scope();
    result.close();
    return result;
  }

  @Benchmark
  public Scope cortex_scope_named(CortexState s) {
    Scope result = s.cortex.scope(s.name);
    result.close();
    return result;
  }

  @Benchmark
  public Slot<Integer> cortex_slot_int(CortexState s) {
    return s.cortex.slot(s.name, INT_VAL);
  }

  @Benchmark
  public Slot<Long> cortex_slot_long(CortexState s) {
    return s.cortex.slot(s.name, LONG_VAL);
  }

  @Benchmark
  public Slot<Double> cortex_slot_double(CortexState s) {
    return s.cortex.slot(s.name, DBL_VAL);
  }

  @Benchmark
  public Slot<String> cortex_slot_string(CortexState s) {
    return s.cortex.slot(s.name, STR_VAL);
  }

  @Benchmark
  public Substrates.State cortex_state_empty(CortexState s) {
    return s.cortex.state();
  }

  @Benchmark
  @OperationsPerInvocation(BATCH_SIZE)
  public Substrates.State cortex_state_empty_batch(CortexState s) {
    Substrates.State result = null;
    for (int i = 0; i < BATCH_SIZE; i++) result = s.cortex.state();
    return result;
  }

  // =========================================================================
  // PIPE BASELINES
  // =========================================================================

  @Benchmark
  public void pipe_baseline_blackhole(Blackhole bh) {
    bh.consume(INT_VAL);
  }

  @Benchmark
  public int pipe_baseline_counter(CortexState s) {
    return s.counter.incrementAndGet();
  }

  @SuppressWarnings("unchecked")
  @Benchmark
  public void pipe_baseline_receptor() {
    Receptor.NOOP.receive(INT_VAL);
  }

  // =========================================================================
  // BASELINE CIRCUIT BENCHMARKS
  // =========================================================================

  @Benchmark
  public void baseline_create_and_close(BaselineState s) {
    Subject<Circuit> subject = new FsSubject<>(s.name, Circuit.class);
    Circuit circuit = new FsCircuit(subject);
    circuit.close();
  }

  @Benchmark
  public void baseline_create_await_close(BaselineState s) {
    Subject<Circuit> subject = new FsSubject<>(s.name, Circuit.class);
    Circuit circuit = new FsCircuit(subject);
    circuit.await();
    circuit.close();
  }

  @Benchmark
  public void baseline_hot_await(BaselineState s) {
    s.hotCircuit.await();
  }

  @Benchmark
  public Pipe<Integer> baseline_hot_pipe_create(BaselineState s) {
    return s.hotCircuit.pipe(Receptor.of(Integer.class));
  }

  @Benchmark
  public Pipe<Integer> baseline_hot_pipe_create_with_flow(BaselineState s) {
    return s.hotCircuit.pipe(Receptor.of(Integer.class), flow -> flow.guard(v -> v > 0));
  }

  @Benchmark
  public void baseline_emit_single(BaselineState s) {
    s.pipe.emit(INT_VAL);
  }

  @Benchmark
  public void baseline_emit_single_await(BaselineState s) {
    s.pipe.emit(INT_VAL);
    s.hotCircuit.await();
  }

  @Benchmark
  @OperationsPerInvocation(BATCH_SIZE)
  public void baseline_emit_batch(BaselineState s) {
    for (int i = 0; i < BATCH_SIZE; i++) {
      s.pipe.emit(INT_VAL + i);
    }
  }

  @Benchmark
  @OperationsPerInvocation(BATCH_SIZE)
  public void baseline_emit_batch_await(BaselineState s) {
    for (int i = 0; i < BATCH_SIZE; i++) {
      s.pipe.emit(INT_VAL + i);
    }
    s.hotCircuit.await();
  }

  @Benchmark
  @OperationsPerInvocation(BATCH_SIZE)
  public void baseline_emit_with_flow_await(BaselineState s) {
    for (int i = 0; i < BATCH_SIZE; i++) {
      s.pipeWithFlow.emit(INT_VAL + i);
    }
    s.hotCircuit.await();
  }

  @Benchmark
  @OperationsPerInvocation(BATCH_SIZE)
  public void baseline_emit_chained(BaselineState s) {
    for (int i = 0; i < BATCH_SIZE; i++) {
      s.chainedPipe.emit(INT_VAL + i);
    }
  }

  @Benchmark
  @OperationsPerInvocation(BATCH_SIZE)
  public void baseline_emit_chained_await(BaselineState s) {
    for (int i = 0; i < BATCH_SIZE; i++) {
      s.chainedPipe.emit(INT_VAL + i);
    }
    s.hotCircuit.await();
  }

  @Benchmark
  public Pipe<Integer> baseline_conduit_percept(BaselineState s) {
    return s.conduit.percept(s.channelName);
  }

  // =========================================================================
  // VALVE CIRCUIT BENCHMARKS
  // =========================================================================

  @Benchmark
  public void valve_create_and_close(ValveState s) {
    Subject<Circuit> subject = new FsSubject<>(s.name, Circuit.class);
    Circuit circuit = new FsValveCircuit(subject);
    circuit.close();
  }

  @Benchmark
  public void valve_create_await_close(ValveState s) {
    Subject<Circuit> subject = new FsSubject<>(s.name, Circuit.class);
    Circuit circuit = new FsValveCircuit(subject);
    circuit.await();
    circuit.close();
  }

  @Benchmark
  public void valve_hot_await(ValveState s) {
    s.hotCircuit.await();
  }

  @Benchmark
  public Pipe<Integer> valve_hot_pipe_create(ValveState s) {
    return s.hotCircuit.pipe(Receptor.of(Integer.class));
  }

  @Benchmark
  public Pipe<Integer> valve_hot_pipe_create_with_flow(ValveState s) {
    return s.hotCircuit.pipe(Receptor.of(Integer.class), flow -> flow.guard(v -> v > 0));
  }

  @Benchmark
  public void valve_emit_single(ValveState s) {
    s.pipe.emit(INT_VAL);
  }

  @Benchmark
  public void valve_emit_single_await(ValveState s) {
    s.pipe.emit(INT_VAL);
    s.hotCircuit.await();
  }

  @Benchmark
  @OperationsPerInvocation(BATCH_SIZE)
  public void valve_emit_batch(ValveState s) {
    for (int i = 0; i < BATCH_SIZE; i++) {
      s.pipe.emit(INT_VAL + i);
    }
  }

  @Benchmark
  @OperationsPerInvocation(BATCH_SIZE)
  public void valve_emit_batch_await(ValveState s) {
    for (int i = 0; i < BATCH_SIZE; i++) {
      s.pipe.emit(INT_VAL + i);
    }
    s.hotCircuit.await();
  }

  @Benchmark
  @OperationsPerInvocation(BATCH_SIZE)
  public void valve_emit_with_flow_await(ValveState s) {
    for (int i = 0; i < BATCH_SIZE; i++) {
      s.pipeWithFlow.emit(INT_VAL + i);
    }
    s.hotCircuit.await();
  }

  @Benchmark
  @OperationsPerInvocation(BATCH_SIZE)
  public void valve_emit_chained(ValveState s) {
    for (int i = 0; i < BATCH_SIZE; i++) {
      s.chainedPipe.emit(INT_VAL + i);
    }
  }

  @Benchmark
  @OperationsPerInvocation(BATCH_SIZE)
  public void valve_emit_chained_await(ValveState s) {
    for (int i = 0; i < BATCH_SIZE; i++) {
      s.chainedPipe.emit(INT_VAL + i);
    }
    s.hotCircuit.await();
  }

  @Benchmark
  public Pipe<Integer> valve_conduit_percept(ValveState s) {
    return s.conduit.percept(s.channelName);
  }

  // =========================================================================
  // RING CIRCUIT BENCHMARKS
  // =========================================================================

  @Benchmark
  public void ring_create_and_close(RingState s) {
    Subject<Circuit> subject = new FsSubject<>(s.name, Circuit.class);
    Circuit circuit = new FsRingCircuit(subject);
    circuit.close();
  }

  @Benchmark
  public void ring_create_await_close(RingState s) {
    Subject<Circuit> subject = new FsSubject<>(s.name, Circuit.class);
    Circuit circuit = new FsRingCircuit(subject);
    circuit.await();
    circuit.close();
  }

  @Benchmark
  public void ring_hot_await(RingState s) {
    s.hotCircuit.await();
  }

  @Benchmark
  public Pipe<Integer> ring_hot_pipe_create(RingState s) {
    return s.hotCircuit.pipe(Receptor.of(Integer.class));
  }

  @Benchmark
  public Pipe<Integer> ring_hot_pipe_create_with_flow(RingState s) {
    return s.hotCircuit.pipe(Receptor.of(Integer.class), flow -> flow.guard(v -> v > 0));
  }

  @Benchmark
  public void ring_emit_single(RingState s) {
    s.pipe.emit(INT_VAL);
  }

  @Benchmark
  public void ring_emit_single_await(RingState s) {
    s.pipe.emit(INT_VAL);
    s.hotCircuit.await();
  }

  @Benchmark
  @OperationsPerInvocation(BATCH_SIZE)
  public void ring_emit_batch(RingState s) {
    for (int i = 0; i < BATCH_SIZE; i++) {
      s.pipe.emit(INT_VAL + i);
    }
  }

  @Benchmark
  @OperationsPerInvocation(BATCH_SIZE)
  public void ring_emit_batch_await(RingState s) {
    for (int i = 0; i < BATCH_SIZE; i++) {
      s.pipe.emit(INT_VAL + i);
    }
    s.hotCircuit.await();
  }

  @Benchmark
  @OperationsPerInvocation(BATCH_SIZE)
  public void ring_emit_with_flow_await(RingState s) {
    for (int i = 0; i < BATCH_SIZE; i++) {
      s.pipeWithFlow.emit(INT_VAL + i);
    }
    s.hotCircuit.await();
  }

  @Benchmark
  @OperationsPerInvocation(BATCH_SIZE)
  public void ring_emit_chained(RingState s) {
    for (int i = 0; i < BATCH_SIZE; i++) {
      s.chainedPipe.emit(INT_VAL + i);
    }
  }

  @Benchmark
  @OperationsPerInvocation(BATCH_SIZE)
  public void ring_emit_chained_await(RingState s) {
    for (int i = 0; i < BATCH_SIZE; i++) {
      s.chainedPipe.emit(INT_VAL + i);
    }
    s.hotCircuit.await();
  }

  @Benchmark
  public Pipe<Integer> ring_conduit_percept(RingState s) {
    return s.conduit.percept(s.channelName);
  }

  // =========================================================================
  // BATCH CIRCUIT BENCHMARKS
  // =========================================================================

  @Benchmark
  public void batch_create_and_close(BatchState s) {
    Subject<Circuit> subject = new FsSubject<>(s.name, Circuit.class);
    Circuit circuit = new FsBatchCircuit(subject);
    circuit.close();
  }

  @Benchmark
  public void batch_create_await_close(BatchState s) {
    Subject<Circuit> subject = new FsSubject<>(s.name, Circuit.class);
    Circuit circuit = new FsBatchCircuit(subject);
    circuit.await();
    circuit.close();
  }

  @Benchmark
  public void batch_hot_await(BatchState s) {
    s.hotCircuit.await();
  }

  @Benchmark
  public Pipe<Integer> batch_hot_pipe_create(BatchState s) {
    return s.hotCircuit.pipe(Receptor.of(Integer.class));
  }

  @Benchmark
  public Pipe<Integer> batch_hot_pipe_create_with_flow(BatchState s) {
    return s.hotCircuit.pipe(Receptor.of(Integer.class), flow -> flow.guard(v -> v > 0));
  }

  @Benchmark
  public void batch_emit_single(BatchState s) {
    s.pipe.emit(INT_VAL);
  }

  @Benchmark
  public void batch_emit_single_await(BatchState s) {
    s.pipe.emit(INT_VAL);
    s.hotCircuit.await();
  }

  @Benchmark
  @OperationsPerInvocation(BATCH_SIZE)
  public void batch_emit_batch(BatchState s) {
    for (int i = 0; i < BATCH_SIZE; i++) {
      s.pipe.emit(INT_VAL + i);
    }
  }

  @Benchmark
  @OperationsPerInvocation(BATCH_SIZE)
  public void batch_emit_batch_await(BatchState s) {
    for (int i = 0; i < BATCH_SIZE; i++) {
      s.pipe.emit(INT_VAL + i);
    }
    s.hotCircuit.await();
  }

  @Benchmark
  @OperationsPerInvocation(BATCH_SIZE)
  public void batch_emit_with_flow_await(BatchState s) {
    for (int i = 0; i < BATCH_SIZE; i++) {
      s.pipeWithFlow.emit(INT_VAL + i);
    }
    s.hotCircuit.await();
  }

  @Benchmark
  @OperationsPerInvocation(BATCH_SIZE)
  public void batch_emit_chained(BatchState s) {
    for (int i = 0; i < BATCH_SIZE; i++) {
      s.chainedPipe.emit(INT_VAL + i);
    }
  }

  @Benchmark
  @OperationsPerInvocation(BATCH_SIZE)
  public void batch_emit_chained_await(BatchState s) {
    for (int i = 0; i < BATCH_SIZE; i++) {
      s.chainedPipe.emit(INT_VAL + i);
    }
    s.hotCircuit.await();
  }

  @Benchmark
  public Pipe<Integer> batch_conduit_percept(BatchState s) {
    return s.conduit.percept(s.channelName);
  }

  // =========================================================================
  // DISRUPTOR CIRCUIT BENCHMARKS
  // =========================================================================

  @Benchmark
  public void disruptor_create_and_close(DisruptorState s) {
    Subject<Circuit> subject = new FsSubject<>(s.name, Circuit.class);
    Circuit circuit = new FsDisruptorCircuit(subject);
    circuit.close();
  }

  @Benchmark
  public void disruptor_create_await_close(DisruptorState s) {
    Subject<Circuit> subject = new FsSubject<>(s.name, Circuit.class);
    Circuit circuit = new FsDisruptorCircuit(subject);
    circuit.await();
    circuit.close();
  }

  @Benchmark
  public void disruptor_hot_await(DisruptorState s) {
    s.hotCircuit.await();
  }

  @Benchmark
  public Pipe<Integer> disruptor_hot_pipe_create(DisruptorState s) {
    return s.hotCircuit.pipe(Receptor.of(Integer.class));
  }

  @Benchmark
  public Pipe<Integer> disruptor_hot_pipe_create_with_flow(DisruptorState s) {
    return s.hotCircuit.pipe(Receptor.of(Integer.class), flow -> flow.guard(v -> v > 0));
  }

  @Benchmark
  public void disruptor_emit_single(DisruptorState s) {
    s.pipe.emit(INT_VAL);
  }

  @Benchmark
  public void disruptor_emit_single_await(DisruptorState s) {
    s.pipe.emit(INT_VAL);
    s.hotCircuit.await();
  }

  @Benchmark
  @OperationsPerInvocation(BATCH_SIZE)
  public void disruptor_emit_batch(DisruptorState s) {
    for (int i = 0; i < BATCH_SIZE; i++) {
      s.pipe.emit(INT_VAL + i);
    }
  }

  @Benchmark
  @OperationsPerInvocation(BATCH_SIZE)
  public void disruptor_emit_batch_await(DisruptorState s) {
    for (int i = 0; i < BATCH_SIZE; i++) {
      s.pipe.emit(INT_VAL + i);
    }
    s.hotCircuit.await();
  }

  @Benchmark
  @OperationsPerInvocation(BATCH_SIZE)
  public void disruptor_emit_with_flow_await(DisruptorState s) {
    for (int i = 0; i < BATCH_SIZE; i++) {
      s.pipeWithFlow.emit(INT_VAL + i);
    }
    s.hotCircuit.await();
  }

  @Benchmark
  @OperationsPerInvocation(BATCH_SIZE)
  public void disruptor_emit_chained_await(DisruptorState s) {
    for (int i = 0; i < BATCH_SIZE; i++) {
      s.chainedPipe.emit(INT_VAL + i);
    }
    s.hotCircuit.await();
  }

  @Benchmark
  public Pipe<Integer> disruptor_conduit_percept(DisruptorState s) {
    return s.conduit.percept(s.channelName);
  }
}
