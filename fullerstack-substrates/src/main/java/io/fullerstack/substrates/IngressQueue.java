package io.fullerstack.substrates;

import jdk.internal.vm.annotation.Contended;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Arrays;
import java.util.function.Consumer;

/**
 * Wait-free MPSC queue using unrolled linked list of {@link QChunk}s.
 *
 * <p>Each chunk holds 64 receiver+value pairs in a flat array. Producers
 * claim slots via atomic {@code getAndAdd} (wait-free). When a chunk fills,
 * a new one is linked. Exhausted chunks are recycled via a Treiber stack
 * free list — zero allocation in steady state.
 *
 * <p>Consumer drains slots sequentially (cache-friendly) and interleaves
 * transit drain after each emission.
 */
public final class IngressQueue {

  private static final VarHandle TAIL;
  private static final VarHandle FREE_HEAD;

  static {
    try {
      MethodHandles.Lookup l = MethodHandles.lookup ();
      TAIL = l.findVarHandle ( IngressQueue.class, "tail", QChunk.class );
      FREE_HEAD = l.findVarHandle ( IngressQueue.class, "freeHead", QChunk.class );
    } catch ( ReflectiveOperationException e ) {
      throw new ExceptionInInitializerError ( e );
    }
  }

  // Consumer state (single-threaded, plain fields)
  private QChunk headChunk;
  private int    headIndex;

  // Producer state (contended)
  @Contended
  @SuppressWarnings ( "unused" )  // accessed via TAIL VarHandle
  private volatile QChunk tail;

  // Free list (Treiber stack)
  @Contended
  @SuppressWarnings ( "unused" )  // accessed via FREE_HEAD VarHandle
  private volatile QChunk freeHead;


  public IngressQueue () {
    QChunk initial = new QChunk ();
    headChunk = initial;
    headIndex = 0;
    tail = initial;
  }

  /**
   * Enqueue emission. Wait-free: getAndAdd always succeeds.
   * Receiver (never null) acts as commit signal via setRelease.
   */
  @jdk.internal.vm.annotation.ForceInline
  public void enqueue ( Consumer < Object > receiver, Object value ) {
    QChunk chunk = tail;                                          // volatile read
    int slot = (int) QChunk.CLAIMED.getAndAdd ( chunk, 1 );      // wait-free claim
    if ( slot < QChunk.CAPACITY ) {
      int base = slot << 1;
      chunk.slots[base + 1] = value;                              // plain store (value first)
      QChunk.SLOTS.setRelease ( chunk.slots, base, receiver );    // release store (commit)
    } else {
      enqueueSlow ( receiver, value, chunk );                     // cold path (1/64 emits)
    }
  }

  /**
   * Cold path: current chunk full, allocate or recycle a new one.
   * At most 1 recursive retry.
   */
  private void enqueueSlow ( Consumer < Object > receiver, Object value, QChunk full ) {
    QChunk next = (QChunk) QChunk.NEXT.getAcquire ( full );
    if ( next == null ) {
      QChunk fresh = popFreeChunk ();
      if ( fresh == null ) fresh = new QChunk ();
      if ( QChunk.NEXT.compareAndSet ( full, null, fresh ) ) {
        TAIL.compareAndSet ( this, full, fresh );
        next = fresh;
      } else {
        recycleFreeChunk ( fresh );
        next = (QChunk) QChunk.NEXT.getAcquire ( full );
      }
    }
    TAIL.compareAndSet ( this, full, next );
    enqueue ( receiver, value );  // retry (at most 1 recursion)
  }

  /**
   * Drain all committed slots with depth-first cascade interleaving.
   * Returns true if any slots were drained.
   */
  @jdk.internal.vm.annotation.ForceInline
  boolean drainBatch ( FsCircuit circuit ) {
    int idx = headIndex;
    QChunk chunk = headChunk;
    if ( idx >= QChunk.CAPACITY ) {
      QChunk next = (QChunk) QChunk.NEXT.getAcquire ( chunk );
      if ( next == null ) return false;
      recycleFreeChunk ( chunk );
      chunk = next;
      headChunk = next;
      idx = 0;
      headIndex = 0;
    }
    if ( QChunk.SLOTS.getAcquire ( chunk.slots, idx << 1 ) == null ) return false;
    drainBatchLoop ( chunk, idx, circuit );
    return true;
  }

  /**
   * Drain loop: process all committed slots across all chunks.
   * No batch limit — drains the entire chain until a null slot or
   * no next chunk. Interleaves transit after each emission.
   * Head updated ONCE at end (not per iteration).
   */
  @SuppressWarnings ( "unchecked" )
  private void drainBatchLoop ( QChunk chunk, int idx, FsCircuit circuit ) {
    Object[] slots = chunk.slots;
    for ( ; ; ) {
      int base = idx << 1;
      Consumer < Object > r = (Consumer < Object >) QChunk.SLOTS.getAcquire ( slots, base );
      if ( r == null ) break;
      Object v = slots[base + 1];
      slots[base] = null;                                    // clear for GC (plain write)
      slots[base + 1] = null;                                // clear for GC (plain write)

      if ( circuit.isMarker ( r ) ) {
        circuit.fireMarker ( r, v );                       // cold: separate type profile
      } else {
        r.accept ( v );                                    // hot: monomorphic receptor
        if ( circuit.transitHasWork () ) {                 // guard: plain field read
          do { } while ( circuit.drainTransit () );        // depth-first cascade drain
        }
      }

      idx++;
      if ( idx >= QChunk.CAPACITY ) {
        QChunk next = (QChunk) QChunk.NEXT.getAcquire ( chunk );
        if ( next == null ) break;
        recycleFreeChunk ( chunk );
        chunk = next;
        slots = chunk.slots;
        idx = 0;
      }
    }
    headChunk = chunk;
    headIndex = idx;
  }

  /**
   * Peek for ingress work. Returns non-null if a committed slot exists.
   */
  @jdk.internal.vm.annotation.ForceInline
  public Object peek () {
    int idx = headIndex;
    if ( idx >= QChunk.CAPACITY ) return headChunk.next;
    return QChunk.SLOTS.getAcquire ( headChunk.slots, idx << 1 );
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // Free list — Treiber stack
  // ─────────────────────────────────────────────────────────────────────────────

  private QChunk popFreeChunk () {
    for ( ; ; ) {
      QChunk head = (QChunk) FREE_HEAD.getAcquire ( this );
      if ( head == null ) return null;
      QChunk next = head.freeNext;
      if ( FREE_HEAD.compareAndSet ( this, head, next ) ) {
        head.freeNext = null;
        return head;
      }
    }
  }

  private void recycleFreeChunk ( QChunk chunk ) {
    Arrays.fill ( chunk.slots, null );
    QChunk.CLAIMED.setRelease ( chunk, 0 );
    QChunk.NEXT.setRelease ( chunk, null );
    for ( ; ; ) {
      QChunk head = (QChunk) FREE_HEAD.getAcquire ( this );
      chunk.freeNext = head;
      if ( FREE_HEAD.compareAndSet ( this, head, chunk ) ) return;
    }
  }
}
