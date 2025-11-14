package io.fullerstack.substrates.benchmarks;

import io.humainary.substrates.api.Substrates;
import io.humainary.substrates.api.Substrates.Cortex;
import io.humainary.substrates.api.Substrates.Name;
import org.openjdk.jmh.annotations.*;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * JMH benchmarks for Name operations.
 *
 * Run with: mvn clean install && java -jar target/benchmarks.jar NameOperationsBenchmark
 * Or: mvn clean test-compile exec:java -Dexec.mainClass="org.openjdk.jmh.Main" -Dexec.args="NameOperationsBenchmark"
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 2, jvmArgs = {"-Xms2G", "-Xmx2G"})
public class NameOperationsBenchmark {

    private Cortex cortex;

    // Pre-created names for benchmarking
    private Name name1;
    private Name name2;
    private Name name3;
    private Name baseName;
    private Name deepName;
    private List<String> segments;

    @Setup
    public void setup() {
        cortex = Substrates.cortex();

        // Create test names
        name1 = cortex.name("test.name.one");
        name2 = cortex.name("test.name.two");
        name3 = cortex.name("level1.level2.level3.level4");
        baseName = cortex.name("base");
        deepName = cortex.name("level1.level2.level3.level4.level5");
        segments = List.of("part1", "part2", "part3");
    }

    @Benchmark
    public Name nameFromString() {
        return cortex.name("test.name.path");
    }

    @Benchmark
    public Name nameChaining() {
        return baseName.name("child");
    }

    @Benchmark
    public int nameComparison() {
        return name1.compareTo(name2);
    }

    @Benchmark
    public int nameDepth() {
        return deepName.depth();
    }

    @Benchmark
    public CharSequence namePath() {
        return deepName.path();
    }

    @Benchmark
    public Name nameFromIterable() {
        return cortex.name(segments);
    }

    @Benchmark
    public int nameHashCode() {
        return name1.hashCode();
    }

    @Benchmark
    public boolean nameEquals() {
        return name1.equals(name2);
    }

    // Additional deep hierarchy tests
    @Benchmark
    public Name nameDeepChaining() {
        return name3.name("level5").name("level6");
    }

    @Benchmark
    public int nameDeepComparison() {
        return name3.compareTo(deepName);
    }
}
