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

  private final InternedName parent;
  private final String       segment;

  /**
   * Composite key for interning cache: (parent, segment) uniquely identifies a Name.
   */
  private record NameKey( InternedName parent, String segment ) {
  }

  /**
   * Private constructor - use static factory methods or name() methods.
   */
  private InternedName ( InternedName parent, String segment ) {
    this.parent = parent;
    this.segment = Objects.requireNonNull ( segment, "segment cannot be null" );
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

    // Check if segment contains dots - if so, parse as path
    if ( segment.contains ( "." ) ) {
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

    // Single segment - intern directly
    return intern ( this, segment );
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
    // Use path() which uses '.' separator for hierarchical names
    return path ().toString ();
  }

  @Override
  public int hashCode () {
    // Hash based on full path string
    return path ().toString ().hashCode ();
  }

  @Override
  public boolean equals ( Object o ) {
    if ( this == o ) return true;
    if ( !( o instanceof Name other ) ) return false;
    return path ().toString ().equals ( other.path ().toString () );
  }
}
