package io.fullerstack.substrates;

import io.humainary.substrates.api.Substrates.Bank;
import io.humainary.substrates.api.Substrates.Conduit;
import io.humainary.substrates.api.Substrates.Fault;
import io.humainary.substrates.api.Substrates.Idempotent;
import io.humainary.substrates.api.Substrates.Name;
import io.humainary.substrates.api.Substrates.NotNull;
import io.humainary.substrates.api.Substrates.Provided;
import io.humainary.substrates.api.Substrates.Routing;
import io.humainary.substrates.api.Substrates.Subject;
import io.humainary.substrates.api.Substrates.Tenure;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/// Bank — closeable name-indexed factory for conduits sharing the same
/// emission type and routing.
///
/// Per spec §10.4: `get(name)` creates a conduit on first access and
/// returns the cached instance on subsequent lookups (identity guarantee).
/// Closing the bank closes the conduits it materialised in creation
/// order; conduits created directly via `Circuit.conduit(...)` are not
/// affected.
@Provided
@Tenure ( Tenure.EPHEMERAL )
public final class FsBank < E > implements Bank < Conduit < E > > {

  private final Subject < Bank < Conduit < E > > > subject;
  private final FsSubject < ? > bankSubject;
  private final FsCircuit       circuit;
  private final Routing         routing;

  /// LinkedHashMap to preserve creation order for ordered close (§10.4).
  /// Guarded by `conduits` monitor. Read paths also synchronise; bank
  /// lookups are not a hot path.
  private final Map < Name, FsConduit < E > > conduits = new LinkedHashMap <> ();

  private volatile boolean closed;

  @SuppressWarnings ( "unchecked" )
  FsBank ( FsSubject < ? > parent, Name bankName, FsCircuit circuit, Routing routing ) {
    this.circuit = circuit;
    this.routing = routing;
    FsSubject < ? > s = new FsSubject <> ( bankName, parent, Bank.class );
    this.bankSubject = s;
    this.subject = (Subject < Bank < Conduit < E > > >) (Subject < ? >) s;
  }

  @Override
  public Subject < Bank < Conduit < E > > > subject () {
    return subject;
  }

  @Override
  @NotNull
  public Conduit < E > get ( @NotNull Name name ) {
    Objects.requireNonNull ( name, "name" );
    if ( closed ) {
      throw new Fault ( subject, "get", "bank is closed" );
    }
    synchronized ( conduits ) {
      if ( closed ) throw new Fault ( subject, "get", "bank is closed" );
      FsConduit < E > existing = conduits.get ( name );
      if ( existing != null ) return existing;
      FsConduit < E > created = new FsConduit <> ( bankSubject, name, circuit, routing );
      conduits.put ( name, created );
      return created;
    }
  }

  @Idempotent
  @Override
  public void close () {
    if ( closed ) return;
    Map < Name, FsConduit < E > > snapshot;
    synchronized ( conduits ) {
      if ( closed ) return;
      closed = true;
      snapshot = new LinkedHashMap <> ( conduits );
      conduits.clear ();
    }
    for ( FsConduit < E > c : snapshot.values () ) {
      c.close ();
    }
  }

  @Idempotent
  @Override
  public void closeAwait () {
    circuit.checkExternalCaller ( "closeAwait" );
    close ();
    circuit.await ();
  }
}
