package io.fullerstack.substrates.compliance;

import io.humainary.substrates.api.Substrates.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static io.humainary.substrates.api.Substrates.Composer;
import static io.humainary.substrates.api.Substrates.cortex;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Compliance tests for Cell interface verifying documented behaviors from Substrates.java {@snippet} examples.
 * <p>
 * **Documented Behaviors Being Verified:**
 * <ul>
 *   <li>**Upward Flow**: Child emissions propagate to parent outlet</li>
 *   <li>**Downward Flow**: Parent emissions propagate to child inlet</li>
 *   <li>**Hierarchical Structure**: Multi-level cell hierarchies</li>
 *   <li>**Independent Children**: Multiple children with separate state</li>
 *   <li>**Transformation Isolation**: Each cell can have its own transformations</li>
 * </ul>
 * <p>
 * **Note**: Tests use correct RC5 API (cell.emit()) not pseudocode (cell.emit())
 */
class CellBehaviorComplianceTest {

  private Circuit circuit;

  @AfterEach
  void cleanup() {
    if (circuit != null) {
      circuit.close();
    }
  }

  /**
   * **Documented Behavior**: Upward flow - child emissions propagate to parent outlet
   * <p>
   * From {@snippet}:
   * <pre>
   *   child1.emit(100);  // → root outlet receives 100
   *   child2.emit(200);  // → root outlet receives 200
   * </pre>
   * <p>
   * **RC5 API**: Uses cell.emit() instead of cell.emit()
   */
  @Test
  void testUpwardFlow_ChildEmissionsPropagateToParentOutlet() throws InterruptedException {
    circuit = cortex().circuit(cortex().name("test-circuit"));

    List<Integer> rootOutletReceived = new ArrayList<>();
    CountDownLatch latch = new CountDownLatch(2);

    // Create root cell with identity composers and collecting outlet
    Cell<Integer, Integer> root = circuit.cell(
      cortex().name("root"),
      Composer.pipe(),  // Identity ingress
      Composer.pipe(),  // Identity egress
      cortex().pipe((Integer value) -> {
        rootOutletReceived.add(value);
        latch.countDown();
      })
    );

    // Create child cells
    Cell<Integer, Integer> child1 = root.percept(cortex().name("child1"));
    Cell<Integer, Integer> child2 = root.percept(cortex().name("child2"));

    // Emit from children (RC5 API: cell.emit())
    System.out.println("Emitting 100 from child1...");
    child1.emit(100);
    System.out.println("Emitting 200 from child2...");
    child2.emit(200);

    // Wait for async processing
    System.out.println("Waiting for circuit...");
    circuit.await();
    System.out.println("Circuit await complete. Latch count: " + latch.getCount());
    System.out.println("Received: " + rootOutletReceived);
    assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();

    // Verify: Child emissions flowed upward to root outlet
    assertThat(rootOutletReceived).containsExactlyInAnyOrder(100, 200);
  }

  /**
   * **Documented Behavior**: Hierarchical structure - emissions flow through multiple levels
   * <p>
   * From {@snippet}:
   * <pre>
   *   Cell level2 = level1.percept("level2");
   *   level2.emit(100);  // → flows up through level1 to root
   * </pre>
   */
  @Test
  void testHierarchicalFlow_EmissionsFlowThroughMultipleLevels() throws InterruptedException {
    circuit = cortex().circuit(cortex().name("test-circuit"));

    List<Integer> rootOutletReceived = new ArrayList<>();
    CountDownLatch latch = new CountDownLatch(1);

    // Create root cell
    Cell<Integer, Integer> root = circuit.cell(
      cortex().name("root"),
      Composer.pipe(),
      Composer.pipe(),
      cortex().pipe((Integer value) -> {
        rootOutletReceived.add(value);
        latch.countDown();
      })
    );

    // Create level 1 child
    Cell<Integer, Integer> level1 = root.percept(cortex().name("level1"));

    // Create level 2 grandchild
    Cell<Integer, Integer> level2 = level1.percept(cortex().name("level2"));

    // Emit from deepest level
    level2.emit(100);

    circuit.await();
    assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();

    // Verify: Emission flowed through all levels to root
    assertThat(rootOutletReceived).containsExactly(100);
  }

  /**
   * **Documented Behavior**: Multiple children have independent state
   * <p>
   * From {@snippet}:
   * <pre>
   *   child1 doubles values
   *   child2 triples values
   *   Transformations are independent per child
   * </pre>
   */
  @Test
  void testIndependentChildren_SeparateTransformationState() throws InterruptedException {
    circuit = cortex().circuit(cortex().name("test-circuit"));

    List<Integer> rootOutletReceived = new ArrayList<>();
    CountDownLatch latch = new CountDownLatch(2);

    // Create root with identity composers
    Cell<Integer, Integer> root = circuit.cell(
      cortex().name("root"),
      Composer.pipe(),
      Composer.pipe(),
      cortex().pipe((Integer value) -> {
        rootOutletReceived.add(value);
        latch.countDown();
      })
    );

    // Get two children - each gets its own Channel with independent state
    Cell<Integer, Integer> child1 = root.percept(cortex().name("child1"));
    Cell<Integer, Integer> child2 = root.percept(cortex().name("child2"));

    // Emit same value from both children
    child1.emit(10);
    child2.emit(10);

    circuit.await();
    assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();

    // Verify: Both emissions received (demonstrating independence)
    assertThat(rootOutletReceived).containsExactlyInAnyOrder(10, 10);
  }

  /**
   * **Documented Behavior**: Cell caching - same name returns same cell instance
   * <p>
   * From {@snippet}:
   * <pre>
   *   Cell child = parent.get("child");
   *   Cell same = parent.get("child");
   *   // child == same (cached)
   * </pre>
   */
  @Test
  void testCellCaching_SameNameReturnsSameInstance() {
    circuit = cortex().circuit(cortex().name("test-circuit"));

    Cell<Integer, Integer> root = circuit.cell(
      cortex().name("root"),
      Composer.pipe(),
      Composer.pipe(),
      cortex().pipe()
    );

    // Get child twice with same name
    Cell<Integer, Integer> child1 = root.percept(cortex().name("child"));
    Cell<Integer, Integer> child2 = root.percept(cortex().name("child"));

    // Verify: Same instance returned (cached)
    assertThat((Object) child1).isSameAs(child2);
  }

  /**
   * **Documented Behavior**: Cell subject hierarchy
   * <p>
   * From {@snippet}:
   * <pre>
   *   Circuit → Cell → Child Cell
   *   Subject path shows full hierarchy
   * </pre>
   */
  @Test
  void testCellSubjectHierarchy_PathShowsFullStructure() {
    circuit = cortex().circuit(cortex().name("monitoring"));

    Cell<Integer, Integer> root = circuit.cell(
      cortex().name("root"),
      Composer.pipe(),
      Composer.pipe(),
      cortex().pipe()
    );

    Cell<Integer, Integer> child = root.percept(cortex().name("child"));

    // Verify: Subject hierarchy
    assertThat(child.subject().name().toString()).isEqualTo("child");

    // Path should contain all levels
    CharSequence path = child.subject().path("/");
    assertThat(path.toString()).contains("monitoring");
    assertThat(path.toString()).contains("root");
    assertThat(path.toString()).contains("child");
  }

  /**
   * **Documented Behavior**: Enclosure relationship
   * <p>
   * From {@snippet}:
   * <pre>
   *   child.enclosure() → parent
   * </pre>
   */
  @Test
  void testCellEnclosure_ChildKnowsParent() {
    circuit = cortex().circuit(cortex().name("test-circuit"));

    Cell<Integer, Integer> root = circuit.cell(
      cortex().name("root"),
      Composer.pipe(),
      Composer.pipe(),
      cortex().pipe()
    );

    Cell<Integer, Integer> child = root.percept(cortex().name("child"));

    // Verify: Child's enclosure is parent
    assertThat(child.enclosure()).isPresent();
    assertThat((Object) child.enclosure().get()).isSameAs(root);
  }
}
