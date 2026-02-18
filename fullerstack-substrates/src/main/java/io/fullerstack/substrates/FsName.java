package io.fullerstack.substrates;

import io.humainary.substrates.api.Substrates.Identity;
import io.humainary.substrates.api.Substrates.Name;
import io.humainary.substrates.api.Substrates.Provided;

import java.lang.reflect.Member;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/// A hierarchical dot-separated name using path-less interning.
/// No path field stored - toString() computed lazily and cached.
/// Memory-efficient: only stores segment + parent reference.
@Identity
@Provided
public final class FsName implements Name {

  /// Counter for generating unique anonymous names.
  private static final AtomicLong ANONYMOUS_COUNTER = new AtomicLong ( 0 );

  /// Unified cache: interned path string → FsName. Single lookup for all paths.
  /// Uses ConcurrentHashMap with high initial capacity to minimize rehashing.
  /// The concurrencyLevel=1 optimizes for single-writer scenarios.
  private static final ConcurrentHashMap < String, FsName >     NAME_CACHE = new ConcurrentHashMap <> ( 256, 0.75f, 1 );
  /// Enum cache: Enum instance → FsName. Avoids redundant getDeclaringClass() +
  /// getCanonicalName() calls.
  private static final ConcurrentHashMap < Enum < ? >, FsName > ENUM_CACHE = new ConcurrentHashMap <> ( 64, 0.75f, 1 );
  private static final char                                     FULLSTOP   = '.';
  private final        String                                   segment;
  /// Parent reference - null for root names.
  private final        FsName                                   parent;
  /// Cached depth - computed at construction, avoids traversal.
  private final        int                                      depth;
  /// Path string - computed at construction, never null.
  private final        String                                   path;
  /// Cached identity hash code - avoids System.identityHashCode() call on every
  /// lookup.
  private final        int                                      cachedHash;

  // =========================================================================
  // Per-node children cache for fast chaining
  // Simple array with linear scan - O(n) but n is typically <10
  // =========================================================================
  private volatile FsName[] childNodes = new FsName[0];
  private final    Object   childLock  = new Object ();

  /// Private constructor for root names (depth=1, no parent).
  private FsName ( String segment ) {
    this.segment = segment;
    this.parent = null;
    this.depth = 1;
    this.path = segment; // Root: path = segment
    this.cachedHash = System.identityHashCode ( this );
  }

  /// Private constructor with known parent (depth=parent.depth+1).
  private FsName ( String segment, FsName parent ) {
    this.segment = segment;
    this.parent = parent;
    this.depth = parent.depth + 1;
    this.path = parent.path + FULLSTOP + segment;
    this.cachedHash = System.identityHashCode ( this );
  }

  // =========================================================================
  // Static factory methods - all Name creation logic lives here
  // =========================================================================

  /// Parses and interns a path string with validation.
  /// Cache hit path is a single ConcurrentHashMap.get() - O(1).
  public static FsName parse ( String path ) {
    // Hot path: single cache lookup - no null check, no validation
    // Assumes most calls are cache hits
    FsName cached = NAME_CACHE.get ( path );
    if ( cached != null )
      return cached;

    // Cold path: cache miss - validate and build
    return parseSlowPath ( path );
  }

  /// Slow path for cache miss. Validates and creates new FsName.
  private static FsName parseSlowPath ( String path ) {
    Objects.requireNonNull ( path, "path must not be null" );
    if ( path.isEmpty () ) {
      throw new IllegalArgumentException ( "Name path cannot be empty" );
    }

    // Check if single segment (no dots)
    int dot = path.indexOf ( FULLSTOP );
    if ( dot < 0 ) {
      // Single segment - create root and cache atomically
      return NAME_CACHE.computeIfAbsent ( path, FsName::new );
    }

    // Multi-segment: validate and intern
    return validateAndIntern ( path );
  }

  /// Validates path and interns it. Called only on cache miss.
  private static FsName validateAndIntern ( String path ) {
    if ( path.isEmpty () ) {
      throw new IllegalArgumentException ( "Name path cannot be empty" );
    }
    if ( path.charAt ( 0 ) == FULLSTOP ) {
      throw new IllegalArgumentException ( "Name path cannot start with a dot: " + path );
    }
    if ( path.charAt ( path.length () - 1 ) == FULLSTOP ) {
      throw new IllegalArgumentException ( "Name path cannot end with a dot: " + path );
    }
    if ( path.contains ( ".." ) ) {
      throw new IllegalArgumentException ( "Name path cannot contain consecutive dots: " + path );
    }
    return intern ( path );
  }

  /// Interns a name by full path with eager parent chain building.
  /// Builds the tree structure via parent→child relationships.
  static FsName intern ( String path ) {
    // Check cache first
    FsName cached = NAME_CACHE.get ( path );
    if ( cached != null )
      return cached;

    // Build parent chain eagerly
    int dot = path.lastIndexOf ( FULLSTOP );
    if ( dot > 0 ) {
      // Has parent - intern parent first (recursive), then create child with parent
      FsName parent = intern ( path.substring ( 0, dot ) );
      String segment = path.substring ( dot + 1 );

      // Use parent's internChild to register in local children array
      FsName child = parent.internChild ( segment );
      // Cache for future lookups
      NAME_CACHE.putIfAbsent ( child.path, child );
      return child;
    } else {
      // Root name - no parent, depth=1
      return NAME_CACHE.computeIfAbsent ( path, FsName::new );
    }
  }

  /// Creates an anonymous name with a unique suffix.
  /// Used for pipes and other components that don't have explicit names.
  static FsName anonymous ( String prefix ) {
    return parse ( prefix + "-" + ANONYMOUS_COUNTER.incrementAndGet () );
  }

  /// Creates a Name from an Enum (fully qualified: declaring.class.CONSTANT).
  static FsName fromEnum ( Enum < ? > e ) {
    Objects.requireNonNull ( e, "enum must not be null" );
    // Check enum cache first (hot path optimization)
    FsName cached = ENUM_CACHE.get ( e );
    if ( cached != null )
      return cached;
    // Cache miss: compute and cache
    Class < ? > declClass = e.getDeclaringClass ();
    String canonical = declClass.getCanonicalName ();
    String className = canonical != null ? canonical : declClass.getName ();
    FsName name = intern ( className + FULLSTOP + e.name () );
    ENUM_CACHE.putIfAbsent ( e, name );
    return name;
  }

  /// Creates a Name from a Class.
  static FsName fromClass ( Class < ? > type ) {
    Objects.requireNonNull ( type, "type must not be null" );
    String canonical = type.getCanonicalName ();
    return intern ( canonical != null ? canonical : type.getName () );
  }

  /// Creates a Name from a Member (declaring class + member name).
  static FsName fromMember ( Member member ) {
    Objects.requireNonNull ( member, "member must not be null" );
    Class < ? > declClass = member.getDeclaringClass ();
    String canonical = declClass.getCanonicalName ();
    String className = canonical != null ? canonical : declClass.getName ();
    return intern ( className + FULLSTOP + member.getName () );
  }

  /// Creates a Name from an Iterable of parts.
  /// Uses indexed access for List inputs to avoid iterator allocation.
  /// First element validated by parse(); subsequent elements use internChild
  /// with inline guard for null/empty/dot edge cases.
  @SuppressWarnings ( "unchecked" )
  static FsName fromIterable ( Iterable < String > parts ) {
    Objects.requireNonNull ( parts, "parts must not be null" );
    if ( parts instanceof java.util.List < String > list ) {
      return fromList ( list );
    }
    Iterator < String > it = parts.iterator ();
    if ( !it.hasNext () ) {
      throw new IllegalArgumentException ( "parts must not be empty" );
    }
    FsName current = parse ( it.next () );
    while ( it.hasNext () ) {
      String part = it.next ();
      if ( part == null || part.isEmpty () || part.indexOf ( FULLSTOP ) >= 0 ) {
        current = (FsName) current.name ( part );
      } else {
        current = current.internChild ( part );
      }
    }
    return current;
  }

  /// Fast path for List inputs — indexed access avoids iterator allocation.
  private static FsName fromList ( java.util.List < String > parts ) {
    int size = parts.size ();
    if ( size == 0 ) {
      throw new IllegalArgumentException ( "parts must not be empty" );
    }
    FsName current = parse ( parts.get ( 0 ) );
    for ( int i = 1; i < size; i++ ) {
      String part = parts.get ( i );
      if ( part == null || part.isEmpty () || part.indexOf ( FULLSTOP ) >= 0 ) {
        current = (FsName) current.name ( part );
      } else {
        current = current.internChild ( part );
      }
    }
    return current;
  }

  /// Creates a Name from an Iterable with mapper.
  /// Uses indexed access for List inputs to avoid iterator allocation.
  @SuppressWarnings ( "unchecked" )
  static < T > FsName fromIterable ( Iterable < ? extends T > parts, Function < T, String > mapper ) {
    Objects.requireNonNull ( parts, "parts must not be null" );
    Objects.requireNonNull ( mapper, "mapper must not be null" );
    if ( parts instanceof java.util.List < ? > list ) {
      return fromListMapped ( (java.util.List < ? extends T >) list, mapper );
    }
    Iterator < ? extends T > it = parts.iterator ();
    if ( !it.hasNext () ) {
      throw new IllegalArgumentException ( "parts must not be empty" );
    }
    FsName current = parse ( mapper.apply ( it.next () ) );
    while ( it.hasNext () ) {
      String part = mapper.apply ( it.next () );
      if ( part == null || part.isEmpty () || part.indexOf ( FULLSTOP ) >= 0 ) {
        current = (FsName) current.name ( part );
      } else {
        current = current.internChild ( part );
      }
    }
    return current;
  }

  /// Fast path for List inputs with mapper — indexed access avoids iterator allocation.
  private static < T > FsName fromListMapped ( java.util.List < ? extends T > parts, Function < T, String > mapper ) {
    int size = parts.size ();
    if ( size == 0 ) {
      throw new IllegalArgumentException ( "parts must not be empty" );
    }
    FsName current = parse ( mapper.apply ( parts.get ( 0 ) ) );
    for ( int i = 1; i < size; i++ ) {
      String part = mapper.apply ( parts.get ( i ) );
      if ( part == null || part.isEmpty () || part.indexOf ( FULLSTOP ) >= 0 ) {
        current = (FsName) current.name ( part );
      } else {
        current = current.internChild ( part );
      }
    }
    return current;
  }

  /// Creates a Name from an Iterator of parts.
  /// First element validated by parse(); subsequent elements use internChild
  /// with inline guard for null/empty/dot edge cases.
  static FsName fromIterator ( Iterator < String > parts ) {
    Objects.requireNonNull ( parts, "parts must not be null" );
    if ( !parts.hasNext () ) {
      throw new IllegalArgumentException ( "parts must not be empty" );
    }
    FsName current = parse ( parts.next () );
    while ( parts.hasNext () ) {
      String part = parts.next ();
      if ( part == null || part.isEmpty () || part.indexOf ( FULLSTOP ) >= 0 ) {
        current = (FsName) current.name ( part );
      } else {
        current = current.internChild ( part );
      }
    }
    return current;
  }

  /// Creates a Name from an Iterator with mapper.
  /// First element validated by parse(); subsequent elements use internChild
  /// with inline guard for null/empty/dot edge cases.
  static < T > FsName fromIterator ( Iterator < ? extends T > parts, Function < T, String > mapper ) {
    Objects.requireNonNull ( parts, "parts must not be null" );
    Objects.requireNonNull ( mapper, "mapper must not be null" );
    if ( !parts.hasNext () ) {
      throw new IllegalArgumentException ( "parts must not be empty" );
    }
    FsName current = parse ( mapper.apply ( parts.next () ) );
    while ( parts.hasNext () ) {
      String part = mapper.apply ( parts.next () );
      if ( part == null || part.isEmpty () || part.indexOf ( FULLSTOP ) >= 0 ) {
        current = (FsName) current.name ( part );
      } else {
        current = current.internChild ( part );
      }
    }
    return current;
  }

  // =========================================================================
  // Instance methods - extend existing Name with suffix
  // =========================================================================

  /// Extends this name with a single segment (no dots in segment).
  /// Uses equals() for string comparison - simple and fast for short segments.
  private FsName internChild ( String childSegment ) {
    // Fast path: linear scan with equals() - short segments are fast to compare
    FsName[] nodes = childNodes;
    for ( int i = 0; i < nodes.length; i++ ) {
      if ( nodes[i].segment.equals ( childSegment ) ) {
        return nodes[i];
      }
    }

    // Slow path: synchronized creation
    synchronized ( childLock ) {
      // Double-check after lock
      nodes = childNodes;
      for ( int i = 0; i < nodes.length; i++ ) {
        if ( nodes[i].segment.equals ( childSegment ) ) {
          return nodes[i];
        }
      }

      // Create child with path computed at construction
      // Constructor will intern the segment
      FsName child = new FsName ( childSegment, this );

      // Append to children array
      FsName[] newNodes = new FsName[nodes.length + 1];
      System.arraycopy ( nodes, 0, newNodes, 0, nodes.length );
      newNodes[nodes.length] = child;
      childNodes = newNodes;

      // Also add to global cache for direct path lookups
      NAME_CACHE.putIfAbsent ( child.path, child );

      return child;
    }
  }

  @Override
  public String part () {
    return segment;
  }

  @Override
  public Optional < Name > enclosure () {
    // Direct return - Optional is lightweight, no caching needed
    return Optional.ofNullable ( parent );
  }

  /// Optimized iterator that avoids Optional allocation per iteration.
  /// Iterates from this name (leaf) up to root.
  @Override
  public Iterator < Name > iterator () {
    return new Iterator <> () {
      private FsName current = FsName.this;

      @Override
      public boolean hasNext () {
        return current != null;
      }

      @Override
      public Name next () {
        if ( current == null ) {
          throw new java.util.NoSuchElementException ();
        }
        FsName result = current;
        current = current.parent; // Direct field access - no Optional allocation
        return result;
      }
    };
  }

  @Override
  public Name name ( Name suffix ) {
    // Walk suffix segments and chain them
    FsName nano = (FsName) suffix;
    // Fast path for single-segment suffix (most common)
    if ( nano.depth == 1 ) {
      return internChild ( nano.segment );
    }
    return chainFrom ( nano );
  }

  /// Chain segments from another FsName onto this one.
  private FsName chainFrom ( FsName suffix ) {
    // Collect suffix segments in order (root to leaf)
    FsName[] segments = new FsName[suffix.depth];
    FsName n = suffix;
    for ( int i = suffix.depth - 1; i >= 0; i-- ) {
      segments[i] = n;
      n = n.parent;
    }
    // Chain each segment
    FsName current = this;
    for ( FsName seg : segments ) {
      current = current.internChild ( seg.segment );
    }
    return current;
  }

  @Override
  public Name name ( String suffix ) {
    // Validate suffix
    Objects.requireNonNull ( suffix, "suffix must not be null" );
    if ( suffix.isEmpty () ) {
      throw new IllegalArgumentException ( "Name suffix cannot be empty" );
    }
    if ( suffix.charAt ( 0 ) == FULLSTOP ) {
      throw new IllegalArgumentException ( "Name suffix cannot start with a dot: " + suffix );
    }
    if ( suffix.charAt ( suffix.length () - 1 ) == FULLSTOP ) {
      throw new IllegalArgumentException ( "Name suffix cannot end with a dot: " + suffix );
    }
    if ( suffix.contains ( ".." ) ) {
      throw new IllegalArgumentException ( "Name suffix cannot contain consecutive dots: " + suffix );
    }

    // Check for dots in suffix
    int dot = suffix.indexOf ( FULLSTOP );
    if ( dot < 0 ) {
      // Simple case: single segment - use internChild for efficiency
      return internChild ( suffix );
    }
    // Multi-segment: split and chain each segment
    FsName current = this;
    int start = 0;
    while ( true ) {
      int nextDot = suffix.indexOf ( FULLSTOP, start );
      if ( nextDot < 0 ) {
        // Last segment
        return current.internChild ( suffix.substring ( start ) );
      }
      current = current.internChild ( suffix.substring ( start, nextDot ) );
      start = nextDot + 1;
    }
  }

  @Override
  public Name name ( Enum < ? > e ) {
    // Extension: just append enum constant name (not fully qualified)
    return internChild ( e.name () );
  }

  @Override
  public Name name ( Iterable < String > parts ) {
    FsName current = this;
    for ( String part : parts ) {
      Objects.requireNonNull ( part, "part must not be null" );
      current = (FsName) current.name ( part );
    }
    return current;
  }

  @Override
  public < T > Name name ( Iterable < ? extends T > parts, Function < T, String > mapper ) {
    FsName current = this;
    for ( T part : parts ) {
      current = (FsName) current.name ( mapper.apply ( part ) );
    }
    return current;
  }

  @Override
  public Name name ( Iterator < String > parts ) {
    FsName current = this;
    while ( parts.hasNext () ) {
      String part = parts.next ();
      Objects.requireNonNull ( part, "part must not be null" );
      current = (FsName) current.name ( part );
    }
    return current;
  }

  @Override
  public < T > Name name ( Iterator < ? extends T > parts, Function < T, String > mapper ) {
    FsName current = this;
    while ( parts.hasNext () ) {
      current = (FsName) current.name ( mapper.apply ( parts.next () ) );
    }
    return current;
  }

  @Override
  public Name name ( Class < ? > type ) {
    String canonical = type.getCanonicalName ();
    return name ( canonical != null ? canonical : type.getName () );
  }

  @Override
  public Name name ( Member member ) {
    Class < ? > declClass = member.getDeclaringClass ();
    String canonical = declClass.getCanonicalName ();
    String className = canonical != null ? canonical : declClass.getName ();
    return name ( className ).name ( member.getName () );
  }

  @Override
  public CharSequence path ( Function < ? super String, ? extends CharSequence > mapper ) {
    StringBuilder sb = new StringBuilder ();
    buildPath ( sb, mapper );
    return sb;
  }

  private void buildPath ( StringBuilder sb, Function < ? super String, ? extends CharSequence > mapper ) {
    if ( parent != null ) {
      parent.buildPath ( sb, mapper );
      sb.append ( FULLSTOP );
    }
    sb.append ( mapper.apply ( segment ) );
  }

  /// Optimized: return cached path directly for '.' separator (most common case).
  /// This is 10-100x faster than the default implementation that walks the tree.
  public CharSequence path ( char separator ) {
    // Fast path for '.' separator - return cached path
    if ( separator == FULLSTOP ) {
      return path;
    }
    // Slow path for other separators - need to rebuild with new separator
    StringBuilder sb = new StringBuilder ();
    buildPathWithSeparator ( sb, separator );
    return sb;
  }

  private void buildPathWithSeparator ( StringBuilder sb, char separator ) {
    if ( parent != null ) {
      parent.buildPathWithSeparator ( sb, separator );
      sb.append ( separator );
    }
    sb.append ( segment );
  }

  @Override
  public String toString () {
    // Path computed at construction - O(1)
    return path;
  }

  @Override
  public boolean equals ( Object o ) {
    // Identity-based: interned names with same path ARE the same object
    return this == o;
  }

  @Override
  public int hashCode () {
    // Return cached identity hash code - computed once at construction
    return cachedHash;
  }

  @Override
  public int depth () {
    // Cached at construction - O(1) instead of O(depth) traversal
    return depth;
  }

  @Override
  public int compareTo ( Name other ) {
    Objects.requireNonNull ( other, "other must not be null" );
    // Identity check first - interned names are identity-comparable
    if ( this == other )
      return 0;
    // Compare using toString (cached after first call)
    return toString ().compareTo ( other.toString () );
  }

}
