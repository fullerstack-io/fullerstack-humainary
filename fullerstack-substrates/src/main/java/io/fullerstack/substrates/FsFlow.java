package io.fullerstack.substrates;

import io.humainary.substrates.api.Substrates.Configurer;
import io.humainary.substrates.api.Substrates.Flow;
import io.humainary.substrates.api.Substrates.Fluent;
import io.humainary.substrates.api.Substrates.Name;
import io.humainary.substrates.api.Substrates.Pipe;
import io.humainary.substrates.api.Substrates.Provided;
import io.humainary.substrates.api.Substrates.Receptor;
import io.humainary.substrates.api.Substrates.Sift;
import io.humainary.substrates.api.Substrates.Subject;

import java.util.Comparator;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * Flow implementation using concrete operator classes for better JIT inlining.
 */
@Provided
public final class FsFlow < E > implements Flow < E > {

  // === Concrete operator classes ===

  static final class Guard < E > implements Consumer < E > {
    final Predicate < ? super E > p;
    final Consumer < E >          d;

    Guard ( Predicate < ? super E > p, Consumer < E > d ) { this.p = p; this.d = d; }

    @Override
    public void accept ( E v ) { if ( p.test ( v ) ) d.accept ( v ); }
  }

  static final class GuardStateful < E > implements Consumer < E > {
    final BiPredicate < ? super E, ? super E > p;
    final Consumer < E >                       d;
    Object prev;

    @SuppressWarnings ( "unchecked" )
    GuardStateful ( E initial, BiPredicate < ? super E, ? super E > p, Consumer < E > d ) {
      this.prev = initial; this.p = p; this.d = d;
    }

    @Override
    @SuppressWarnings ( "unchecked" )
    public void accept ( E v ) {
      if ( p.test ( (E) prev, v ) ) { prev = v; d.accept ( v ); }
    }
  }

  static final class Diff < E > implements Consumer < E > {
    final Consumer < E > d;
    Object prev;

    Diff ( Consumer < E > d )            { this.d = d; }

    Diff ( E initial, Consumer < E > d ) { this.prev = initial; this.d = d; }

    @Override
    public void accept ( E v ) {
      if ( !Objects.equals ( v, prev ) ) { prev = v; d.accept ( v ); }
    }
  }

  // === Fused operator: Guard + Diff in single accept() ===

  static final class GuardDiff < E > implements Consumer < E > {
    final Predicate < ? super E > p;
    final Consumer < E >          d;
    Object prev;

    GuardDiff ( Predicate < ? super E > p, Consumer < E > d ) { this.p = p; this.d = d; }

    @Override
    public void accept ( E v ) {
      if ( p.test ( v ) && !Objects.equals ( v, prev ) ) { prev = v; d.accept ( v ); }
    }
  }

  static final class Limit < E > implements Consumer < E > {
    final Consumer < E > d;
    final long           max;
    long count;

    Limit ( long max, Consumer < E > d ) { this.max = max; this.d = d; }

    @Override
    public void accept ( E v ) { if ( count++ < max ) d.accept ( v ); }
  }

  static final class Skip < E > implements Consumer < E > {
    final Consumer < E > d;
    final long           n;
    long count;

    Skip ( long n, Consumer < E > d ) { this.n = n; this.d = d; }

    @Override
    public void accept ( E v ) { if ( count++ >= n ) d.accept ( v ); }
  }

  static final class SampleN < E > implements Consumer < E > {
    final Consumer < E > d;
    final int            n;
    int count;

    SampleN ( int n, Consumer < E > d ) { this.n = n; this.d = d; }

    @Override
    public void accept ( E v ) { if ( ++count % n == 0 ) d.accept ( v ); }
  }

  static final class SampleP < E > implements Consumer < E > {
    final Consumer < E > d;
    final double         p;

    SampleP ( double p, Consumer < E > d ) { this.p = p; this.d = d; }

    @Override
    public void accept ( E v ) { if ( Math.random () < p ) d.accept ( v ); }
  }

  static final class Replace < E > implements Consumer < E > {
    final UnaryOperator < E > t;
    final Consumer < E >      d;

    Replace ( UnaryOperator < E > t, Consumer < E > d ) { this.t = t; this.d = d; }

    @Override
    public void accept ( E v ) { E r = t.apply ( v ); if ( r != null ) d.accept ( r ); }
  }

  static final class Reduce < E > implements Consumer < E > {
    final BinaryOperator < E > op;
    final Consumer < E >       d;
    Object acc;

    @SuppressWarnings ( "unchecked" )
    Reduce ( E initial, BinaryOperator < E > op, Consumer < E > d ) {
      this.acc = initial; this.op = op; this.d = d;
    }

    @Override
    @SuppressWarnings ( "unchecked" )
    public void accept ( E v ) {
      E r = op.apply ( (E) acc, v ); acc = r; d.accept ( r );
    }
  }

  static final class Peek < E > implements Consumer < E > {
    final Receptor < ? super E > r;
    final Consumer < E >         d;

    Peek ( Receptor < ? super E > r, Consumer < E > d ) { this.r = r; this.d = d; }

    @Override
    public void accept ( E v ) { r.receive ( v ); d.accept ( v ); }
  }

  // === Wrapper interface ===

  @FunctionalInterface
  interface Wrap < E > {
    Consumer < E > wrap ( Consumer < E > d );
  }

  // === Flow state ===

  private final Name       name;
  private final FsCircuit  circuit;
  private final Pipe < E > target;

  private Wrap < E >[]   wrappers;
  private int            count;
  private Consumer < E > cached;

  public FsFlow ( Name name, FsCircuit circuit, Pipe < E > target ) {
    this.name = name;
    this.circuit = circuit;
    this.target = target;
  }

  FsFlow ( Subject < Pipe < E > > subject, FsCircuit circuit, Pipe < E > target ) {
    this.name = subject.name ();
    this.circuit = circuit;
    this.target = target;
  }

  @SuppressWarnings ( "unchecked" )
  private void add ( Wrap < E > w ) {
    if ( wrappers == null ) {
      wrappers = new Wrap[4];
    } else if ( count == wrappers.length ) {
      Wrap < E >[] arr = new Wrap[count * 2];
      System.arraycopy ( wrappers, 0, arr, 0, count );
      wrappers = arr;
    }
    wrappers[count++] = w;
  }

  @SuppressWarnings ( "unchecked" )
  Consumer < E > consumer () {
    if ( cached != null ) return cached;

    // Extract receiver directly from FsPipe to avoid double-queue
    // (bypass emit() which would route through transit queue)
    Consumer < E > c;
    if ( target instanceof FsPipe < E > fsPipe ) {
      c = (Consumer < E >) fsPipe.receiver ();
    } else {
      c = target::emit;  // Fallback for non-FsPipe targets
    }

    // Fuse adjacent Guard+Diff into single GuardDiff operator
    // (saves one virtual dispatch per emission)
    for ( int i = count - 1; i >= 0; i-- ) {
      if ( i > 0 ) {
        Consumer < E > probe = wrappers[i].wrap ( c );
        Consumer < E > outer = wrappers[i - 1].wrap ( probe );
        if ( outer instanceof Guard < E > g && probe instanceof Diff < E > ) {
          c = new GuardDiff <> ( g.p, c );
          i--;  // skip the Guard wrapper (already fused)
          continue;
        }
      }
      c = wrappers[i].wrap ( c );
    }
    cached = c;
    return c;
  }

  @SuppressWarnings ( "unchecked" )
  public Pipe < E > pipe () {
    // Wrap consumer chain in ReceptorReceiver so drain loop stays monomorphic.
    // Without this, the flow operator class (Guard, Diff, etc.) pollutes the
    // drain loop's r.accept(v) type profile, causing bimorphic dispatch.
    Receptor < E > r = consumer ()::accept;
    return circuit.createPipe ( name, (FsSubject < ? >) circuit.subject (),
      new FsCircuit.ReceptorReceiver <> ( r ) );
  }

  // === Fluent API ===

  @Fluent
  @Override
  public Flow < E > guard ( Predicate < ? super E > p ) {
    Objects.requireNonNull ( p, "predicate must not be null" );
    add ( d -> new Guard <> ( p, d ) );
    return this;
  }

  @Fluent
  @Override
  public Flow < E > guard ( E initial, BiPredicate < ? super E, ? super E > p ) {
    Objects.requireNonNull ( p, "predicate must not be null" );
    add ( d -> new GuardStateful <> ( initial, p, d ) );
    return this;
  }

  @Fluent
  @Override
  public Flow < E > diff () {
    add ( Diff::new );
    return this;
  }

  @Fluent
  @Override
  public Flow < E > diff ( E initial ) {
    Objects.requireNonNull ( initial, "initial value must not be null" );
    add ( d -> new Diff <> ( initial, d ) );
    return this;
  }

  @Fluent
  @Override
  public Flow < E > limit ( int n ) { return limit ( (long) n ); }

  @Fluent
  @Override
  public Flow < E > limit ( long n ) {
    if ( n < 0 ) throw new IllegalArgumentException ( "count must not be negative" );
    add ( d -> new Limit <> ( n, d ) );
    return this;
  }

  @Fluent
  @Override
  public Flow < E > skip ( long n ) {
    if ( n < 0 ) throw new IllegalArgumentException ( "count must not be negative" );
    add ( d -> new Skip <> ( n, d ) );
    return this;
  }

  @Fluent
  @Override
  public Flow < E > sample ( int n ) {
    if ( n <= 0 ) throw new IllegalArgumentException ( "sample count must be positive" );
    add ( d -> new SampleN <> ( n, d ) );
    return this;
  }

  @Fluent
  @Override
  public Flow < E > sample ( double p ) {
    if ( Double.isNaN ( p ) || p < 0.0 || p > 1.0 ) throw new IllegalArgumentException ( "sample must be in range [0.0,1.0]" );
    if ( p == 0.0 || p == 1.0 ) return this;
    add ( d -> new SampleP <> ( p, d ) );
    return this;
  }

  @Fluent
  @Override
  public Flow < E > sift ( Comparator < ? super E > cmp, Configurer < Sift < E > > cfg ) {
    Objects.requireNonNull ( cmp, "comparator must not be null" );
    Objects.requireNonNull ( cfg, "configurer must not be null" );
    FsSift < E > s = new FsSift <> ( cmp );
    cfg.configure ( s );
    Predicate < E > pred = s.predicate ();
    add ( d -> new Guard <> ( pred, d ) );
    return this;
  }

  @Fluent
  @Override
  public Flow < E > replace ( UnaryOperator < E > t ) {
    Objects.requireNonNull ( t, "transformer must not be null" );
    add ( d -> new Replace <> ( t, d ) );
    return this;
  }

  @Fluent
  @Override
  public Flow < E > reduce ( E initial, BinaryOperator < E > op ) {
    Objects.requireNonNull ( op, "operator must not be null" );
    add ( d -> new Reduce <> ( initial, op, d ) );
    return this;
  }

  @Fluent
  @Override
  public Flow < E > peek ( Receptor < ? super E > r ) {
    Objects.requireNonNull ( r, "receptor must not be null" );
    add ( d -> new Peek <> ( r, d ) );
    return this;
  }
}
