package io.fullerstack.substrates;

import io.humainary.substrates.api.Substrates.Name;
import io.humainary.substrates.api.Substrates.Subject;
import io.humainary.substrates.api.Substrates.Substrate;

/**
 * Abstract base class for all Fullerstack substrate implementations.
 *
 * <p>Provides lazy subject creation with double-checked locking. Subject is only created when
 * {@link #subject()} is first called, avoiding allocation overhead when subject is never accessed.
 *
 * <p>Subclasses must implement a specific non-sealed Substrate interface (Conduit, Pipe, Cell,
 * etc.) and delegate their {@code subject()} method to {@link #lazySubject()}.
 *
 * @param <S> the substrate type
 */
abstract class FsSubstrate < S extends Substrate < S > > {

  private final    FsSubject < ? > parent;
  private final    Name            name;
  private volatile Subject < S >   subject;

  /**
   * Creates a new substrate with lazy subject creation.
   *
   * @param parent the parent subject (for hierarchy)
   * @param name the substrate name (may be null for anonymous substrates)
   */
  protected FsSubstrate ( FsSubject < ? > parent, Name name ) {
    this.parent = parent;
    this.name = name;
  }

  /**
   * Returns the type of this substrate. Subclasses must implement this to provide their type class.
   * This is only called once during lazy subject creation.
   *
   * @return the substrate type class
   */
  protected abstract Class < ? > type ();

  /**
   * Returns the lazily-created subject. Subclasses should call this from their subject() method.
   *
   * @return the subject for this substrate
   */
  @SuppressWarnings ( "unchecked" )
  protected final Subject < S > lazySubject () {
    Subject < S > s = subject;
    if ( s == null ) {
      synchronized ( this ) {
        s = subject;
        if ( s == null ) {
          s = subject = (Subject < S >) (Subject < ? >) new FsSubject <> ( name, parent, type () );
        }
      }
    }
    return s;
  }

  /** Returns the parent subject. For use by subclasses that need hierarchy access. */
  protected final FsSubject < ? > parent () {
    return parent;
  }

  /** Returns the name. For use by subclasses. */
  protected final Name name () {
    return name;
  }
}
