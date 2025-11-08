package io.fullerstack.substrates.scope;

import io.humainary.substrates.api.Substrates.*;
import io.fullerstack.substrates.id.UuidIdentifier;
import io.fullerstack.substrates.subject.ContextualSubject;
import io.fullerstack.substrates.state.LinkedState;
import io.fullerstack.substrates.closure.AutoClosingResource;
import io.fullerstack.substrates.name.InternedName;

import java.util.Deque;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentHashMap;

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
  private final    Name                name;
  private final    Scope               parent;
  private final    Map < Name, Scope > childScopes;
  private final    Deque < Resource >  resources = new ConcurrentLinkedDeque <> ();
  private volatile boolean             closed    = false;

  // Cache Subject - each Scope has a persistent identity
  private final Subject scopeSubject;

  // Cache Closures per resource - cleared when closure is consumed
  private final Map < Resource, Closure < ? > > closureCache = new ConcurrentHashMap <> ();

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
    this.childScopes = new ConcurrentHashMap <> ();
    // Create Subject once - represents persistent identity of this Scope
    this.scopeSubject = new ContextualSubject <> (
      UuidIdentifier.generate (),
      name,
      LinkedState.empty (),
      Scope.class
    );
  }

  @Override
  public Subject subject () {
    return scopeSubject;
  }

  @Override
  public Scope scope () {
    checkClosed ();
    // Create anonymous scope with generated name and add to child scopes
    // so it gets closed when parent closes
    Name anonymousName = InternedName.of ( "scope-" + UuidIdentifier.generate () );
    ManagedScope childScope = new ManagedScope ( anonymousName, this );
    childScopes.put ( anonymousName, childScope );
    return childScope;
  }

  @Override
  public Scope scope ( Name name ) {
    checkClosed ();
    return childScopes.computeIfAbsent ( name, n -> new ManagedScope ( n, this ) );
  }

  @Override
  public < R extends Resource > R register ( R resource ) {
    checkClosed ();
    Objects.requireNonNull ( resource, "Resource cannot be null" );
    resources.addFirst ( resource );  // Add to front for LIFO closure ordering
    return resource;
  }

  @Override
  @SuppressWarnings ( "unchecked" )
  public < R extends Resource > Closure < R > closure ( R resource ) {
    checkClosed ();
    Objects.requireNonNull ( resource, "Resource cannot be null" );

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

    // Close all child scopes first
    for ( Scope scope : childScopes.values () ) {
      try {
        scope.close ();
      } catch ( java.lang.Exception e ) {
        // Log but continue closing others
      }
    }

    // Close all resources in LIFO order (reverse of registration)
    // Since we use addFirst(), iteration is already in LIFO order
    resources.forEach ( resource -> {
      try {
        resource.close ();
      } catch ( java.lang.Exception e ) {
        // Log but continue closing others
      }
    } );

    resources.clear ();
    childScopes.clear ();
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
