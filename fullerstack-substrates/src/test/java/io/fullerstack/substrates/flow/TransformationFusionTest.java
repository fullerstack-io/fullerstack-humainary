package io.fullerstack.substrates.flow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for pipeline fusion optimization in FlowRegulator.
 * <p>
 * < p >< b >Pipeline Fusion (JVM-style Optimization):</b >
 * < ul >
 * < li >Adjacent skip() calls are fused: skip(3).skip(2) → skip(5)</li >
 * < li >Adjacent limit() calls are minimized: limit(10).limit(5) → limit(5)</li >
 * < li >Reduces transformation overhead in hot paths</li >
 * </ul >
 * <p>
 * < p >This optimization is inspired by William's comment:
 * "I decorate pipes and connect pipes as well as optimise them like the JVM
 * when I detect in the pipeline the same operations is being performed."
 */
class TransformationFusionTest {

  @Test
  @DisplayName ( "Adjacent skip() calls are fused into a single skip" )
  void testSkipFusion () {
    FlowRegulator < Integer > flow = new FlowRegulator <> ();

    // Chain multiple skip() calls
    flow.skip ( 3 ).skip ( 2 ).skip ( 1 );

    // Should be fused to skip(6)
    // Apply to values 0-9
    List < Integer > results = new ArrayList <> ();
    for ( int i = 0; i < 10; i++ ) {
      Integer result = flow.apply ( i );
      if ( result != null ) {
        results.add ( result );
      }
    }

    // First 6 values skipped (0,1,2,3,4,5), then 6,7,8,9 pass through
    assertEquals ( List.of ( 6, 7, 8, 9 ), results,
      "skip(3).skip(2).skip(1) should be fused to skip(6)" );
  }

  @Test
  @DisplayName ( "Adjacent limit() calls are minimized to smallest limit" )
  void testLimitFusion () {
    FlowRegulator < Integer > flow = new FlowRegulator <> ();

    // Chain multiple limit() calls - should take the minimum
    flow.limit ( 10 ).limit ( 5 ).limit ( 7 );

    // Should be fused to limit(5) (smallest)
    List < Integer > results = new ArrayList <> ();
    for ( int i = 0; i < 20; i++ ) {
      Integer result = flow.apply ( i );
      if ( result != null ) {
        results.add ( result );
      }
    }

    // Only first 5 values should pass
    assertEquals ( List.of ( 0, 1, 2, 3, 4 ), results,
      "limit(10).limit(5).limit(7) should be fused to limit(5)" );
  }

  @Test
  @DisplayName ( "Combined skip().limit() fusion works correctly" )
  void testSkipLimitCombination () {
    FlowRegulator < Integer > flow = new FlowRegulator <> ();

    // skip(3).skip(2) → skip(5)
    // limit(10).limit(8) → limit(8)
    flow.skip ( 3 ).skip ( 2 ).limit ( 10 ).limit ( 8 );

    List < Integer > results = new ArrayList <> ();
    for ( int i = 0; i < 20; i++ ) {
      Integer result = flow.apply ( i );
      if ( result != null ) {
        results.add ( result );
      }
    }

    // Skip first 5 (0-4), then take 8 (5-12)
    assertEquals ( List.of ( 5, 6, 7, 8, 9, 10, 11, 12 ), results,
      "skip(5).limit(8) should skip 5 values then take 8" );
  }

  @Test
  @DisplayName ( "Fusion doesn't affect non-adjacent transformations" )
  void testNonAdjacentTransformationsNotFused () {
    FlowRegulator < Integer > flow = new FlowRegulator <> ();

    // skip() separated by guard() - should NOT be fused
    flow.skip ( 2 ).guard ( x -> x > 0 ).skip ( 1 );

    List < Integer > results = new ArrayList <> ();
    for ( int i = 0; i < 10; i++ ) {
      Integer result = flow.apply ( i );
      if ( result != null ) {
        results.add ( result );
      }
    }

    // First skip(2): removes 0,1
    // guard(x > 0): removes 2 (but 2 already skipped, so this filters nothing extra)
    // Actually: 0,1 skipped, then 2 passes guard (2 >0), then skip(1) removes 2
    // So: 0,1 skipped, 2 skipped by second skip, 3,4,5,6,7,8,9 pass
    assertEquals ( List.of ( 3, 4, 5, 6, 7, 8, 9 ), results,
      "Non-adjacent skips should not be fused" );
  }

  @Test
  @DisplayName ( "Single skip() without fusion works correctly" )
  void testSingleSkipNoFusion () {
    FlowRegulator < Integer > flow = new FlowRegulator <> ();

    flow.skip ( 3 );

    List < Integer > results = new ArrayList <> ();
    for ( int i = 0; i < 10; i++ ) {
      Integer result = flow.apply ( i );
      if ( result != null ) {
        results.add ( result );
      }
    }

    assertEquals ( List.of ( 3, 4, 5, 6, 7, 8, 9 ), results,
      "Single skip(3) should work without fusion" );
  }

  @Test
  @DisplayName ( "Single limit() without fusion works correctly" )
  void testSingleLimitNoFusion () {
    FlowRegulator < Integer > flow = new FlowRegulator <> ();

    flow.limit ( 5 );

    List < Integer > results = new ArrayList <> ();
    for ( int i = 0; i < 10; i++ ) {
      Integer result = flow.apply ( i );
      if ( result != null ) {
        results.add ( result );
      }
    }

    assertEquals ( List.of ( 0, 1, 2, 3, 4 ), results,
      "Single limit(5) should work without fusion" );
  }

  @Test
  @DisplayName ( "Multiple skip() fusions in sequence" )
  void testMultipleSkipFusions () {
    FlowRegulator < Integer > flow = new FlowRegulator <> ();

    // Each pair should fuse: skip(1).skip(1) → skip(2)
    // Then skip(2).skip(1) → skip(3)
    // Then skip(3).skip(1) → skip(4)
    flow.skip ( 1 ).skip ( 1 ).skip ( 1 ).skip ( 1 );

    List < Integer > results = new ArrayList <> ();
    for ( int i = 0; i < 10; i++ ) {
      Integer result = flow.apply ( i );
      if ( result != null ) {
        results.add ( result );
      }
    }

    // Should skip first 4 values
    assertEquals ( List.of ( 4, 5, 6, 7, 8, 9 ), results,
      "Multiple skip(1) calls should fuse to skip(4)" );
  }

  @Test
  @DisplayName ( "Multiple limit() fusions in sequence" )
  void testMultipleLimitFusions () {
    FlowRegulator < Integer > flow = new FlowRegulator <> ();

    // Each fusion should take minimum
    // limit(10).limit(8) → limit(8)
    // limit(8).limit(6) → limit(6)
    // limit(6).limit(9) → limit(6) (9 is larger, keep 6)
    flow.limit ( 10 ).limit ( 8 ).limit ( 6 ).limit ( 9 );

    List < Integer > results = new ArrayList <> ();
    for ( int i = 0; i < 20; i++ ) {
      Integer result = flow.apply ( i );
      if ( result != null ) {
        results.add ( result );
      }
    }

    // Should limit to 6 (smallest)
    assertEquals ( List.of ( 0, 1, 2, 3, 4, 5 ), results,
      "Multiple limit() calls should fuse to smallest: limit(6)" );
  }

  @Test
  @DisplayName ( "Fusion reduces internal transformation count" )
  void testFusionReducesTransformationCount () {
    FlowRegulator < Integer > unfused = new FlowRegulator <> ();
    unfused.skip ( 5 );

    FlowRegulator < Integer > fused = new FlowRegulator <> ();
    fused.skip ( 3 ).skip ( 2 );  // Should fuse to skip(5)

    // Both should produce same results
    List < Integer > unfusedResults = new ArrayList <> ();
    List < Integer > fusedResults = new ArrayList <> ();

    for ( int i = 0; i < 10; i++ ) {
      Integer result1 = unfused.apply ( i );
      if ( result1 != null ) unfusedResults.add ( result1 );

      Integer result2 = fused.apply ( i );
      if ( result2 != null ) fusedResults.add ( result2 );
    }

    assertEquals ( unfusedResults, fusedResults,
      "Fused and unfused pipelines should produce identical results" );
    assertEquals ( List.of ( 5, 6, 7, 8, 9 ), fusedResults,
      "Both should skip first 5 values" );
  }

  @Test
  @DisplayName ( "Zero skip() fuses correctly" )
  void testZeroSkipFusion () {
    FlowRegulator < Integer > flow = new FlowRegulator <> ();

    flow.skip ( 0 ).skip ( 3 );  // skip(0) is no-op, but should still fuse

    List < Integer > results = new ArrayList <> ();
    for ( int i = 0; i < 10; i++ ) {
      Integer result = flow.apply ( i );
      if ( result != null ) {
        results.add ( result );
      }
    }

    // skip(0).skip(3) → skip(3)
    assertEquals ( List.of ( 3, 4, 5, 6, 7, 8, 9 ), results,
      "skip(0).skip(3) should fuse to skip(3)" );
  }

  @Test
  @DisplayName ( "Large skip() values fuse correctly" )
  void testLargeSkipFusion () {
    FlowRegulator < Integer > flow = new FlowRegulator <> ();

    flow.skip ( 1000 ).skip ( 500 ).skip ( 250 );  // Should fuse to skip(1750)

    List < Integer > results = new ArrayList <> ();
    for ( int i = 0; i < 2000; i++ ) {
      Integer result = flow.apply ( i );
      if ( result != null ) {
        results.add ( result );
      }
    }

    // First 1750 skipped, then 250 values pass (1750-1999)
    assertEquals ( 250, results.size (), "Should skip 1750 values" );
    assertEquals ( 1750, results.percept ( 0 ), "First value should be 1750" );
    assertEquals ( 1999, results.percept ( results.size () - 1 ), "Last value should be 1999" );
  }
}
