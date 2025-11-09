package io.fullerstack.substrates.flow;

import io.humainary.substrates.api.Substrates.*;
import io.fullerstack.substrates.name.InternedName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for FlowRegulator transformation pipeline.
 */
class FlowRegulatorTest {

  @Test
  void shouldPassAllEmissionsWithNoTransformations () {
    FlowRegulator < Integer > segment = new FlowRegulator <> ();

    assertThat ( segment.apply ( 1 ) ).isEqualTo ( 1 );
    assertThat ( segment.apply ( 2 ) ).isEqualTo ( 2 );
    assertThat ( segment.apply ( 3 ) ).isEqualTo ( 3 );
  }

  @Test
  void shouldFilterWithGuard () {
    Flow < Integer > segment = new FlowRegulator < Integer > ()
      .guard ( value -> value > 0 );

    FlowRegulator < Integer > impl = (FlowRegulator < Integer >) segment;

    assertThat ( impl.apply ( -1 ) ).isNull ();
    assertThat ( impl.apply ( 0 ) ).isNull ();
    assertThat ( impl.apply ( 1 ) ).isEqualTo ( 1 );
    assertThat ( impl.apply ( 5 ) ).isEqualTo ( 5 );
  }

  @Test
  void shouldLimitEmissions () {
    Flow < Integer > segment = new FlowRegulator < Integer > ()
      .limit ( 3 );

    FlowRegulator < Integer > impl = (FlowRegulator < Integer >) segment;

    assertThat ( impl.apply ( 1 ) ).isEqualTo ( 1 );
    assertThat ( impl.apply ( 2 ) ).isEqualTo ( 2 );
    assertThat ( impl.apply ( 3 ) ).isEqualTo ( 3 );
    assertThat ( impl.apply ( 4 ) ).isNull (); // Limit reached
    assertThat ( impl.apply ( 5 ) ).isNull (); // Still limited
  }

  @Test
  void shouldReplaceWithMapper () {
    Flow < Integer > segment = new FlowRegulator < Integer > ()
      .replace ( value -> value * 2 );

    FlowRegulator < Integer > impl = (FlowRegulator < Integer >) segment;

    assertThat ( impl.apply ( 1 ) ).isEqualTo ( 2 );
    assertThat ( impl.apply ( 5 ) ).isEqualTo ( 10 );
    assertThat ( impl.apply ( 10 ) ).isEqualTo ( 20 );
  }

  @Test
  void shouldReduceWithAccumulator () {
    Flow < Integer > segment = new FlowRegulator < Integer > ()
      .reduce ( 0, Integer::sum );

    FlowRegulator < Integer > impl = (FlowRegulator < Integer >) segment;

    assertThat ( impl.apply ( 1 ) ).isEqualTo ( 1 );   // 0 + 1
    assertThat ( impl.apply ( 2 ) ).isEqualTo ( 3 );   // 1 + 2
    assertThat ( impl.apply ( 3 ) ).isEqualTo ( 6 );   // 3 + 3
    assertThat ( impl.apply ( 4 ) ).isEqualTo ( 10 );  // 6 + 4
  }

  @Test
  void shouldFilterDuplicatesWithDiff () {
    Flow < Integer > segment = new FlowRegulator < Integer > ()
      .diff ();

    FlowRegulator < Integer > impl = (FlowRegulator < Integer >) segment;

    assertThat ( impl.apply ( 1 ) ).isEqualTo ( 1 );   // First value passes
    assertThat ( impl.apply ( 1 ) ).isNull ();       // Duplicate filtered
    assertThat ( impl.apply ( 2 ) ).isEqualTo ( 2 );   // Changed value passes
    assertThat ( impl.apply ( 2 ) ).isNull ();       // Duplicate filtered
    assertThat ( impl.apply ( 1 ) ).isEqualTo ( 1 );   // Changed back passes
  }

  @Test
  void shouldFilterDuplicatesWithDiffInitial () {
    Flow < Integer > segment = new FlowRegulator < Integer > ()
      .diff ( 1 );

    FlowRegulator < Integer > impl = (FlowRegulator < Integer >) segment;

    assertThat ( impl.apply ( 1 ) ).isNull ();       // Same as initial - filtered
    assertThat ( impl.apply ( 2 ) ).isEqualTo ( 2 );   // Different - passes
    assertThat ( impl.apply ( 2 ) ).isNull ();       // Duplicate - filtered
  }

  @Test
  void shouldSampleEveryNthEmission () {
    Flow < Integer > segment = new FlowRegulator < Integer > ()
      .sample ( 3 );

    FlowRegulator < Integer > impl = (FlowRegulator < Integer >) segment;

    assertThat ( impl.apply ( 1 ) ).isNull ();       // 1st - filtered
    assertThat ( impl.apply ( 2 ) ).isNull ();       // 2nd - filtered
    assertThat ( impl.apply ( 3 ) ).isEqualTo ( 3 );   // 3rd - passes
    assertThat ( impl.apply ( 4 ) ).isNull ();       // 4th - filtered
    assertThat ( impl.apply ( 5 ) ).isNull ();       // 5th - filtered
    assertThat ( impl.apply ( 6 ) ).isEqualTo ( 6 );   // 6th - passes
  }

  @Test
  void shouldPeekWithoutModifying () {
    List < Integer > peeked = new ArrayList <> ();
    Flow < Integer > segment = new FlowRegulator < Integer > ()
      .peek ( peeked::add )
      .guard ( value -> value > 0 );

    FlowRegulator < Integer > impl = (FlowRegulator < Integer >) segment;

    impl.apply ( -1 );
    impl.apply ( 5 );
    impl.apply ( 10 );

    assertThat ( peeked ).containsExactly ( -1, 5, 10 );
  }

  @Test
  void shouldChainMultipleTransformations () {
    // Guard > 0, multiply by 2, limit to 3
    Flow < Integer > segment = new FlowRegulator < Integer > ()
      .guard ( value -> value > 0 )
      .replace ( value -> value * 2 )
      .limit ( 3 );

    FlowRegulator < Integer > impl = (FlowRegulator < Integer >) segment;

    assertThat ( impl.apply ( -1 ) ).isNull ();      // Filtered by guard
    assertThat ( impl.apply ( 1 ) ).isEqualTo ( 2 );   // 1 * 2 = 2
    assertThat ( impl.apply ( 5 ) ).isEqualTo ( 10 );  // 5 * 2 = 10
    assertThat ( impl.apply ( 7 ) ).isEqualTo ( 14 );  // 7 * 2 = 14
    assertThat ( impl.apply ( 9 ) ).isNull ();       // Limit reached
  }

  @Test
  void shouldSiftAboveThreshold () {
    Flow < Integer > segment = new FlowRegulator < Integer > ()
      .sift ( Integer::compareTo, sift -> sift.above ( 5 ) );

    FlowRegulator < Integer > impl = (FlowRegulator < Integer >) segment;

    assertThat ( impl.apply ( 3 ) ).isNull ();
    assertThat ( impl.apply ( 5 ) ).isNull ();
    assertThat ( impl.apply ( 6 ) ).isEqualTo ( 6 );
    assertThat ( impl.apply ( 10 ) ).isEqualTo ( 10 );
  }

  @Test
  void shouldSiftBelowThreshold () {
    Flow < Integer > segment = new FlowRegulator < Integer > ()
      .sift ( Integer::compareTo, sift -> sift.below ( 5 ) );

    FlowRegulator < Integer > impl = (FlowRegulator < Integer >) segment;

    assertThat ( impl.apply ( 3 ) ).isEqualTo ( 3 );
    assertThat ( impl.apply ( 5 ) ).isNull ();
    assertThat ( impl.apply ( 6 ) ).isNull ();
  }

  @Test
  void shouldSiftInRange () {
    Flow < Integer > segment = new FlowRegulator < Integer > ()
      .sift ( Integer::compareTo, sift -> sift.range ( 5, 10 ) );

    FlowRegulator < Integer > impl = (FlowRegulator < Integer >) segment;

    assertThat ( impl.apply ( 3 ) ).isNull ();
    assertThat ( impl.apply ( 5 ) ).isEqualTo ( 5 );
    assertThat ( impl.apply ( 7 ) ).isEqualTo ( 7 );
    assertThat ( impl.apply ( 10 ) ).isEqualTo ( 10 );
    assertThat ( impl.apply ( 11 ) ).isNull ();
  }

  @Test
  void shouldSiftWithMin () {
    Flow < Integer > segment = new FlowRegulator < Integer > ()
      .sift ( Integer::compareTo, sift -> sift.min ( 5 ) );

    FlowRegulator < Integer > impl = (FlowRegulator < Integer >) segment;

    assertThat ( impl.apply ( 3 ) ).isNull ();
    assertThat ( impl.apply ( 5 ) ).isEqualTo ( 5 );
    assertThat ( impl.apply ( 10 ) ).isEqualTo ( 10 );
  }

  @Test
  void shouldSiftWithMax () {
    Flow < Integer > segment = new FlowRegulator < Integer > ()
      .sift ( Integer::compareTo, sift -> sift.max ( 10 ) );

    FlowRegulator < Integer > impl = (FlowRegulator < Integer >) segment;

    assertThat ( impl.apply ( 5 ) ).isEqualTo ( 5 );
    assertThat ( impl.apply ( 10 ) ).isEqualTo ( 10 );
    assertThat ( impl.apply ( 11 ) ).isNull ();
  }

  @Test
  void shouldRequireNonNullPredicate () {
    FlowRegulator < Integer > segment = new FlowRegulator <> ();

    assertThatThrownBy ( () -> segment.guard ( null ) )
      .isInstanceOf ( NullPointerException.class );
  }

  @Test
  void shouldRequireNonNullMapper () {
    FlowRegulator < Integer > segment = new FlowRegulator <> ();

    assertThatThrownBy ( () -> segment.replace ( null ) )
      .isInstanceOf ( NullPointerException.class );
  }

  @Test
  void shouldRequireNonNullAccumulator () {
    FlowRegulator < Integer > segment = new FlowRegulator <> ();

    assertThatThrownBy ( () -> segment.reduce ( 0, null ) )
      .isInstanceOf ( NullPointerException.class );
  }

  @Test
  void shouldRequireNonNegativeLimit () {
    FlowRegulator < Integer > segment = new FlowRegulator <> ();

    assertThatThrownBy ( () -> segment.limit ( -1 ) )
      .isInstanceOf ( IllegalArgumentException.class );
  }

  @Test
  void shouldRequirePositiveSampleRate () {
    FlowRegulator < Integer > segment = new FlowRegulator <> ();

    assertThatThrownBy ( () -> segment.sample ( 0 ) )
      .isInstanceOf ( IllegalArgumentException.class );

    assertThatThrownBy ( () -> segment.sample ( -1 ) )
      .isInstanceOf ( IllegalArgumentException.class );
  }

  // ==================== FUSION OPTIMIZATION TESTS ====================

  @Test
  void shouldFuseAdjacentSkipCalls () {
    // skip(3).skip(2) should be optimized to skip(5)
    FlowRegulator < Integer > segment = (FlowRegulator < Integer >) new FlowRegulator < Integer > ()
      .skip ( 3 )
      .skip ( 2 );

    // Should skip first 5 emissions
    assertThat ( segment.apply ( 1 ) ).isNull (); // skip 1
    assertThat ( segment.apply ( 2 ) ).isNull (); // skip 2
    assertThat ( segment.apply ( 3 ) ).isNull (); // skip 3
    assertThat ( segment.apply ( 4 ) ).isNull (); // skip 4
    assertThat ( segment.apply ( 5 ) ).isNull (); // skip 5
    assertThat ( segment.apply ( 6 ) ).isEqualTo ( 6 ); // first emission
    assertThat ( segment.apply ( 7 ) ).isEqualTo ( 7 );

    // Verify optimization: should only have 1 transformation (fused)
    assertThat ( segment.transformations ).hasSize ( 1 );
  }

  @Test
  void shouldFuseAdjacentLimitCalls () {
    // limit(10).limit(5) should be optimized to limit(5) (minimum)
    FlowRegulator < Integer > segment = (FlowRegulator < Integer >) new FlowRegulator < Integer > ()
      .limit ( 10 )
      .limit ( 5 );

    // Should only emit 5 items
    assertThat ( segment.apply ( 1 ) ).isEqualTo ( 1 );
    assertThat ( segment.apply ( 2 ) ).isEqualTo ( 2 );
    assertThat ( segment.apply ( 3 ) ).isEqualTo ( 3 );
    assertThat ( segment.apply ( 4 ) ).isEqualTo ( 4 );
    assertThat ( segment.apply ( 5 ) ).isEqualTo ( 5 );
    assertThat ( segment.apply ( 6 ) ).isNull (); // limit reached
    assertThat ( segment.apply ( 7 ) ).isNull (); // limit reached

    // Verify optimization: should only have 1 transformation (fused)
    assertThat ( segment.transformations ).hasSize ( 1 );
  }

  @Test
  void shouldFuseAdjacentGuardCalls () {
    // guard(x -> x > 0).guard(x -> x < 100) should be optimized to guard(x -> x > 0 && x < 100)
    FlowRegulator < Integer > segment = (FlowRegulator < Integer >) new FlowRegulator < Integer > ()
      .guard ( value -> value > 0 )
      .guard ( value -> value < 100 );

    // Should pass values between 0 and 100 (exclusive)
    assertThat ( segment.apply ( -5 ) ).isNull ();  // fails first guard
    assertThat ( segment.apply ( 0 ) ).isNull ();   // fails first guard
    assertThat ( segment.apply ( 1 ) ).isEqualTo ( 1 );   // passes both
    assertThat ( segment.apply ( 50 ) ).isEqualTo ( 50 ); // passes both
    assertThat ( segment.apply ( 99 ) ).isEqualTo ( 99 ); // passes both
    assertThat ( segment.apply ( 100 ) ).isNull (); // fails second guard
    assertThat ( segment.apply ( 200 ) ).isNull (); // fails second guard

    // Verify optimization: should only have 1 transformation (fused)
    assertThat ( segment.transformations ).hasSize ( 1 );
  }

  @Test
  void shouldFuseAdjacentReplaceCalls () {
    // replace(x -> x * 2).replace(x -> x + 1) should be optimized to replace(x -> (x * 2) + 1)
    FlowRegulator < Integer > segment = (FlowRegulator < Integer >) new FlowRegulator < Integer > ()
      .replace ( value -> value * 2 )
      .replace ( value -> value + 1 );

    // Should apply both transformations in sequence
    assertThat ( segment.apply ( 5 ) ).isEqualTo ( 11 );  // (5 * 2) + 1 = 11
    assertThat ( segment.apply ( 10 ) ).isEqualTo ( 21 ); // (10 * 2) + 1 = 21
    assertThat ( segment.apply ( 0 ) ).isEqualTo ( 1 );   // (0 * 2) + 1 = 1

    // Verify optimization: should only have 1 transformation (fused)
    assertThat ( segment.transformations ).hasSize ( 1 );
  }

  @Test
  void shouldFuseAdjacentSampleIntCalls () {
    // sample(2).sample(3) should be optimized to sample(6) using LCM
    FlowRegulator < Integer > segment = (FlowRegulator < Integer >) new FlowRegulator < Integer > ()
      .sample ( 2 )
      .sample ( 3 );

    // Should emit every 6th item (LCM of 2 and 3)
    // Note: counter increments with each apply(), not based on the value passed
    assertThat ( segment.apply ( 1 ) ).isNull ();   // counter=1, 1 % 6 != 0
    assertThat ( segment.apply ( 2 ) ).isNull ();   // counter=2, 2 % 6 != 0
    assertThat ( segment.apply ( 3 ) ).isNull ();   // counter=3, 3 % 6 != 0
    assertThat ( segment.apply ( 4 ) ).isNull ();   // counter=4, 4 % 6 != 0
    assertThat ( segment.apply ( 5 ) ).isNull ();   // counter=5, 5 % 6 != 0
    assertThat ( segment.apply ( 6 ) ).isEqualTo ( 6 );   // counter=6, 6 % 6 == 0 ✓
    assertThat ( segment.apply ( 7 ) ).isNull ();   // counter=7, 7 % 6 != 0
    assertThat ( segment.apply ( 8 ) ).isNull ();   // counter=8, 8 % 6 != 0
    assertThat ( segment.apply ( 9 ) ).isNull ();   // counter=9, 9 % 6 != 0
    assertThat ( segment.apply ( 10 ) ).isNull ();  // counter=10, 10 % 6 != 0
    assertThat ( segment.apply ( 11 ) ).isNull ();  // counter=11, 11 % 6 != 0
    assertThat ( segment.apply ( 12 ) ).isEqualTo ( 12 ); // counter=12, 12 % 6 == 0 ✓

    // Verify optimization: should only have 1 transformation (fused)
    assertThat ( segment.transformations ).hasSize ( 1 );
  }

  @Test
  void shouldFuseMultipleSkipCalls () {
    // skip(2).skip(3).skip(5) should be optimized to skip(10)
    FlowRegulator < Integer > segment = (FlowRegulator < Integer >) new FlowRegulator < Integer > ()
      .skip ( 2 )
      .skip ( 3 )
      .skip ( 5 );

    // Should skip first 10 emissions
    for ( int i = 1; i <= 10; i++ ) {
      assertThat ( segment.apply ( i ) ).isNull ();
    }
    assertThat ( segment.apply ( 11 ) ).isEqualTo ( 11 );
    assertThat ( segment.apply ( 12 ) ).isEqualTo ( 12 );

    // Verify optimization: should only have 1 transformation (all fused)
    assertThat ( segment.transformations ).hasSize ( 1 );
  }

  @Test
  void shouldFuseMultipleGuardCalls () {
    // guard(x > 0).guard(x < 100).guard(x % 2 == 0)
    // should be optimized to single guard with all conditions
    FlowRegulator < Integer > segment = (FlowRegulator < Integer >) new FlowRegulator < Integer > ()
      .guard ( value -> value > 0 )
      .guard ( value -> value < 100 )
      .guard ( value -> value % 2 == 0 ); // even numbers only

    assertThat ( segment.apply ( -5 ) ).isNull ();  // fails > 0
    assertThat ( segment.apply ( 1 ) ).isNull ();   // fails % 2 == 0
    assertThat ( segment.apply ( 2 ) ).isEqualTo ( 2 );   // passes all
    assertThat ( segment.apply ( 50 ) ).isEqualTo ( 50 ); // passes all
    assertThat ( segment.apply ( 99 ) ).isNull ();  // fails % 2 == 0
    assertThat ( segment.apply ( 100 ) ).isNull (); // fails < 100

    // Verify optimization: should only have 1 transformation (all fused)
    assertThat ( segment.transformations ).hasSize ( 1 );
  }

  @Test
  void shouldNotFuseNonAdjacentTransformations () {
    // skip(2), then guard, then skip(3) - middle two should NOT fuse
    FlowRegulator < Integer > segment = (FlowRegulator < Integer >) new FlowRegulator < Integer > ()
      .skip ( 2 )
      .guard ( value -> value > 0 )
      .skip ( 3 );

    // Should have 3 separate transformations (no fusion across different types)
    assertThat ( segment.transformations ).hasSize ( 3 );
  }

  @Test
  void shouldHandleComplexFusionChain () {
    // Mix of fusable and non-fusable operations
    FlowRegulator < Integer > segment = (FlowRegulator < Integer >) new FlowRegulator < Integer > ()
      .skip ( 1 )      // 1
      .skip ( 1 )      // fuse → skip(2)
      .guard ( v -> v > 0 )  // 2
      .guard ( v -> v < 100 ) // fuse → guard(v > 0 && v < 100)
      .replace ( v -> v * 2 )  // 3
      .replace ( v -> v + 1 ); // fuse → replace(v -> (v * 2) + 1)

    // Should have 3 transformations: skip(2), guard(combined), replace(composed)
    assertThat ( segment.transformations ).hasSize ( 3 );

    // Test behavior: skip first 2, then apply guards and replacements
    assertThat ( segment.apply ( 1 ) ).isNull (); // skip
    assertThat ( segment.apply ( 2 ) ).isNull (); // skip
    assertThat ( segment.apply ( 5 ) ).isEqualTo ( 11 );  // (5 * 2) + 1
    assertThat ( segment.apply ( 50 ) ).isEqualTo ( 101 ); // (50 * 2) + 1
    assertThat ( segment.apply ( 0 ) ).isNull ();  // fails guard (> 0)
    assertThat ( segment.apply ( 100 ) ).isNull (); // fails guard (< 100)
  }
}
