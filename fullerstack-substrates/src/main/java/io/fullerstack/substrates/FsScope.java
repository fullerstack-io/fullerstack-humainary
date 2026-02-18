package io.fullerstack.substrates;

import io.humainary.substrates.api.Substrates.Closure;
import io.humainary.substrates.api.Substrates.Idempotent;
import io.humainary.substrates.api.Substrates.Name;
import io.humainary.substrates.api.Substrates.New;
import io.humainary.substrates.api.Substrates.NotNull;
import io.humainary.substrates.api.Substrates.Provided;
import io.humainary.substrates.api.Substrates.Resource;
import io.humainary.substrates.api.Substrates.Scope;
import io.humainary.substrates.api.Substrates.Subject;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/// A container for grouping resources with coordinated lifecycle management.
///
/// Scope provides structured resource management - resources registered with
/// a scope are automatically closed when the scope closes. Resources are closed
/// in reverse registration order (LIFO).
///
/// ## Key Features
///
/// - **Automatic cleanup**: All registered resources closed on scope close
/// - **LIFO ordering**: Last registered is first closed
/// - **Child scopes**: Create nested scopes for hierarchical management
/// - **Idempotent close**: Safe to call close() multiple times
/// - **Lazy subject**: Subject created on-demand to avoid AtomicLong overhead
///
/// @see Resource
@Provided
final class FsScope implements Scope {

  /// Cached Name for anonymous scopes.
  static final Name SCOPE_NAME = FsName.intern ( "scope" );

  /// The name for this scope (null for anonymous).
  private final Name name;

  /// Parent scope (for hierarchy).
  private final FsScope parent;

  /// Lazily-initialized subject (benign race - multiple creates are harmless).
  private volatile Subject < Scope > subject;

  /// Registered resources (closed in reverse order). Lazily initialized.
  private List < Resource > resources;

  /// Child scopes. Lazily initialized.
  private List < FsScope > children;

  /// Cache of closures per resource (cleared when consumed). Lazily initialized.
  private Map < Resource, FsClosure < ? > > closureCache;

  /// Whether this scope has been closed.
  private volatile boolean closed;

  /// Creates a new root scope with the given name.
  FsScope ( Name name ) {
    this.name = name;
    this.parent = null;
  }

  /// Creates a new child scope with the given name and parent.
  FsScope ( Name name, FsScope parent ) {
    this.name = name;
    this.parent = parent;
  }

  boolean isClosed () {
    return closed;
  }

  /// Called by FsClosure when consumed to remove from cache and resources list.
  void closureConsumed ( Resource resource ) {
    if ( closureCache != null )
      closureCache.remove ( resource );
    if ( resources != null )
      resources.remove ( resource );
  }

  @Override
  public Subject < Scope > subject () {
    Subject < Scope > s = subject;
    if ( s == null ) {
      FsSubject < ? > parentSubject = parent != null ? (FsSubject < ? >) parent.subject () : null;
      s = new FsSubject <> ( effectiveName (), parentSubject, Scope.class );
      subject = s;
    }
    return s;
  }

  /// Returns the effective name for this scope.
  private Name effectiveName () {
    return name != null ? name : SCOPE_NAME;
  }

  @Override
  public String part () {
    return effectiveName ().part ();
  }

  @Override
  public Optional < Scope > enclosure () {
    return Optional.ofNullable ( parent );
  }

  @New
  @NotNull
  @Override
  @SuppressWarnings ( "unchecked" )
  public < R extends Resource > Closure < R > closure ( @NotNull R resource ) {
    java.util.Objects.requireNonNull ( resource, "resource must not be null" );
    if ( closed ) {
      throw new IllegalStateException ( "Scope is closed" );
    }
    // Lazy init closure cache
    if ( closureCache == null ) {
      closureCache = new IdentityHashMap <> ();
    }
    // Check cache for existing non-consumed closure
    FsClosure < ? > cached = closureCache.get ( resource );
    if ( cached != null && !cached.isConsumed () ) {
      return (Closure < R >) cached;
    }
    // Create new closure and cache it
    FsClosure < R > closure = new FsClosure <> ( resource, this );
    closureCache.put ( resource, closure );
    // Lazy init resources list
    if ( resources == null ) {
      resources = new ArrayList <> ();
    }
    // Register resource so it gets closed when scope closes (if not consumed)
    resources.add ( resource );
    return closure;
  }

  @NotNull
  @Override
  public < R extends Resource > R register ( @NotNull R resource ) {
    java.util.Objects.requireNonNull ( resource, "resource must not be null" );
    if ( closed ) {
      throw new IllegalStateException ( "Scope is closed" );
    }
    // Lazy init resources list
    if ( resources == null ) {
      resources = new ArrayList <> ();
    }
    // Idempotent: same instance (by identity) is a no-op
    for ( int i = 0, len = resources.size (); i < len; i++ ) {
      if ( resources.get ( i ) == resource ) return resource;
    }
    resources.add ( resource );
    return resource;
  }

  @New
  @NotNull
  @Override
  public Scope scope () {
    if ( closed ) {
      throw new IllegalStateException ( "Scope is closed" );
    }
    FsScope child = new FsScope ( SCOPE_NAME, this );
    // Lazy init children list
    if ( children == null ) {
      children = new ArrayList <> ();
    }
    children.add ( child );
    return child;
  }

  @New
  @NotNull
  @Override
  public Scope scope ( @NotNull Name childName ) {
    java.util.Objects.requireNonNull ( childName, "name must not be null" );
    if ( closed ) {
      throw new IllegalStateException ( "Scope is closed" );
    }
    FsScope child = new FsScope ( childName, this );
    // Lazy init children list
    if ( children == null ) {
      children = new ArrayList <> ();
    }
    children.add ( child );
    return child;
  }

  @Idempotent
  @Override
  public void close () {
    if ( closed ) {
      return; // Idempotent
    }
    closed = true;

    // Close children first (if any)
    if ( children != null ) {
      for ( FsScope child : children ) {
        try {
          child.close ();
        } catch ( java.lang.Exception e ) {
          // Suppress and continue
        }
      }
    }

    // Close resources in reverse order (LIFO) (if any)
    if ( resources != null ) {
      for ( int i = resources.size () - 1; i >= 0; i-- ) {
        try {
          resources.get ( i ).close ();
        } catch ( java.lang.Exception e ) {
          // Suppress and continue
        }
      }
    }
  }

  @Override
  public String toString () {
    return path ().toString ();
  }

}
