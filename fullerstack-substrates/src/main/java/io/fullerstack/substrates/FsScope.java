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
///
/// @see Resource
@Provided
final class FsScope
  implements Scope {

  /// Cached Name for anonymous scopes.
  static final Name SCOPE_NAME = FsName.intern ( "scope" );

  /// The subject identity for this scope.
  private final Subject < Scope > subject;

  /// Parent scope (for hierarchy).
  private final FsScope parent;

  /// Registered resources (closed in reverse order). Lazily initialized.
  private List < Resource > resources;

  /// Child scopes. Lazily initialized.
  private List < FsScope > children;

  /// Cache of closures per resource (cleared when consumed). Lazily initialized.
  private Map < Resource, FsClosure < ? > > closureCache;

  /// Whether this scope has been closed.
  private volatile boolean closed;

  /// Creates a new root scope with the given subject.
  FsScope ( Subject < Scope > subject ) {
    this.subject = subject;
    this.parent = null;
  }

  /// Creates a new child scope with the given subject and parent.
  FsScope ( Subject < Scope > subject, FsScope parent ) {
    this.subject = subject;
    this.parent = parent;
  }

  boolean isClosed () {
    return closed;
  }

  /// Called by FsClosure when consumed to remove from cache and resources list.
  void closureConsumed ( Resource resource ) {
    if ( closureCache != null ) closureCache.remove ( resource );
    if ( resources != null ) resources.remove ( resource );
  }

  @Override
  public Subject < Scope > subject () {
    return subject;
  }

  @Override
  public String part () {
    return subject.name ().part ();
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
    FsSubject < Scope > childSubject = new FsSubject <> (
      SCOPE_NAME, (FsSubject < ? >) subject, Scope.class
    );
    FsScope child = new FsScope ( childSubject, this );
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
  public Scope scope ( @NotNull Name name ) {
    java.util.Objects.requireNonNull ( name, "name must not be null" );
    if ( closed ) {
      throw new IllegalStateException ( "Scope is closed" );
    }
    FsSubject < Scope > childSubject = new FsSubject <> (
      name, (FsSubject < ? >) subject, Scope.class
    );
    FsScope child = new FsScope ( childSubject, this );
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
