package io.fullerstack.substrates.circuit;

import static io.humainary.substrates.api.Substrates.*;
import io.humainary.substrates.api.Substrates.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

/**
 * Simple test to debug Circuit creation hang.
 */
class SimpleCircuitTest {

  @Test
  @Timeout(value = 5, unit = TimeUnit.SECONDS)
  void testCircuitCreation() {
    System.out.println("Test starting...");
    Cortex cortex = cortex();
    System.out.println("Got cortex");

    Circuit circuit = cortex.circuit(cortex.name("test-circuit"));
    System.out.println("Created circuit");

    circuit.close();
    System.out.println("Closed circuit");
  }
}
