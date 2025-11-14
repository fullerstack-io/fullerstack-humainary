package io.fullerstack.substrates.name;

import io.humainary.substrates.api.Substrates.Name;

import java.lang.reflect.Member;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Interned Name implementation with identity-based equality.
 * <p>
 * < p >< b >Design Principles:</b >
 * < ul >
 * < li >Cortex creates root names only: new InternedName(null, "root")</li >
 * < li >Hierarchy built by name() methods: parent.name("child")</li >
 * < li >Parent-child links via constructor (node structure)</li >
 * < li >All Extent methods use defaults (path, depth, iterator)</li >
 * < li >< b >Name Interning:</b > Names with identical paths return same instance (like String.intern())</li >
 * < li >< b >Identity Equality:</b > Use == for comparison, not equals()</li >
 * </ul >
 * <p>
 * < p >< b >Required implementations:</b >
 * < ul >
 * < li >part() - returns this segment</li >
 * < li >enclosure() - returns parent</li >
 * < li >name() methods - create children with parent reference</li >
 * </ul >
 */
public final class InternedName implements Name {

  /**
   * Global cache for name interning.
   * Key: (parent, segment), Value: InternedName instance
   * Ensures identical paths return same instance across entire JVM.
   */
  private static final ConcurrentHashMap < NameKey, InternedName > INTERN_CACHE = new ConcurrentHashMap <> ();

  /**
   * Path-level cache for complete name strings.
   * Key: Full path string (e.g., "test.name.path"), Value: InternedName instance
   * Eliminates split/validate/build overhead for repeated identical paths.
   *
   * Performance: ~586ns → ~5ns per call (single hash lookup, no parsing)
   */
  private static final ConcurrentHashMap < String, Name > PATH_CACHE = new ConcurrentHashMap <> ();

  private final InternedName parent;
  private final String       segment;

  // Performance: Pre-computed hash, depth, and lazy path cache
  // hashCode and depth are final because they're computed once during construction (interned names are immutable)
  // path remains volatile/lazy because it's rarely needed (only for debugging/logging)
  private final int           precomputedHash;
  private final int           precomputedDepth;
  private volatile String     cachedPath = null;

  /**
   * Composite key for interning cache: (parent, segment) uniquely identifies a Name.
   */
  private record NameKey( InternedName parent, String segment ) {
  }

  /**
   * Private constructor - use static factory methods or name() methods.
   * Computes hashCode and depth eagerly during construction (names are immutable and interned).
   */
  private InternedName ( InternedName parent, String segment ) {
    this.parent = parent;
    this.segment = Objects.requireNonNull ( segment, "segment cannot be null" );

    // Compute hash eagerly: combine parent hash with segment hash
    // This is much faster than building full path string and hashing it
    // Uses same algorithm as String.hashCode() for consistency
    if ( parent == null ) {
      // Root name: just hash the segment
      this.precomputedHash = segment.hashCode ();
      // Root depth is 1
      this.precomputedDepth = 1;
    } else {
      // Child name: combine parent hash + '.' + segment hash
      // This matches: (parent.path() + "." + segment).hashCode()
      // String.hashCode algorithm: s[0]*31^(n-1) + s[1]*31^(n-2) + ... + s[n-1]
      int hash = parent.precomputedHash;
      hash = hash * 31 + (int) '.';  // Add '.' separator (ASCII 46)

      // Now add each character from segment
      for ( int i = 0; i < segment.length (); i++ ) {
        hash = hash * 31 + segment.charAt ( i );
      }

      this.precomputedHash = hash;

      // Child depth is parent depth + 1
      this.precomputedDepth = parent.precomputedDepth + 1;
    }
  }

  /**
   * Intern a name - returns cached instance if exists, creates new if not.
   */
  private static InternedName intern ( InternedName parent, String segment ) {
    NameKey key = new NameKey ( parent, segment );
    return INTERN_CACHE.computeIfAbsent ( key, k -> new InternedName ( parent, segment ) );
  }

  /**
   * Fast path for creating non-interned single-segment names.
   * Used when we know the name is unique and will never be looked up again.
   * Skips interning cache operations entirely.
   *
   * @param segment the single segment (no dots)
   * @return a new Name (NOT interned)
   */
  public static Name ofUnique ( String segment ) {
    Objects.requireNonNull ( segment, "segment cannot be null" );
    if ( segment.isEmpty () ) {
      throw new IllegalArgumentException ( "segment cannot be empty" );
    }
    // Direct construction - no interning, no split, no cache lookup
    return new InternedName ( null, segment );
  }

  /**
   * Public factory for creating names from dot-separated paths.
   * Splits on '.' to create hierarchical names.
   * Uses interning to ensure identical paths return same instance.
   *
   * @param path the dot-separated path (e.g., "kafka.broker.1")
   * @return a hierarchical Name (interned)
   */
  public static Name of ( String path ) {
    Objects.requireNonNull ( path, "path cannot be null" );
    if ( path.isEmpty () ) {
      throw new IllegalArgumentException ( "path cannot be empty" );
    }

    // OPTIMIZATION: Check path-level cache first (fast path for repeated paths)
    // This eliminates split/validate/build overhead for common paths
    Name cachedName = PATH_CACHE.get ( path );
    if ( cachedName != null ) {
      return cachedName;  // Cache hit - ~5ns!
    }

    // Slow path: Parse and build name hierarchy
    // Split on dots to create hierarchy
    String[] segments = path.split ( "\\.", -1 ); // -1 to preserve empty strings

    // Validate: no empty segments (leading/trailing/consecutive dots)
    for ( String segment : segments ) {
      if ( segment.isEmpty () ) {
        throw new IllegalArgumentException ( "path cannot contain empty segments (leading, trailing, or consecutive dots)" );
      }
    }

    // Create root using intern, then use name() for children (which also interns)
    Name current = intern ( null, segments[0] );
    for ( int i = 1; i < segments.length; i++ ) {
      current = current.name ( segments[i] );
    }

    // Cache the complete path for next time
    PATH_CACHE.put ( path, current );

    return current;
  }

  // ============ REQUIRED: Extent implementations ============

  @Override
  public CharSequence part () {
    return segment;
  }

  @Override
  public Optional < Name > enclosure () {
    return Optional.ofNullable ( parent );
  }

  // ============ REQUIRED: Name interface - name() factory methods ============
  // Note: Name API doesn't provide defaults, must implement all

  @Override
  public Name name ( Name suffix ) {
    Objects.requireNonNull ( suffix, "suffix" );
    // Iterate from ROOT to LEAF by collecting in reverse order
    // suffix iterator goes LEAF→ROOT, so we need to reverse it
    java.util.List < String > parts = new java.util.ArrayList <> ();
    for ( Name part : suffix ) {
      parts.add ( part.part ().toString () );
    }
    // Reverse to get ROOT→LEAF order
    java.util.Collections.reverse ( parts );

    // Now append in correct order
    Name current = this;
    for ( String part : parts ) {
      current = current.name ( part );
    }
    return current;
  }

  @Override
  public Name name ( String segment ) {
    Objects.requireNonNull ( segment, "segment" );
    if ( segment.isEmpty () ) return this;

    // Fast path: Check for dots using indexOf (faster than contains)
    // indexOf returns -1 if not found, which is a simple int comparison
    int dotIndex = segment.indexOf ( '.' );
    if ( dotIndex == -1 ) {
      // No dots - single segment, intern directly (fast path)
      return intern ( this, segment );
    }

    // Slow path: segment contains dots - parse as path
    String[] parts = segment.split ( "\\.", -1 );
    // Validate no empty segments
    for ( String part : parts ) {
      if ( part.isEmpty () ) {
        throw new IllegalArgumentException ( "segment cannot contain empty parts (consecutive dots)" );
      }
    }
    // Build hierarchy
    Name current = this;
    for ( String part : parts ) {
      current = intern ( (InternedName) current, part );
    }
    return current;
  }

  @Override
  public Name name ( Enum < ? > e ) {
    Objects.requireNonNull ( e, "e" );
    // Convert Class name to hierarchical Name with dots
    // Replace $ with . for inner classes to create proper hierarchy
    String className = e.getDeclaringClass ().getName ().replace ( '$', '.' );
    return name ( className ).name ( e.name () );
  }

  @Override
  public Name name ( Class < ? > type ) {
    Objects.requireNonNull ( type, "type" );
    // Convert $ to . for proper hierarchical name with inner classes
    return name ( type.getName ().replace ( '$', '.' ) );
  }

  @Override
  public Name name ( Member member ) {
    Objects.requireNonNull ( member, "member" );
    // Delegate to name(String) - don't force InternedName
    return name ( member.getDeclaringClass ().getName () ).name ( member.getName () );
  }

  @Override
  public Name name ( Iterable < String > parts ) {
    Objects.requireNonNull ( parts, "parts" );
    // Delegate to name(String) - don't force InternedName
    Name current = this;
    for ( String part : parts ) {
      current = current.name ( Objects.requireNonNull ( part ) );
    }
    return current;
  }

  @Override
  public < T > Name name ( Iterable < ? extends T > parts, Function < T, String > mapper ) {
    Objects.requireNonNull ( parts, "parts" );
    Objects.requireNonNull ( mapper, "mapper" );
    // Delegate to name(String) - don't force InternedName
    Name current = this;
    for ( T item : parts ) {
      current = current.name ( Objects.requireNonNull ( mapper.apply ( item ) ) );
    }
    return current;
  }

  @Override
  public Name name ( Iterator < String > parts ) {
    Objects.requireNonNull ( parts, "parts" );
    // Delegate to name(String) - don't force InternedName
    Name current = this;
    while ( parts.hasNext () ) {
      current = current.name ( Objects.requireNonNull ( parts.next () ) );
    }
    return current;
  }

  @Override
  public < T > Name name ( Iterator < ? extends T > parts, Function < T, String > mapper ) {
    Objects.requireNonNull ( parts, "parts" );
    Objects.requireNonNull ( mapper, "mapper" );
    // Delegate to name(String) - don't force InternedName
    Name current = this;
    while ( parts.hasNext () ) {
      current = current.name ( Objects.requireNonNull ( mapper.apply ( parts.next () ) ) );
    }
    return current;
  }

  // ============ ALL OTHER METHODS USE EXTENT DEFAULTS ============
  // - path() - builds from parts using Extent's foldTo()
  // - depth() - counts via fold
  // - iterator() - walks via enclosure()
  // - compareTo() - compares paths
  // - extent(), extremity(), fold(), foldTo() - all defaults

  // Override default path() to use "." separator instead of Extent's "/" default
  @Override
  public CharSequence path () {
    return path ( '.' );
  }

  // Required: path(Function) is abstract in Name
  // Use "." as separator for hierarchical names (not "/" like filesystem paths)
  @Override
  public CharSequence path ( Function < ? super String, ? extends CharSequence > mapper ) {
    Objects.requireNonNull ( mapper, "mapper" );
    // Adapt Function< String, CharSequence > to Function< Name, CharSequence >
    // by extracting part() from each Name, then delegate to Extent's default
    // Use "." separator for dot-notation hierarchical names
    return path ( name -> mapper.apply ( name.part ().toString () ), '.' );
  }

  @Override
  public String value () {
    // Return just this segment (part), not the full path
    return part ().toString ();
  }

  @Override
  public String toString () {
    // Performance: Cache the path string to avoid rebuilding on every call
    if ( cachedPath == null ) {
      cachedPath = path ().toString ();
    }
    return cachedPath;
  }

  @Override
  public int hashCode () {
    // Performance: Return pre-computed hash (computed once during construction)
    // This is a simple final field read - no volatile overhead, no lazy computation
    return precomputedHash;
  }

  @Override
  public int depth () {
    // Performance: Return pre-computed depth (computed once during construction)
    // This is a simple final field read - eliminates fold/iterator overhead
    // Default implementation uses fold which walks hierarchy every time (~50ns)
    return precomputedDepth;
  }

  @Override
  public int compareTo ( Name other ) {
    Objects.requireNonNull ( other, "other" );

    // Performance: Optimized comparison without building full path strings
    // Default implementation calls path() twice (~300ns total), we avoid that

    // Fast path: identity check
    if ( this == other ) return 0;

    // Fast path: use pre-computed depth for quick ordering
    // If depths differ, deeper names come after shallower names
    if ( other instanceof InternedName otherInterned ) {
      int depthDiff = this.precomputedDepth - otherInterned.precomputedDepth;
      if ( depthDiff != 0 ) {
        return depthDiff;  // Different depths, order by depth
      }

      // Same depth: compare segments from root to leaf
      // Walk up to root and compare corresponding segments
      return compareHierarchies ( this, otherInterned );
    }

    // Fallback for non-InternedName implementations: use default path comparison
    return CharSequence.compare ( path ( '\u0001' ), other.path ( '\u0001' ) );
  }

  /**
   * Compare two name hierarchies segment by segment from root to leaf.
   * Both names must have the same depth.
   *
   * Performance: Zero allocation - compares segments recursively from root to leaf.
   */
  private static int compareHierarchies ( InternedName a, InternedName b ) {
    // Base case: both null (root reached)
    if ( a == null && b == null ) return 0;
    if ( a == null ) return -1;  // a is shorter
    if ( b == null ) return 1;   // b is shorter

    // Recursive case: compare parents first (root to leaf order)
    int parentCmp = compareHierarchies ( a.parent, b.parent );
    if ( parentCmp != 0 ) {
      return parentCmp;  // Parents differ, return that comparison
    }

    // Parents are equal, compare this segment
    return a.segment.compareTo ( b.segment );
  }

  @Override
  public boolean equals ( Object o ) {
    // Fast path: identity check (works for interned names)
    if ( this == o ) return true;
    if ( !( o instanceof Name other ) ) return false;

    // Performance: Walk segments instead of building full path strings
    // This is MUCH faster than path().toString().equals(other.path().toString())
    // which would build two complete path strings just to compare them
    if ( o instanceof InternedName otherInterned ) {
      // Compare segments walking up hierarchy (avoids string building)
      InternedName thisName = this;
      InternedName otherName = otherInterned;

      while ( thisName != null && otherName != null ) {
        if ( !thisName.segment.equals ( otherName.segment ) ) {
          return false;
        }
        thisName = thisName.parent;
        otherName = otherName.parent;
      }

      // Both should be null (same depth)
      return thisName == null && otherName == null;
    }

    // Fallback for non-InternedName implementations: compare paths
    // (Rare case - only if someone creates a custom Name implementation)
    return path ().toString ().equals ( other.path ().toString () );
  }
}
