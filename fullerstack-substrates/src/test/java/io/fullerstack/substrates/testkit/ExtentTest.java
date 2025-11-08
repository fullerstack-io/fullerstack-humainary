// Copyright (c) 2025 William David Louth

package io.fullerstack.substrates.testkit;

import io.humainary.substrates.api.Substrates.Extent;
import io.humainary.substrates.api.Substrates.NotNull;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/// A comprehensive test kit for the [Extent] interface.
///
/// This test kit validates all default methods of the Extent interface using
/// a custom test implementation to ensure the default behavior works correctly
/// without relying on existing implementations like Name or Subject that might
/// override these methods.
///
/// Tests cover:
/// - Basic extent operations (extent, enclosure, extremity)
/// - Depth calculation
/// - Iteration (forward and reverse)
/// - Folding operations (fold and foldTo)
/// - Path representation with various separators and mappers
/// - Comparison operations
/// - Stream operations
/// - Within (containment) checks
/// - Edge cases and null handling
///
/// @author wlouth
/// @since 1.0

final class ExtentTest {

  // ===========================
  // Test Implementation
  // ===========================

  @SuppressWarnings ( "EqualsWithItself" )
  @Test
  void testCompareTo () {

    final var a = TestExtent.root ( "a" );
    final var b = TestExtent.root ( "b" );
    final var c = TestExtent.root ( "c" );

    assertTrue ( a.compareTo ( b ) < 0 );
    assertTrue ( c.compareTo ( b ) > 0 );
    assertEquals ( 0, a.compareTo ( a ) );

  }

  // ===========================
  // Basic Extent Tests
  // ===========================

  @SuppressWarnings ( "DataFlowIssue" )
  @Test
  void testCompareToNullThrows () {

    final var extent = TestExtent.root ( "test" );

    assertThrows (
      NullPointerException.class,
      () -> extent.compareTo ( null )
    );

  }

  @Test
  void testCompareToSameHierarchy () {

    final var root1 = TestExtent.root ( "root" );
    final var child1 = root1.child ( "child" );

    final var root2 = TestExtent.root ( "root" );
    final var child2 = root2.child ( "child" );

    assertEquals ( 0, child1.compareTo ( child2 ) );

  }

  @Test
  void testCompareToWithHierarchy () {

    final var root = TestExtent.root ( "root" );
    final var child = root.child ( "child" );

    assertTrue ( root.compareTo ( child ) < 0 );
    assertTrue ( child.compareTo ( root ) > 0 );

  }

  @Test
  void testConsistencyBetweenOperations () {

    final var a = TestExtent.root ( "a" );
    final var b = a.child ( "b" );
    final var c = b.child ( "c" );
    final var d = c.child ( "d" );

    // Depth should match stream count
    assertEquals ( d.depth (), d.stream ().count () );

    // Fold count should match depth
    final var foldCount = d.fold (
      _ -> 1,
      ( acc, _ ) -> acc + 1
    );
    assertEquals ( d.depth (), foldCount );

    // Iterator count should match depth
    final Iterator < TestExtent > iterator = d.iterator ();
    var iteratorCount = 0;
    while ( iterator.hasNext () ) {
      iterator.next ();
      iteratorCount++;
    }
    assertEquals ( d.depth (), iteratorCount );

  }

  @Test
  void testDepthRoot () {

    final var root = TestExtent.root ( "root" );
    assertEquals ( 1, root.depth () );

  }

  // ===========================
  // Depth Tests
  // ===========================

  @Test
  void testDepthWithChildren () {

    final var root = TestExtent.root ( "a" );
    final var b = root.child ( "b" );
    final var c = b.child ( "c" );
    final var d = c.child ( "d" );

    assertEquals ( 1, root.depth () );
    assertEquals ( 2, b.depth () );
    assertEquals ( 3, c.depth () );
    assertEquals ( 4, d.depth () );

  }

  @Test
  void testEnclosureConsumer () {

    final var root = TestExtent.root ( "root" );
    final var child = root.child ( "child" );
    final var result = new String[1];

    child.enclosure ( parent -> result[0] = parent.part ().toString () );

    assertEquals ( "root", result[0] );

  }

  // ===========================
  // Extremity Tests
  // ===========================

  @Test
  void testEnclosureConsumerNotCalledMultipleTimes () {

    final var root = TestExtent.root ( "root" );
    final var child = root.child ( "child" );

    final var counter = new AtomicInteger ( 0 );
    child.enclosure ( _ -> counter.incrementAndGet () );

    assertEquals ( 1, counter.get () );

  }

  @Test
  void testEnclosureConsumerOnRoot () {

    final var root = TestExtent.root ( "root" );
    final var called = new boolean[1];

    root.enclosure ( _ -> called[0] = true );

    assertFalse ( called[0] );

  }

  @Test
  void testEnclosureOnChild () {

    final var root = TestExtent.root ( "root" );
    final var child = root.child ( "child" );

    final var enclosure = child.enclosure ();
    assertTrue ( enclosure.isPresent () );
    assertSame ( root, enclosure.get () );

  }

  // ===========================
  // Iterator Tests
  // ===========================

  @Test
  void testEnclosureOnRoot () {

    final var root = TestExtent.root ( "root" );
    assertTrue ( root.enclosure ().isEmpty () );

  }

  @Test
  void testExtent () {

    final var extent = TestExtent.root ( "test" );
    assertSame ( extent, extent.extent () );

  }

  @Test
  void testExtentWithEmptyString () {

    final var root = TestExtent.root ( "" );
    assertEquals ( "", root.part () );
    assertEquals ( "", root.path ().toString () );

  }

  @Test
  void testExtentWithSpecialCharacters () {

    final var root = TestExtent.root ( "test-name_123" );
    final var child = root.child ( "child@#$" );

    assertEquals ( "test-name_123/child@#$", child.path ().toString () );

  }

  // ===========================
  // Fold Tests
  // ===========================

  @Test
  void testExtremity () {

    final var root = TestExtent.root ( "root" );
    final var child = root.child ( "child" );
    final var grandchild = child.child ( "grandchild" );

    assertSame ( root, grandchild.extremity () );
    assertSame ( root, child.extremity () );
    assertSame ( root, root.extremity () );

  }

  @Test
  void testExtremityDeepHierarchy () {

    var current = TestExtent.root ( "level0" );

    for ( var i = 1; i <= 10; i++ ) {
      current = current.child ( "level" + i );
    }

    final var root = current.extremity ();
    assertEquals ( "level0", root.part () );

  }

  @Test
  void testExtremityOnRoot () {

    final var root = TestExtent.root ( "root" );
    assertSame ( root, root.extremity () );

  }

  @Test
  void testFoldAccumulation () {

    final var root = TestExtent.root ( "10" );
    final var child = root.child ( "20" );
    final var grandchild = child.child ( "30" );

    final var sum = grandchild.fold (
      e -> Integer.parseInt ( e.part ().toString () ),
      ( acc, e ) -> acc + Integer.parseInt ( e.part ().toString () )
    );

    assertEquals ( 60, sum );

  }

  // ===========================
  // FoldTo Tests
  // ===========================

  @Test
  void testFoldCount () {

    final var root = TestExtent.root ( "a" );
    final var child = root.child ( "b" );
    final var grandchild = child.child ( "c" );

    final var count = grandchild.fold (
      _ -> 1,
      ( acc, _ ) -> acc + 1
    );

    assertEquals ( 3, count );

  }

  @Test
  void testFoldOnRoot () {

    final var root = TestExtent.root ( "test" );

    final var result = root.fold (
      e -> e.part ().toString (),
      ( acc, e ) -> acc + "/" + e.part ()
    );

    assertEquals ( "test", result );

  }

  @Test
  void testFoldRightToLeft () {

    final var a = TestExtent.root ( "a" );
    final var b = a.child ( "b" );
    final var c = b.child ( "c" );

    // fold goes from right (c) to left (a)
    final var result = c.fold (
      e -> e.part ().toString (),
      ( acc, e ) -> e.part () + "/" + acc
    );

    assertEquals ( "a/b/c", result );

  }

  @Test
  void testFoldToLeftToRight () {

    final var a = TestExtent.root ( "a" );
    final var b = a.child ( "b" );
    final var c = b.child ( "c" );

    // foldTo goes from left (a) to right (c)
    final var result = c.foldTo (
      e -> e.part ().toString (),
      ( acc, e ) -> acc + "." + e.part ()
    );

    assertEquals ( "a.b.c", result );

  }

  // ===========================
  // Path Tests
  // ===========================

  @Test
  void testFoldToOnRoot () {

    final var root = TestExtent.root ( "single" );

    final var result = root.foldTo (
      e -> e.part ().toString (),
      ( acc, e ) -> acc + "." + e.part ()
    );

    assertEquals ( "single", result );

  }

  @Test
  void testFoldToVsFoldOrder () {

    final var a = TestExtent.root ( "first" );
    final var b = a.child ( "second" );
    final var c = b.child ( "third" );

    // fold builds right-to-left
    final var foldResult = c.fold (
      e -> e.part ().toString (),
      ( acc, e ) -> e.part () + "." + acc
    );

    // foldTo builds left-to-right
    final var foldToResult = c.foldTo (
      e -> e.part ().toString (),
      ( acc, e ) -> acc + "." + e.part ()
    );

    assertEquals ( foldResult, foldToResult );
    assertEquals ( "first.second.third", foldResult );

  }

  @SuppressWarnings ( "DataFlowIssue" )
  @Test
  void testFoldToWithNullAccumulatorThrows () {

    final var extent = TestExtent.root ( "test" );

    assertThrows (
      NullPointerException.class,
      () -> extent.foldTo ( _ -> 1, null )
    );

  }

  @SuppressWarnings ( "DataFlowIssue" )
  @Test
  void testFoldToWithNullInitializerThrows () {

    final var extent = TestExtent.root ( "test" );

    assertThrows (
      NullPointerException.class,
      () -> extent.foldTo ( null, ( acc, _ ) -> acc )
    );

  }

  @Test
  void testFoldToWithTransformation () {

    final var root = TestExtent.root ( "one" );
    final var child = root.child ( "two" );

    final var result = child.foldTo (
      e -> e.part ().toString ().toUpperCase (),
      ( acc, e ) -> acc + "-" + e.part ().toString ().toUpperCase ()
    );

    assertEquals ( "ONE-TWO", result );

  }

  @SuppressWarnings ( "DataFlowIssue" )
  @Test
  void testFoldWithNullAccumulatorThrows () {

    final var extent = TestExtent.root ( "test" );

    assertThrows (
      NullPointerException.class,
      () -> extent.fold ( _ -> 1, null )
    );

  }

  // ===========================
  // Stream Tests
  // ===========================

  @SuppressWarnings ( "DataFlowIssue" )
  @Test
  void testFoldWithNullInitializerThrows () {

    final var extent = TestExtent.root ( "test" );

    assertThrows (
      NullPointerException.class,
      () -> extent.fold ( null, ( acc, _ ) -> acc )
    );

  }

  @Test
  void testIterator () {

    final var a = TestExtent.root ( "a" );
    final var b = a.child ( "b" );
    final var c = b.child ( "c" );

    final var iterator = c.iterator ();

    assertTrue ( iterator.hasNext () );
    assertSame ( c, iterator.next () );

    assertTrue ( iterator.hasNext () );
    assertSame ( b, iterator.next () );

    assertTrue ( iterator.hasNext () );
    assertSame ( a, iterator.next () );

    assertFalse ( iterator.hasNext () );

  }

  @Test
  void testIteratorMultiplePasses () {

    final var root = TestExtent.root ( "a" );
    final var child = root.child ( "b" );

    // First iteration
    final var values1 = new ArrayList < String > ();
    child.iterator ().forEachRemaining (
      e -> values1.add ( e.part ().toString () )
    );

    // Second iteration
    final var values2 = new ArrayList < String > ();
    child.iterator ().forEachRemaining (
      e -> values2.add ( e.part ().toString () )
    );

    assertEquals ( values1, values2 );
    assertEquals ( List.of ( "b", "a" ), values1 );

  }

  @Test
  void testIteratorOnRoot () {

    final var root = TestExtent.root ( "root" );
    final var iterator = root.iterator ();

    assertTrue ( iterator.hasNext () );
    assertSame ( root, iterator.next () );
    assertFalse ( iterator.hasNext () );

  }

  @Test
  void testIteratorThrowsWhenExhausted () {

    final var root = TestExtent.root ( "root" );
    final var iterator = root.iterator ();

    iterator.next (); // consume the only element

    assertThrows ( NoSuchElementException.class, iterator::next );

  }

  // ===========================
  // CompareTo Tests
  // ===========================

  @Test
  void testLongHierarchyChain () {

    var current = TestExtent.root ( "level0" );

    for ( var i = 1; i <= 100; i++ ) {
      current = current.child ( "level" + i );
    }

    assertEquals ( 101, current.depth () );
    assertEquals ( 101L, current.stream ().count () );
    assertEquals ( "level0", current.extremity ().part () );

  }

  @Test
  void testMultipleChildrenNotInIterator () {

    final var root = TestExtent.root ( "root" );
    final var child1 = root.child ( "child1" );
    final var child2 = root.child ( "child2" );

    // child1's iterator should only include child1 and root
    final var values1 = child1.stream ()
      .map ( e -> e.part ().toString () )
      .toList ();

    assertEquals ( List.of ( "child1", "root" ), values1 );

    // child2's iterator should only include child2 and root
    final var values2 = child2.stream ()
      .map ( e -> e.part ().toString () )
      .toList ();

    assertEquals ( List.of ( "child2", "root" ), values2 );

  }

  @Test
  void testPathDefaultSeparator () {

    final var a = TestExtent.root ( "a" );
    final var b = a.child ( "b" );
    final var c = b.child ( "c" );

    assertEquals ( "a/b/c", c.path ().toString () );

  }

  @Test
  void testPathOnRoot () {

    final var root = TestExtent.root ( "root" );
    assertEquals ( "root", root.path ().toString () );

  }

  // ===========================
  // Within Tests
  // ===========================

  @Test
  void testPathWithCharSeparator () {

    final var root = TestExtent.root ( "x" );
    final var child = root.child ( "y" );

    assertEquals ( "x-y", child.path ( '-' ).toString () );

  }

  @Test
  void testPathWithMapper () {

    final var root = TestExtent.root ( "hello" );
    final var child = root.child ( "world" );

    final var result = child.path (
      e -> e.part ().toString ().toUpperCase (),
      '/'
    );

    assertEquals ( "HELLO/WORLD", result.toString () );

  }

  @Test
  void testPathWithMapperAndStringSeparator () {

    final var a = TestExtent.root ( "foo" );
    final var b = a.child ( "bar" );

    final var result = b.path (
      e -> e.part ().toString ().toUpperCase (),
      " -> "
    );

    assertEquals ( "FOO -> BAR", result.toString () );

  }

  @SuppressWarnings ( "DataFlowIssue" )
  @Test
  void testPathWithNullMapperAndStringSeparatorThrows () {

    final var extent = TestExtent.root ( "test" );

    assertThrows (
      NullPointerException.class,
      () -> extent.path ( null, "::" )
    );

    assertThrows (
      NullPointerException.class,
      () -> extent.path ( Extent::part, null )
    );

  }

  @SuppressWarnings ( "DataFlowIssue" )
  @Test
  void testPathWithNullMapperThrows () {

    final var extent = TestExtent.root ( "test" );

    assertThrows (
      NullPointerException.class,
      () -> extent.path ( null, '/' )
    );

  }

  @SuppressWarnings ( "DataFlowIssue" )
  @Test
  void testPathWithNullStringSeparatorThrows () {

    final var extent = TestExtent.root ( "test" );

    assertThrows (
      NullPointerException.class,
      () -> extent.path ( null )
    );

  }

  // ===========================
  // Edge Cases and Integration Tests
  // ===========================

  @Test
  void testPathWithStringSeparator () {

    final var a = TestExtent.root ( "a" );
    final var b = a.child ( "b" );
    final var c = b.child ( "c" );

    assertEquals ( "a::b::c", c.path ( "::" ).toString () );

  }

  @Test
  void testStream () {

    final var a = TestExtent.root ( "a" );
    final var b = a.child ( "b" );
    final var c = b.child ( "c" );

    final var values = c.stream ()
      .map ( e -> e.part ().toString () )
      .toList ();

    assertEquals ( List.of ( "c", "b", "a" ), values );

  }

  @Test
  void testStreamCount () {

    final var root = TestExtent.root ( "a" );
    final var child = root.child ( "b" );
    final var grandchild = child.child ( "c" );

    assertEquals ( 3L, grandchild.stream ().count () );
    assertEquals ( 2L, child.stream ().count () );
    assertEquals ( 1L, root.stream ().count () );

  }

  @Test
  void testStreamMatchesDepth () {

    final var root = TestExtent.root ( "a" );
    final var child = root.child ( "b" );
    final var grandchild = child.child ( "c" );

    assertEquals ( grandchild.depth (), grandchild.stream ().count () );
    assertEquals ( child.depth (), child.stream ().count () );
    assertEquals ( root.depth (), root.stream ().count () );

  }

  @Test
  void testStreamMatchesFold () {

    final var root = TestExtent.root ( "a" );
    final var child = root.child ( "b" );
    final var grandchild = child.child ( "c" );

    final var streamCount = grandchild.stream ().count ();

    final var foldCount = grandchild.fold (
      _ -> 1,
      ( acc, _ ) -> acc + 1
    );

    assertEquals ( streamCount, (long) foldCount );

  }

  @Test
  void testStreamOperations () {

    final var a = TestExtent.root ( "alpha" );
    final var b = a.child ( "beta" );
    final var c = b.child ( "gamma" );

    final var maxLength = c.stream ()
      .map ( e -> e.part ().toString () )
      .mapToInt ( String::length )
      .max ()
      .orElse ( 0 );

    assertEquals ( 5, maxLength ); // "alpha" and "gamma" are 5 chars

    final var hasShortName = c.stream ()
      .anyMatch ( e -> e.part ().length () < 5 );

    assertTrue ( hasShortName ); // "beta" is 4 chars

  }

  @Test
  void testWithin () {

    final var root = TestExtent.root ( "root" );
    final var child = root.child ( "child" );
    final var grandchild = child.child ( "grandchild" );

    assertTrue ( child.within ( root ) );
    assertTrue ( grandchild.within ( root ) );
    assertTrue ( grandchild.within ( child ) );

  }

  @Test
  void testWithinDeepHierarchy () {

    var current = TestExtent.root ( "level0" );
    final var root = current;

    for ( var i = 1; i <= 10; i++ ) {
      current = current.child ( "level" + i );
    }

    assertTrue ( current.within ( root ) );

  }

  @SuppressWarnings ( "DataFlowIssue" )
  @Test
  void testWithinNullThrows () {

    final var extent = TestExtent.root ( "test" );

    assertThrows (
      NullPointerException.class,
      () -> extent.within ( null )
    );

  }

  @Test
  void testWithinReverse () {

    final var root = TestExtent.root ( "root" );
    final var child = root.child ( "child" );

    assertFalse ( root.within ( child ) );

  }

  @Test
  void testWithinSelf () {

    final var root = TestExtent.root ( "root" );
    assertFalse ( root.within ( root ) );

  }

  @Test
  void testWithinUnrelated () {

    final var tree1 = TestExtent.root ( "tree1" );
    final var tree2 = TestExtent.root ( "tree2" );

    assertFalse ( tree1.within ( tree2 ) );
    assertFalse ( tree2.within ( tree1 ) );

  }

  /// A simple test implementation of Extent for testing default methods.
  /// Represents a hierarchical structure of string values.

  private record TestExtent( String value, TestExtent parent )
    implements Extent < TestExtent, TestExtent > {

    private TestExtent (
      final String value
    ) {

      this ( value, null );

    }

    /// Creates a root extent with no parent

    static TestExtent root (
      final String value
    ) {

      return new TestExtent ( value );

    }

    /// Returns the parent (enclosure) of this extent

    @NotNull
    @Override
    public Optional < TestExtent > enclosure () {

      return Optional.ofNullable ( parent );

    }

    /// Returns the part (value) of this extent

    @NotNull
    @Override
    public CharSequence part () {

      return value;

    }

    /// Creates a child extent

    TestExtent child (
      final String value
    ) {

      return new TestExtent ( value, this );

    }

  }

}
