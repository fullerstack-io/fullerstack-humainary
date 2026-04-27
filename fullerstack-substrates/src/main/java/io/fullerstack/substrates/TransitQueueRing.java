package io.fullerstack.substrates;

import java.util.function.Consumer;

/// Single-threaded transit queue using a growable power-of-2 ring.
///
/// Same role as {@link TransitQueue} but a leaner data structure for the
/// cascade pattern. Each emission is held as a (receiver, value) pair across
/// two parallel arrays indexed by `head & mask` / `tail & mask`. Capacity is
/// unbounded — when the ring fills, it doubles in size with a one-shot copy.
///
/// Per-iteration cost on the cascade hot path:
/// - enqueue: `int i = tail & mask; receivers[i] = r; values[i] = v; tail++`
/// - dequeue: `int i = head & mask; r = receivers[i]; v = values[i];`
///            `receivers[i] = null; values[i] = null; head++; r.accept(v)`
///
/// No chunk-advance check, no ring-reset branch, no linked-list `next`
/// pointer maintenance. Just `& mask` and indexed array access.
///
/// Single-thread ownership: only the circuit worker thread enqueues and
/// drains. No memory barriers, no atomics.
final class TransitQueueRing {

  private static final int INITIAL_CAP = 64;

  private Consumer < ? >[] receivers = new Consumer < ? >[INITIAL_CAP];
  private Object[]         values    = new Object[INITIAL_CAP];
  private int              mask      = INITIAL_CAP - 1;
  private int              head;
  private int              tail;

  @jdk.internal.vm.annotation.ForceInline
  void enqueue ( Consumer < Object > receiver, Object value ) {
    int i = tail & mask;
    receivers[i] = receiver;
    values[i] = value;
    tail++;
    if ( tail - head > mask ) grow ();
  }

  @SuppressWarnings ( "unchecked" )
  private void grow () {
    int oldCap = mask + 1;
    int newCap = oldCap << 1;
    Consumer < ? >[] nr = new Consumer < ? >[newCap];
    Object[] nv = new Object[newCap];
    int n = tail - head;
    for ( int i = 0; i < n; i++ ) {
      int src = ( head + i ) & mask;
      nr[i] = receivers[src];
      nv[i] = values[src];
    }
    receivers = nr;
    values = nv;
    mask = newCap - 1;
    head = 0;
    tail = n;
  }

  @jdk.internal.vm.annotation.ForceInline
  boolean hasWork () {
    return head != tail;
  }

  @jdk.internal.vm.annotation.ForceInline
  @SuppressWarnings ( "unchecked" )
  boolean drain () {
    if ( head == tail ) return false;
    do {
      int i = head & mask;
      Consumer < Object > r = (Consumer < Object >) receivers[i];
      Object v = values[i];
      receivers[i] = null;
      values[i] = null;
      head++;
      r.accept ( v );
    } while ( head != tail );
    // After draining, reset cursors so the ring stays at home position.
    // This is a same-thread operation; no synchronization needed.
    head = 0;
    tail = 0;
    return true;
  }
}
