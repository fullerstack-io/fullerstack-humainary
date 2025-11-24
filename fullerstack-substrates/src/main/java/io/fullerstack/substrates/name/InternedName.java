package io.fullerstack.substrates.name;

import io.humainary.substrates.api.Substrates.Name;

import java.lang.reflect.Member;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Interned Name implementation with identity-based equality per @Identity annotation.
 * <p>
 * <b>@Identity Contract:</b> Name uses reference equality, not value equality.
 * <ul>
 * <li>Two Name instances are equal if and only if they're the same object (==)</li>
 * <li>Interning ensures identical paths return the same instance</li>
 * <li>equals() and hashCode() are NOT overridden - use Object's identity-based implementations</li>
 * </ul>
 * <p>
 * <b>Design:</b>
 * <ul>
 * <li>Cortex creates root names: InternedName.of("root")</li>
 * <li>Hierarchy built by name() methods: parent.name("child")</li>
 * <li>Parent-child links create tree structure</li>
 * <li>Interning ensures identical paths → same instance → == comparison works</li>
 * </ul>
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
    // Use getCanonicalName() which already has dots for inner classes
    // Falls back to getName() for anonymous/local classes
    Class<?> declaringClass = e.getDeclaringClass();
    String className = declaringClass.getCanonicalName();
    if (className == null) {
      // Anonymous or local class - use runtime name
      className = declaringClass.getName();
    }
    return name ( className ).name ( e.name () );
  }

  @Override
  public Name name ( Class < ? > type ) {
    Objects.requireNonNull ( type, "type" );
    // Use getCanonicalName() which already has dots for inner classes
    // Falls back to getName() for anonymous/local classes per API spec
    String className = type.getCanonicalName();
    if (className == null) {
      // Anonymous or local class - use runtime name
      className = type.getName();
    }
    return name ( className );
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
    return segment;
  }

  @Override
  public String toString () {
    // Return full hierarchical path for debugging
    return path ().toString ();
  }

  // NOTE: Name uses @Identity contract - reference equality only
  // Do NOT override equals() or hashCode()
  // Use Object.equals() (identity) and Object.hashCode() (identity hash)
  // Interning ensures identical paths return same instance, so == works correctly
}
