package io.fullerstack.substrates;

import jdk.internal.vm.annotation.Contended;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/**
 * Unified chunk for both ingress (MPSC) and transit (single-threaded) queues.
 *
 * <p>Each chunk holds up to {@link #CAPACITY} receiver+value pairs in an
 * interleaved {@code Object[]} array: {@code [r0, v0, r1, v1, ...]}.
 *
 * <p>Ingress: producers claim slots via atomic {@code getAndAdd} on {@link #claimed}.
 * Consumer reads committed slots via {@code getAcquire} on the receiver position.
 * Exhausted chunks are recycled via a Treiber stack free list.
 *
 * <p>Transit: single pre-allocated chunk reused every drain cycle. Plain writes
 * (no atomics). Overflow chunks linked via {@link #next} for cascade depth &gt; 64.
 */
final class QChunk {

  static final int CAPACITY  = 64;
  static final int ARRAY_LEN = CAPACITY << 1;  // 128

  final    Object[] slots = new Object[ARRAY_LEN];   // interleaved [r0,v0,r1,v1,...]
  volatile QChunk   next;                            // link to next chunk (consumer reads)

  @Contended  // isolate producer's atomic from consumer's slots/next reads
  volatile int claimed;                            // ingress: atomic getAndAdd
  QChunk freeNext;                        // ingress: Treiber stack link

  static final VarHandle SLOTS;
  static final VarHandle CLAIMED;
  static final VarHandle NEXT;

  static {
    try {
      MethodHandles.Lookup l = MethodHandles.lookup ();
      SLOTS = MethodHandles.arrayElementVarHandle ( Object[].class );
      CLAIMED = l.findVarHandle ( QChunk.class, "claimed", int.class );
      NEXT = l.findVarHandle ( QChunk.class, "next", QChunk.class );
    } catch ( ReflectiveOperationException e ) {
      throw new ExceptionInInitializerError ( e );
    }
  }
}
