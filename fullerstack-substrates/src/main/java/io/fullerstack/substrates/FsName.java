package io.fullerstack.substrates;

import io.humainary.substrates.api.Substrates.Extent;
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
  private static final ConcurrentHashMap < String, FsName > NAME_CACHE = new ConcurrentHashMap <> ( 256, 0.75f, 1 );
  /// Enum cache: Enum instance → FsName. Avoids redundant getDeclaringClass() +
  /// getCanonicalName() calls.
  private static final ConcurrentHashMap < Enum < ? >, FsName >   ENUM_CACHE  = new ConcurrentHashMap <> ( 64, 0.75f, 1 );
  /// Class cache: Class → FsName. Avoids redundant getCanonicalName() + intern() calls.
  private static final ConcurrentHashMap < Class < ? >, FsName >  CLASS_CACHE      = new ConcurrentHashMap <> ( 64, 0.75f, 1 );
  /// Class name cache: Class → canonical name string. Avoids redundant reflection.
  private static final ConcurrentHashMap < Class < ? >, String >  CLASS_NAME_CACHE = new ConcurrentHashMap <> ( 64, 0.75f, 1 );
  private static final char                                     FULLSTOP   = '.';
  private final String segment;
  /// Parent reference - null for root names.
  private final FsName parent;
  /// Cached depth - computed at construction, avoids traversal.
  private final int    depth;
  /// Cached Optional wrapper around parent. Allocated once at construction so
  /// enclosure() never allocates. This makes the default Extent methods that
  /// we don't override (compareTo, foldTo, path) efficient.
  private final Optional < Name > enclosure;
  /// Eager final path. Built at construction. Eliminates the volatile read
  /// on every toString/path/compareTo call.
  private final String path;

  /// Direct children of this name. Lazy: null until the first child is added.
  /// Copy-on-write under synchronized(this); reads are lock-free via volatile.
  private volatile FsName[] childNodes;

  /// Private constructor for root names (depth=1, no parent).
  private FsName ( String segment ) {
    this.segment   = segment;
    this.parent    = null;
    this.depth     = 1;
    this.enclosure = Optional.empty ();
    this.path      = segment;
  }

  /// Private constructor with known parent (depth=parent.depth+1).
  private FsName ( String segment, FsName parent ) {
    this.segment   = segment;
    this.parent    = parent;
    this.depth     = parent.depth + 1;
    this.enclosure = Optional.of ( parent );
    this.path      = parent.path + FULLSTOP + segment;
  }

  // =========================================================================
  // Static factory methods - all Name creation logic lives here
  // =========================================================================

  /// Interns a path string, returning the canonical FsName for it.
  /// Hot path: single ConcurrentHashMap.get() — O(1).
  /// Cold path: validate then recursively build the parent chain.
  /// Caller contract: path is @NotNull (NPE from CHM.get if violated).
  public static FsName intern ( String path ) {
    FsName cached = NAME_CACHE.get ( path );
    if ( cached != null )
      return cached;
    validate ( path );
    FsName built = getOrCreate ( path );
    // Cache the leaf under the *original input* string so future intern()
    // calls with the same String instance hit CHM's reference-equality path.
    NAME_CACHE.putIfAbsent ( path, built );
    return built;
  }

  /// Recursive cold-path builder. Walks the path right-to-left via lastIndexOf,
  /// creating each prefix on the way back up. Bug-free pattern: get-then-putIfAbsent,
  /// no nested computeIfAbsent (which is forbidden by ConcurrentHashMap's contract).
  private static FsName getOrCreate ( String path ) {
    FsName cached = NAME_CACHE.get ( path );
    if ( cached != null )
      return cached;
    int dot = path.lastIndexOf ( FULLSTOP );
    if ( dot < 0 ) {
      // Root segment — input IS the segment, used directly as both key and value
      return NAME_CACHE.computeIfAbsent ( path, FsName::new );
    }
    // Recursively obtain (or build) the parent, then attach the new child
    FsName parent = getOrCreate ( path.substring ( 0, dot ) );
    return parent.internChild ( path.substring ( dot + 1 ) );
  }

  /// Validates a path: not empty, no leading/trailing dot, no consecutive dots.
  /// Called once on the full path; valid prefixes of a valid path are valid.
  private static void validate ( String path ) {
    int len = path.length ();
    if ( len == 0 ) {
      throw new IllegalArgumentException ( "Name path cannot be empty" );
    }
    if ( path.charAt ( 0 ) == FULLSTOP ) {
      throw new IllegalArgumentException ( "Name path cannot start with a dot: " + path );
    }
    if ( path.charAt ( len - 1 ) == FULLSTOP ) {
      throw new IllegalArgumentException ( "Name path cannot end with a dot: " + path );
    }
    if ( path.contains ( ".." ) ) {
      throw new IllegalArgumentException ( "Name path cannot contain consecutive dots: " + path );
    }
  }

  /// Creates an anonymous name with a unique suffix.
  /// Used for pipes and other components that don't have explicit names.
  static FsName anonymous ( String prefix ) {
    return intern ( prefix + "-" + ANONYMOUS_COUNTER.incrementAndGet () );
  }

  /// Creates a Name from an Enum (fully qualified: declaring.class.CONSTANT).
  static FsName fromEnum ( Enum < ? > e ) {
    FsName cached = ENUM_CACHE.get ( e );
    if ( cached != null )
      return cached;
    FsName name = intern ( classNameOf ( e.getDeclaringClass () ) + FULLSTOP + e.name () );
    ENUM_CACHE.putIfAbsent ( e, name );
    return name;
  }

  /// Returns the canonical or simple name for a Class, cached to avoid reflection.
  private static String classNameOf ( Class < ? > type ) {
    String cached = CLASS_NAME_CACHE.get ( type );
    if ( cached != null )
      return cached;
    String canonical = type.getCanonicalName ();
    String name = canonical != null ? canonical : type.getName ();
    CLASS_NAME_CACHE.putIfAbsent ( type, name );
    return name;
  }

  /// Creates a Name from a Class.
  static FsName fromClass ( Class < ? > type ) {
    FsName cached = CLASS_CACHE.get ( type );
    if ( cached != null )
      return cached;
    FsName name = intern ( classNameOf ( type ) );
    CLASS_CACHE.putIfAbsent ( type, name );
    return name;
  }

  /// Creates a Name from a Member (declaring class + member name).
  static FsName fromMember ( Member member ) {
    return intern ( classNameOf ( member.getDeclaringClass () ) + FULLSTOP + member.getName () );
  }

  /// Creates a Name from an Iterable of parts.
  /// Uses indexed access for List inputs to avoid iterator allocation.
  /// First element validated by intern(); subsequent elements use internChild
  /// with inline guard for null/empty/dot edge cases.
  @SuppressWarnings ( "unchecked" )
  static FsName fromIterable ( Iterable < String > parts ) {
    if ( parts instanceof java.util.List < String > list ) {
      return fromList ( list );
    }
    Iterator < String > it = parts.iterator ();
    if ( !it.hasNext () ) {
      throw new IllegalArgumentException ( "parts must not be empty" );
    }
    FsName current = intern ( it.next () );
    while ( it.hasNext () ) {
      current = current.internChild ( it.next () );
    }
    return current;
  }

  /// Fast path for List inputs — indexed access avoids iterator allocation.
  /// internChild handles null/empty/dot validation in its slow path, so
  /// warm-path lookups skip per-element validation entirely.
  private static FsName fromList ( java.util.List < String > parts ) {
    int size = parts.size ();
    if ( size == 0 ) {
      throw new IllegalArgumentException ( "parts must not be empty" );
    }
    FsName current = intern ( parts.get ( 0 ) );
    for ( int i = 1; i < size; i++ ) {
      current = current.internChild ( parts.get ( i ) );
    }
    return current;
  }

  /// Creates a Name from an Iterable with mapper.
  /// Uses indexed access for List inputs to avoid iterator allocation.
  @SuppressWarnings ( "unchecked" )
  static < T > FsName fromIterable ( Iterable < ? extends T > parts, Function < ? super T, String > mapper ) {
    if ( parts instanceof java.util.List < ? > list ) {
      return fromListMapped ( (java.util.List < ? extends T >) list, mapper );
    }
    Iterator < ? extends T > it = parts.iterator ();
    if ( !it.hasNext () ) {
      throw new IllegalArgumentException ( "parts must not be empty" );
    }
    FsName current = intern ( mapper.apply ( it.next () ) );
    while ( it.hasNext () ) {
      current = current.internChild ( mapper.apply ( it.next () ) );
    }
    return current;
  }

  /// Fast path for List inputs with mapper — indexed access avoids iterator allocation.
  private static < T > FsName fromListMapped ( java.util.List < ? extends T > parts, Function < T, String > mapper ) {
    int size = parts.size ();
    if ( size == 0 ) {
      throw new IllegalArgumentException ( "parts must not be empty" );
    }
    FsName current = intern ( mapper.apply ( parts.get ( 0 ) ) );
    for ( int i = 1; i < size; i++ ) {
      current = current.internChild ( mapper.apply ( parts.get ( i ) ) );
    }
    return current;
  }

  /// Creates a Name from an Iterator of parts.
  /// First element validated by intern(); subsequent elements use internChild
  /// with inline guard for null/empty/dot edge cases.
  static FsName fromIterator ( Iterator < String > parts ) {
    if ( !parts.hasNext () ) {
      throw new IllegalArgumentException ( "parts must not be empty" );
    }
    FsName current = intern ( parts.next () );
    while ( parts.hasNext () ) {
      current = current.internChild ( parts.next () );
    }
    return current;
  }

  /// Creates a Name from an Iterator with mapper.
  /// internChild handles null/empty/dot validation in its slow path,
  /// so warm-path lookups skip per-element validation entirely.
  static < T > FsName fromIterator ( Iterator < ? extends T > parts, Function < ? super T, String > mapper ) {
    if ( !parts.hasNext () ) {
      throw new IllegalArgumentException ( "parts must not be empty" );
    }
    FsName current = intern ( mapper.apply ( parts.next () ) );
    while ( parts.hasNext () ) {
      current = current.internChild ( mapper.apply ( parts.next () ) );
    }
    return current;
  }

  // =========================================================================
  // Instance methods - extend existing Name with suffix
  // =========================================================================

  /// Extends this name with a segment. Fast path: lock-free children scan.
  /// Slow path: validates the segment (which may itself be a multi-segment
  /// path like "child.grandchild") and creates / parses as needed.
  ///
  /// Critically: the dot validation is paid only once per new child, at creation
  /// time. Subsequent lookups skip the check entirely.
  private FsName internChild ( String childSegment ) {
    FsName[] nodes = childNodes;
    if ( nodes != null ) {
      for ( int i = 0; i < nodes.length; i++ ) {
        String s = nodes[i].segment;
        if ( s == childSegment || s.equals ( childSegment ) ) {
          return nodes[i];
        }
      }
    }
    return internChildSlow ( childSegment );
  }

  /// Slow path: validates segment, splits multi-segment paths inline,
  /// or synchronized create-or-find for simple segments. Paid only on cache miss
  /// (creating a new child) — warm-path lookups skip this entirely.
  private FsName internChildSlow ( String childSegment ) {
    Objects.requireNonNull ( childSegment );
    if ( childSegment.isEmpty () ) {
      throw new IllegalArgumentException ( "Name segment cannot be empty" );
    }
    int dot = childSegment.indexOf ( FULLSTOP );
    if ( dot >= 0 ) {
      // Multi-segment: validate dot positions and split inline
      if ( dot == 0 ) {
        throw new IllegalArgumentException ( "Name segment cannot start with a dot: " + childSegment );
      }
      if ( dot == childSegment.length () - 1 ) {
        throw new IllegalArgumentException ( "Name segment cannot end with a dot: " + childSegment );
      }
      FsName current = this;
      int start = 0;
      while ( true ) {
        int nextDot = childSegment.indexOf ( FULLSTOP, start );
        if ( nextDot < 0 ) {
          return current.internChild ( childSegment.substring ( start ) );
        }
        if ( nextDot == start ) {
          throw new IllegalArgumentException ( "Name segment cannot contain consecutive dots: " + childSegment );
        }
        current = current.internChild ( childSegment.substring ( start, nextDot ) );
        start = nextDot + 1;
      }
    }
    // Simple segment: synchronized create-or-find
    synchronized ( this ) {
      FsName[] nodes = childNodes;
      if ( nodes != null ) {
        for ( int i = 0; i < nodes.length; i++ ) {
          String s = nodes[i].segment;
          if ( s == childSegment || s.equals ( childSegment ) ) {
            return nodes[i];
          }
        }
      }
      FsName child = new FsName ( childSegment, this );
      if ( nodes == null ) {
        childNodes = new FsName[] { child };
      } else {
        FsName[] newNodes = new FsName[nodes.length + 1];
        System.arraycopy ( nodes, 0, newNodes, 0, nodes.length );
        newNodes[nodes.length] = child;
        childNodes = newNodes;
      }
      return child;
    }
  }

  @Override
  public String part () {
    return segment;
  }

  /// Returns the cached Optional wrapper. No allocation per call —
  /// the Optional was built once at construction. This makes the default
  /// Extent methods (iterator, within, compareTo, foldTo, ...) efficient.
  @Override
  public Optional < Name > enclosure () {
    return enclosure;
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
    // internChild handles single-segment fast path AND multi-segment splitting
    // (in its slow path), so chaining hits a hot path with no per-call validation.
    return internChild ( suffix );
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
      current = current.internChild ( part );
    }
    return current;
  }

  @Override
  public < T > Name name ( Iterable < ? extends T > parts, Function < ? super T, String > mapper ) {
    FsName current = this;
    for ( T part : parts ) {
      current = current.internChild ( mapper.apply ( part ) );
    }
    return current;
  }

  @Override
  public Name name ( Iterator < String > parts ) {
    FsName current = this;
    while ( parts.hasNext () ) {
      current = current.internChild ( parts.next () );
    }
    return current;
  }

  @Override
  public < T > Name name ( Iterator < ? extends T > parts, Function < ? super T, String > mapper ) {
    FsName current = this;
    while ( parts.hasNext () ) {
      current = current.internChild ( mapper.apply ( parts.next () ) );
    }
    return current;
  }

  @Override
  public Name name ( Class < ? > type ) {
    return name ( classNameOf ( type ) );
  }

  @Override
  public Name name ( Member member ) {
    return name ( classNameOf ( member.getDeclaringClass () ) ).name ( member.getName () );
  }

  /// Optimized: return cached path directly for '.' separator (the common case).
  /// Other separators fall through to the default Extent.path(char) implementation.
  @Override
  public CharSequence path ( char separator ) {
    return ( separator == FULLSTOP )
      ? path
      : Name.super.path ( separator );
  }

  /// Maps each segment via the mapper and joins with '.'.
  /// Required because Name.path(Function) is abstract.
  @Override
  public CharSequence path ( Function < ? super String, ? extends CharSequence > mapper ) {
    StringBuilder sb = new StringBuilder ();
    appendMappedPath ( sb, mapper );
    return sb;
  }

  private void appendMappedPath ( StringBuilder sb, Function < ? super String, ? extends CharSequence > mapper ) {
    if ( parent != null ) {
      parent.appendMappedPath ( sb, mapper );
      sb.append ( FULLSTOP );
    }
    sb.append ( mapper.apply ( segment ) );
  }

  @Override
  public String toString () {
    return path;
  }

  @Override
  public boolean equals ( Object o ) {
    // Identity-based: interned names with same path ARE the same object
    return this == o;
  }

  @Override
  public int hashCode () {
    // System.identityHashCode is a JVM intrinsic; cached in the object header
    // after first call. No need for a per-instance cache field.
    return System.identityHashCode ( this );
  }

  @Override
  public int depth () {
    // Cached at construction - O(1) instead of O(depth) traversal
    return depth;
  }

  /// Static iterator class — easier for JIT to inline than an anonymous inner
  /// class which carries an implicit reference to the enclosing FsName.
  private static final class ParentIterator implements Iterator < Name > {
    private FsName cursor;
    ParentIterator ( FsName start ) { this.cursor = start; }

    @Override
    public boolean hasNext () {
      return cursor != null;
    }

    @Override
    public Name next () {
      FsName result = cursor;
      if ( result == null ) {
        throw new java.util.NoSuchElementException ();
      }
      cursor = result.parent;
      return result;
    }
  }

  @Override
  public Iterator < Name > iterator () {
    return new ParentIterator ( this );
  }

  /// Optimized within() with depth-guard fast-fail and direct parent walk.
  /// The default Extent.within() walks via enclosure() with Optional unwrapping
  /// per level. We can short-circuit using cached depth when the enclosure is
  /// also an FsName.
  @Override
  public boolean within ( final Extent < ?, ? > enclosure ) {
    Objects.requireNonNull ( enclosure, "enclosure must not be null" );
    if ( enclosure instanceof FsName target ) {
      // Fast fail: enclosure must be strictly shallower than us
      if ( target.depth >= depth ) {
        return false;
      }
      // Walk exactly (depth - target.depth) levels up
      FsName cursor = this;
      for ( int i = depth; i > target.depth; i-- ) {
        cursor = cursor.parent;
      }
      return cursor == target;
    }
    // Foreign Extent type — walk via parent fields
    for ( FsName cursor = parent; cursor != null; cursor = cursor.parent ) {
      if ( cursor == enclosure ) {
        return true;
      }
    }
    return false;
  }

}
