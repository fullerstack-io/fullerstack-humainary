package io.fullerstack.substrates;

import static java.util.Objects.requireNonNull;

import io.humainary.substrates.api.Substrates.Circuit;
import io.humainary.substrates.api.Substrates.Cortex;
import io.humainary.substrates.api.Substrates.Current;
import io.humainary.substrates.api.Substrates.Name;
import io.humainary.substrates.api.Substrates.New;
import io.humainary.substrates.api.Substrates.NotNull;
import io.humainary.substrates.api.Substrates.Provided;
import io.humainary.substrates.api.Substrates.Scope;
import io.humainary.substrates.api.Substrates.Slot;
import io.humainary.substrates.api.Substrates.State;
import io.humainary.substrates.api.Substrates.Subject;
import java.lang.reflect.Member;
import java.util.Iterator;
import java.util.Objects;
import java.util.function.Function;

/// The entry point for creating substrates.
@Provided
final class FsCortex implements Cortex {

  /// Cached Name for anonymous scopes - avoid repeated HashMap lookup.
  static final Name SCOPE_NAME = FsName.intern("scope");
  /// Cached Name for anonymous circuits.
  static final Name CIRCUIT_NAME = FsName.intern("circuit");

  private final Subject<Cortex> subject;

  /// ThreadLocal cache for Current instances - each thread gets one stable Current.
  private final ThreadLocal < FsCurrent > currentCache;

  FsCortex () {
    this.subject = new FsSubject <> ( FsName.intern ( "cortex" ), Cortex.class );
    this.currentCache = ThreadLocal.withInitial ( () -> {
      Thread t = Thread.currentThread ();
      String threadName = t.getName();
      // Handle empty thread names (common with virtual threads)
      if (threadName == null || threadName.isEmpty()) {
        threadName = "vt-" + t.threadId();
      }
      FsSubject < Current > currentSubject = new FsSubject <> (
        FsName.parse ( "thread." + threadName ),
        (FsSubject < ? >) subject,
        Current.class
      );
      return new FsCurrent ( currentSubject );
    } );
  }

  @Override
  public Subject < Cortex > subject () {
    return subject;
  }

  @New
  @NotNull
  @Override
  public Circuit circuit () {
    return circuit ( CIRCUIT_NAME );
  }

  @New
  @NotNull
  @Override
  public Circuit circuit ( @NotNull Name name ) {
    requireNonNull ( name, "name must not be null" );
    FsSubject < Circuit > circuitSubject = new FsSubject <> (
      name, (FsSubject < ? >) subject, Circuit.class
    );
    return new FsCircuit(circuitSubject);
  }

  @Override
  public Current current () {
    return currentCache.get ();
  }

  // =========================================================================
  // Name factory methods - delegate to FsName static factories
  // =========================================================================

  @Override
  public Name name ( String path ) {
    return FsName.parse ( path );
  }

  @Override
  public Name name ( Enum < ? > path ) {
    return FsName.fromEnum ( path );
  }

  @Override
  public Name name ( Iterable < String > parts ) {
    return FsName.fromIterable ( parts );
  }

  @Override
  public < T > Name name ( Iterable < ? extends T > it, Function < T, String > mapper ) {
    return FsName.fromIterable ( it, mapper );
  }

  @Override
  public Name name ( Iterator < String > it ) {
    return FsName.fromIterator ( it );
  }

  @Override
  public < T > Name name ( Iterator < ? extends T > it, Function < T, String > mapper ) {
    return FsName.fromIterator ( it, mapper );
  }

  @Override
  public Name name ( Class < ? > type ) {
    return FsName.fromClass ( type );
  }

  @Override
  public Name name ( Member member ) {
    return FsName.fromMember ( member );
  }

  @New
  @NotNull
  @Override
  public Scope scope ( @NotNull Name name ) {
    requireNonNull ( name, "name must not be null" );
    FsSubject < Scope > scopeSubject = new FsSubject <> (
      name, (FsSubject < ? >) subject, Scope.class
    );
    return new FsScope ( scopeSubject );
  }

  @New
  @NotNull
  @Override
  public Scope scope () {
    return scope ( SCOPE_NAME );
  }

  @New
  @NotNull
  @Override
  @SuppressWarnings ( "unchecked" )
  public Slot < Boolean > slot ( @NotNull Name name, boolean value ) {
    Objects.requireNonNull ( name, "name must not be null" );
    return new FsSlot <> ( name, value, (Class < Boolean >) (Class < ? >) boolean.class );
  }

  @New
  @NotNull
  @Override
  @SuppressWarnings ( "unchecked" )
  public Slot < Integer > slot ( @NotNull Name name, int value ) {
    Objects.requireNonNull ( name, "name must not be null" );
    return new FsSlot <> ( name, value, (Class < Integer >) (Class < ? >) int.class );
  }

  @New
  @NotNull
  @Override
  @SuppressWarnings ( "unchecked" )
  public Slot < Long > slot ( @NotNull Name name, long value ) {
    Objects.requireNonNull ( name, "name must not be null" );
    return new FsSlot <> ( name, value, (Class < Long >) (Class < ? >) long.class );
  }

  @New
  @NotNull
  @Override
  @SuppressWarnings ( "unchecked" )
  public Slot < Double > slot ( @NotNull Name name, double value ) {
    Objects.requireNonNull ( name, "name must not be null" );
    return new FsSlot <> ( name, value, (Class < Double >) (Class < ? >) double.class );
  }

  @New
  @NotNull
  @Override
  @SuppressWarnings ( "unchecked" )
  public Slot < Float > slot ( @NotNull Name name, float value ) {
    Objects.requireNonNull ( name, "name must not be null" );
    return new FsSlot <> ( name, value, (Class < Float >) (Class < ? >) float.class );
  }

  @New
  @NotNull
  @Override
  public Slot < String > slot ( @NotNull Name name, @NotNull String value ) {
    Objects.requireNonNull ( name, "name must not be null" );
    Objects.requireNonNull ( value, "value must not be null" );
    return new FsSlot <> ( name, value, String.class );
  }

  @New
  @NotNull
  @Override
  public Slot < Name > slot ( @NotNull Enum < ? > value ) {
    Objects.requireNonNull ( value, "value must not be null" );
    Name slotName = name ( value.getDeclaringClass () );
    // Value is the full enum name: DeclaringClass.name
    Name slotValue = name ( value );
    return new FsSlot <> ( slotName, slotValue, Name.class );
  }

  @New
  @NotNull
  @Override
  public Slot < Name > slot ( @NotNull Name name, @NotNull Name value ) {
    Objects.requireNonNull ( name, "name must not be null" );
    Objects.requireNonNull ( value, "value must not be null" );
    return new FsSlot <> ( name, value, Name.class );
  }

  @New
  @NotNull
  @Override
  public Slot < State > slot ( @NotNull Name name, @NotNull State value ) {
    Objects.requireNonNull ( name, "name must not be null" );
    Objects.requireNonNull ( value, "value must not be null" );
    return new FsSlot <> ( name, value, State.class );
  }

  @New
  @NotNull
  @Override
  public State state () {
    return FsState.create ();
  }

}
