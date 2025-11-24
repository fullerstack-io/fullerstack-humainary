package io.fullerstack.substrates.scope;

import io.humainary.substrates.api.Substrates.*;
import io.fullerstack.substrates.subject.ContextualSubject;
import io.fullerstack.substrates.state.LinkedState;
import io.fullerstack.substrates.closure.AutoClosingResource;
import io.fullerstack.substrates.name.InternedName;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Implementation of Substrates.Scope for hierarchical context management.
 * <p>
 * < p >Scopes support hierarchical resource management and can be nested.
 * Resources are closed in LIFO (Last In, First Out) order, matching Java's
 * try-with-resources semantics.
 *
 * @see Scope
 */
public class ManagedScope implements Scope {
  private final Name                            name;
  private final Scope                           parent;
  private       Map < Name, Scope >             childScopes;
  private       Deque < Resource >              resources;
  private       boolean                         closed = false;

  // Counter for anonymous child scopes (cheaper than UUID)
  private       int                             anonymousCounter = 0;

  // Lazy Subject - only created if subject() is called
  private       Subject < Scope >               scopeSubject;

  // Lazy Closure cache - only created if closure() is called
  private       Map < Resource, Closure < ? > > closureCache;

  /**
   * Creates a root scope.
   *
   * @param name scope name
   */
  public ManagedScope ( Name name ) {
    this ( name, null );
  }

  /**
   * Creates a child scope.
   *
   * @param name   scope name
   * @param parent parent scope (nullable for root)
   */
  private ManagedScope ( Name name, Scope parent ) {
    this.name = Objects.requireNonNull ( name, "Scope name cannot be null" );
    this.parent = parent;
    // All collections and Subject are lazy - only created when needed
  }

  @Override
  public Subject < Scope > subject () {
    // Lazy Subject creation (single-threaded access)
    if ( scopeSubject == null ) {
      // Pass parent's subject for hierarchy (null for root scope)
      Subject < ? > parentSubject = parent != null ? parent.subject () : null;
      scopeSubject = new ContextualSubject <> (
        name,
        Scope.class,
        parentSubject
      );
    }
    return scopeSubject;
  }

  @Override
  public Scope scope () {
    checkClosed ();
    // Lazy create childScopes map
    // Per API: Scope is NOT thread-safe, single-threaded access assumed
    if ( childScopes == null ) {
      childScopes = new HashMap <> ();
    }
    // Use counter for anonymous child names (cheap vs UUID)
    // Creates hierarchical name: parent.name.name("0"), parent.name.name("1"), etc.
    Name childName = name.name ( String.valueOf ( anonymousCounter++ ) );
    ManagedScope child = new ManagedScope ( childName, this );
    childScopes.put ( childName, child );
    return child;
  }

  @Override
  public Scope scope ( Name name ) {
    checkClosed ();
    // Lazy create childScopes map
    // Per API: Scope is NOT thread-safe, single-threaded access assumed
    if ( childScopes == null ) {
      childScopes = new HashMap <> ();
    }
    return childScopes.computeIfAbsent ( name, n -> new ManagedScope ( n, this ) );
  }

  @Override
  public < R extends Resource > R register ( R resource ) {
    checkClosed ();
    Objects.requireNonNull ( resource, "Resource cannot be null" );
    // Lazy create resources deque (single-threaded access)
    if ( resources == null ) {
      resources = new ArrayDeque <> ();
    }
    resources.addFirst ( resource );  // Add to front for LIFO closure ordering
    return resource;
  }

  @Override
  @SuppressWarnings ( "unchecked" )
  public < R extends Resource > Closure < R > closure ( R resource ) {
    checkClosed ();
    Objects.requireNonNull ( resource, "Resource cannot be null" );

    // Lazy create closureCache map (single-threaded access)
    if ( closureCache == null ) {
      closureCache = new HashMap <> ();
    }

    // Return cached closure if exists and not yet consumed
    Closure < R > cached = (Closure < R >) closureCache.get ( resource );
    if ( cached != null ) {
      return cached;
    }

    // Register resource and create new closure with validity check
    register ( resource );
    Closure < R > closure = new AutoClosingResource <> (
      resource,
      () -> closureCache.remove ( resource ),
      () -> !closed  // Valid only if scope is not closed
    );
    closureCache.put ( resource, closure );
    return closure;
  }

  @Override
  public void close () {
    if ( closed ) {
      return;
    }
    closed = true;

    // Close all child scopes first (only if they exist)
    Map < Name, Scope > scopes = childScopes;
    if ( scopes != null ) {
      for ( Scope scope : scopes.values () ) {
        try {
          scope.close ();
        } catch ( java.lang.Exception e ) {
          // Log but continue closing others
        }
      }
      scopes.clear ();
    }

    // Close all resources in LIFO order (only if they exist)
    Deque < Resource > res = resources;
    if ( res != null ) {
      // Since we use addFirst(), iteration is already in LIFO order
      res.forEach ( resource -> {
        try {
          resource.close ();
        } catch ( java.lang.Exception e ) {
          // Log but continue closing others
        }
      } );
      res.clear ();
    }
  }

  @Override
  public CharSequence part () {
    return name.part ();
  }

  @Override
  public Optional < Scope > enclosure () {
    return Optional.ofNullable ( parent );
  }

  /**
   * Gets the parent scope.
   *
   * @return parent scope or null if root
   */
  public Scope parent () {
    return parent;
  }

  private void checkClosed () {
    if ( closed ) {
      throw new IllegalStateException ( "Scope is closed" );
    }
  }

  @Override
  public String toString () {
    return path ().toString ();
  }
}
