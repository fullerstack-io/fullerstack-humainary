package io.fullerstack.substrates.lookup;

import io.humainary.substrates.api.Substrates.Name;
import io.humainary.substrates.api.Substrates.Percept;
import io.humainary.substrates.api.Substrates.Lookup;
import io.humainary.substrates.api.Substrates.Subject;
import io.humainary.substrates.api.Substrates.Substrate;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Implementation of Substrates.Lookup for percept instance management.
 * <p>
 * < p >Lookups cache percept instances by name, creating them on-demand using a factory function.
 * Instances are cached and reused for the same name.
 *
 * @param < P > the percept type (must extend Percept)
 * @see Lookup
 */
public class ConcurrentLookup < P extends Percept > implements Lookup < P > {
  private final Map < Name, P >                        percepts = new ConcurrentHashMap <> ();
  private final Function < ? super Name, ? extends P > factory;

  /**
   * Creates a new Lookup with the given factory.
   *
   * @param factory function to create percepts for given names
   */
  public ConcurrentLookup ( Function < ? super Name, ? extends P > factory ) {
    this.factory = Objects.requireNonNull ( factory, "Lookup factory cannot be null" );
  }

  @Override
  public P percept ( Name name ) {
    Objects.requireNonNull ( name, "Name cannot be null" );
    return percepts.computeIfAbsent ( name, factory::apply );
  }

  @Override
  public P percept ( Substrate < ? > substrate ) {
    Objects.requireNonNull ( substrate, "Substrate cannot be null" );
    return percept ( substrate.subject ().name () );
  }

  @Override
  public P percept ( Subject < ? > subject ) {
    Objects.requireNonNull ( subject, "Subject cannot be null" );
    return percept ( subject.name () );
  }

  /**
   * Gets the number of cached percepts.
   *
   * @return percept count
   */
  public int size () {
    return percepts.size ();
  }

  /**
   * Clears all cached percepts.
   */
  public void clear () {
    percepts.clear ();
  }

  @Override
  public String toString () {
    return "Lookup[size=" + percepts.size () + "]";
  }
}
