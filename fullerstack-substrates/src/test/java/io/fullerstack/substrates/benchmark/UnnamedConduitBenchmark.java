package io.fullerstack.substrates.benchmark;

import io.humainary.substrates.api.Substrates;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

import static io.humainary.substrates.api.Substrates.Composer.pipe;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
public class UnnamedConduitBenchmark {

    private Substrates.Cortex cortex;
    private Substrates.Circuit hotCircuit;

    @Setup(Level.Trial)
    public void setupTrial() {
        cortex = Substrates.cortex();
    }

    @Setup(Level.Iteration)
    public void setupIteration() {
        hotCircuit = cortex.circuit();
    }

    @TearDown(Level.Iteration)
    public void tearDownIteration() {
        hotCircuit.close();
    }

    @Benchmark
    public Substrates.Conduit<Substrates.Pipe<Integer>, Integer> createUnnamedConduit() {
        return hotCircuit.conduit(pipe(Integer.class));
    }
}
