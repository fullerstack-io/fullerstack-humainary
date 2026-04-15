package io.fullerstack.substrates;

import java.util.function.Consumer;

/**
 * Single-threaded transit queue using a pre-allocated ring buffer.
 *
 * <p>Zero allocation in steady state. The home chunk is reused as a
 * circular buffer: when the read cursor catches the write cursor during
 * cascade processing, both reset to 0 — the same 64 slots are reused
 * indefinitely regardless of cascade depth.
 *
 * <p>Overflow (cascade depth &gt; 64 simultaneous pending entries) links
 * a new chunk. This is rare — cascades typically have 1 pending entry
 * (write one, read one, write one, read one...).
 */
final class TransitQueue {

  private final QChunk homeChunk = new QChunk ();
  private       QChunk writeChunk;
  private       int    writeIndex;
  private       int    readIndex;

  TransitQueue () {
    writeChunk = homeChunk;
  }

  @jdk.internal.vm.annotation.ForceInline
  void enqueue ( Consumer < Object > receiver, Object value ) {
    int idx = writeIndex;
    if ( idx < QChunk.CAPACITY ) {
      int base = idx << 1;
      writeChunk.slots[base] = receiver;
      writeChunk.slots[base + 1] = value;
      writeIndex = idx + 1;
    } else {
      enqueueSlow ( receiver, value );
    }
  }

  private void enqueueSlow ( Consumer < Object > receiver, Object value ) {
    QChunk overflow = new QChunk ();
    writeChunk.next = overflow;
    writeChunk = overflow;
    overflow.slots[0] = receiver;
    overflow.slots[1] = value;
    writeIndex = 1;
  }

  @jdk.internal.vm.annotation.ForceInline
  boolean hasWork () {
    return readIndex != writeIndex || writeChunk != homeChunk;
  }

  @jdk.internal.vm.annotation.ForceInline
  boolean drain () {
    if ( readIndex == writeIndex && writeChunk == homeChunk ) return false;
    drainSlots ();
    return true;
  }

  @SuppressWarnings ( "unchecked" )
  private void drainSlots () {
    int ri = readIndex;
    int wi = writeIndex;
    QChunk rc = homeChunk;
    Object[] slots = rc.slots;
    for ( ; ; ) {
      if ( rc == writeChunk && ri >= wi ) break;
      int base = ri << 1;
      Consumer < Object > receiver = (Consumer < Object >) slots[base];
      Object value = slots[base + 1];
      slots[base] = null;
      slots[base + 1] = null;
      ri++;

      // Process — may enqueue more transit work
      receiver.accept ( value );

      // Re-read writeIndex (accept may have enqueued)
      wi = writeIndex;

      if ( ri >= QChunk.CAPACITY ) {
        QChunk next = rc.next;
        if ( next == null ) break;
        rc.next = null;
        rc = next;
        slots = rc.slots;
        ri = 0;
      }

      // Ring buffer reset: when read catches write on the home chunk,
      // reset both cursors to reuse the same slots. This prevents
      // overflow allocation in cascade patterns (write 1, read 1, ...).
      if ( rc == writeChunk && ri == wi ) {
        writeIndex = 0;
        wi = 0;
        ri = 0;
        // Stay on home chunk if we drifted to overflow
        if ( rc != homeChunk ) {
          writeChunk = homeChunk;
          rc = homeChunk;
          slots = rc.slots;
        }
      }
    }
    // Final reset
    readIndex = 0;
    writeIndex = 0;
    writeChunk = homeChunk;
  }
}
