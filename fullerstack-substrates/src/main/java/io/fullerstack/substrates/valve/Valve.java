package io.fullerstack.substrates.valve;

import java.util.concurrent.BlockingDeque;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Valve - Controls the flow of tasks through a virtual thread processor.
 * <p>
 * < p >< b >Dual-Queue Architecture:</b >
 * < ul >
 * < li >< b >Ingress Queue (FIFO)</b >: Emissions from external threads (outside circuit)</li >
 * < li >< b >Transit Deque (LIFO)</b >: Emissions from circuit thread (recursive, stack-like)</li >
 * < li >< b >Priority</b >: Transit deque processed FIRST (true depth-first execution)</li >
 * </ul >
 * <p>
 * < p >< b >Depth-First Execution Example:</b >
 * < pre >
 * External thread emits: [A, B, C] → Ingress queue
 * Circuit processes A, which emits: [A1, A2] → Transit deque (pushed to front)
 * Circuit processes A1, which emits: [A1a, A1b] → Transit deque (pushed to front)
 * <p>
 * Execution order: A, A1, A1a, A1b, A2, B, C (true depth-first)
 * Transit uses LIFO (stack) so nested emissions are processed immediately.
 * </pre >
 * <p>
 * < p >< b >Design Pattern:</b >
 * < ul >
 * < li >Valve = Dual BlockingQueue + Virtual Thread processor</li >
 * < li >Emissions → Tasks (via submit, routed to appropriate queue)</li >
 * < li >Virtual thread parks when both queues empty (BlockingQueue.take())</li >
 * < li >Virtual thread unparks and executes when task arrives</li >
 * < li >Transit queue has priority - fully drained before next ingress item</li >
 * </ul >
 * <p>
 * < p >< b >Usage:</b >
 * < pre >
 * Valve valve = new Valve("circuit-main");
 * valve.submit(() -> System.out.println("Task 1")); // External thread → Ingress
 * valve.close(); // Shutdown when done
 * </pre >
 * <p>
 * < p >This abstraction makes William's "valve" pattern explicit in the codebase,
 * aligning terminology with the reference implementation.
 */
public class Valve implements AutoCloseable {

  private final String name;

  // Dual-Queue Architecture
  private final BlockingQueue < Runnable > ingressQueue;  // External emissions (FIFO)
  private final BlockingDeque < Runnable > transitDeque;  // Recursive emissions (LIFO for depth-first)

  private          Thread  processor;  // Lazy-initialized on first emission
  private volatile boolean running   = true;
  private volatile boolean executing = false;

  // Synchronization for event-driven await() (eliminates polling)
  private final Object idleLock = new Object ();

  // ThreadLocal to mark when we're executing circuit tasks (processor thread OR sync fast-path)
  private static final ThreadLocal < Boolean > inCircuitContext = ThreadLocal.withInitial ( () -> Boolean.FALSE );

  /**
   * Creates a new Valve with lazy-initialized virtual thread processor and dual-queue architecture.
   * The processor thread is started only on first emission (lazy initialization).
   *
   * @param name descriptive name for the valve (used in thread naming)
   */
  public Valve ( String name ) {
    this.name = name;
    this.ingressQueue = new LinkedBlockingQueue <> ();  // External emissions (FIFO)
    this.transitDeque = new LinkedBlockingDeque <> ();  // Recursive emissions (LIFO stack)
    this.processor = null;  // Lazy-initialized on first async emission
  }

  /**
   * Submits a task to the valve for execution.
   * <p>
   * < p >< b >Dual-Queue Routing:</b >
   * < ul >
   * < li >If called from circuit thread (recursive): < b >Pushes to front of Transit deque</b > (LIFO stack)</li >
   * < li >If called from external thread: < b >Appends to Ingress queue</b > (FIFO)</li >
   * </ul >
   * <p>
   * < p >This ensures true depth-first execution: nested recursive emissions are processed
   * immediately (stack behavior), before siblings or external emissions.
   *
   * @param task the task to execute
   * @return true if task was accepted, false if valve is closed
   */
  public boolean submit ( Runnable task ) {
    if ( task != null && running ) {
      //  Route to appropriate queue based on calling thread OR sync fast-path context
      // If we're executing on processor thread OR via sync fast-path, this is a recursive emission
      if ( ( processor != null && Thread.currentThread () == processor ) || inCircuitContext.get () ) {
        // Recursive emission from circuit thread → Append to Transit (FIFO within batch)
        // Transit has priority, but siblings maintain emit order
        return transitDeque.offerLast ( task );  // Append to back (preserves order)
      } else {
        // OPTIMIZATION: Sync fast-path - if valve is idle AND queues empty, execute directly
        // This skips async overhead (park/unpark) for single-threaded emit batches
        // Must check: NOT executing, both queues empty, and we can acquire the lock
        // Safety: We set executing=true to prevent processor thread from taking other work
        if ( !executing && ingressQueue.isEmpty () && transitDeque.isEmpty () ) {
          synchronized ( idleLock ) {
            // Double-check after acquiring lock (race condition protection)
            if ( !executing && ingressQueue.isEmpty () && transitDeque.isEmpty () ) {
              executing = true;
              inCircuitContext.set ( Boolean.TRUE );  // Mark as circuit context for await() check
              try {
                // Execute initial task
                task.run ();

                // Process any recursive emissions from Transit deque (depth-first with FIFO siblings)
                // This ensures Transit priority and correct ordering: A, A1, A2, A1a, A1b
                Runnable transitTask;
                while ( ( transitTask = transitDeque.pollFirst () ) != null ) {
                  transitTask.run ();
                }

                return true;
              } finally {
                inCircuitContext.set ( Boolean.FALSE );  // Clear circuit context
                executing = false;
                // No need to notify - we're returning to caller immediately
              }
            }
          }
        }

        // Slow path: External emission → Append to Ingress queue (FIFO)
        // Lazy initialization: Start processor thread on first async emission
        ensureProcessorStarted ();
        return ingressQueue.offer ( task );
      }
    }
    return false;
  }

  /**
   * Ensures the processor thread is started (lazy initialization).
   * Thread-safe via double-checked locking.
   */
  private void ensureProcessorStarted () {
    if ( processor == null ) {
      synchronized ( idleLock ) {
        if ( processor == null ) {
          processor = Thread.startVirtualThread ( this::processQueue );
        }
      }
    }
  }

  /**
   * Blocks until all queued tasks are executed and the valve is idle.
   * Cannot be called from the valve's own thread.
   * <p>
   * < p >< b >Event-Driven Design:</b >
   * Uses wait/notify mechanism instead of polling. The valve processor notifies
   * waiting threads immediately when it becomes idle, eliminating polling overhead
   * and providing zero-latency wake-up.
   *
   * @param contextName name of the calling context (e.g., "Circuit", "Valve") for error messages (will be lowercased for possessive)
   * @throws IllegalStateException if called from valve's thread
   */
  public void await ( String contextName ) {
    // Cannot be called from valve's own thread OR from sync fast-path execution
    if ( ( processor != null && Thread.currentThread () == processor ) || inCircuitContext.get () ) {
      throw new IllegalStateException (
        "Cannot call " + contextName + "::await from within a " + contextName.toLowerCase () + "'s thread"
      );
    }

    // Fast path: If processor was never started (no async emissions), we're already idle
    if ( processor == null && !executing && ingressQueue.isEmpty () && transitDeque.isEmpty () ) {
      return;
    }

    // Event-driven wait - no polling!
    synchronized ( idleLock ) {
      //  Check BOTH ingress queue and transit deque
      while ( running && ( executing || !ingressQueue.isEmpty () || !transitDeque.isEmpty () ) ) {
        try {
          idleLock.wait ();  // Block until notified by processor
        } catch ( InterruptedException e ) {
          Thread.currentThread ().interrupt ();
          throw new RuntimeException ( contextName + " await interrupted", e );
        }
      }
    }
  }

  /**
   * Checks if the valve is currently idle (no tasks executing or queued).
   *
   * @return true if idle, false if tasks are pending or executing
   */
  public boolean isIdle () {
    //  Check BOTH ingress queue and transit deque
    return !executing && ingressQueue.isEmpty () && transitDeque.isEmpty ();
  }

  /**
   * Background processor that executes tasks using dual-queue depth-first execution.
   * Runs in a virtual thread (parks when both queues empty, unparks when task arrives).
   * <p>
   * < p >< b >True Depth-First Algorithm:</b >
   * < ol >
   * < li >Check Transit deque first (pop from front - LIFO stack behavior)</li >
   * < li >If Transit empty, take from Ingress queue (FIFO)</li >
   * < li >Execute task (may push to front of Transit deque recursively)</li >
   * < li >Repeat from step 1 (Transit deque gets priority, newest items first)</li >
   * </ol >
   * <p>
   * < p >Using LIFO for Transit ensures nested recursive emissions are processed immediately:
   * < pre >
   * A emits [A1, A2] → Transit: [A1, A2]
   * Process A1, emits [A1a, A1b] → Transit: [A1a, A1b, A2] (pushed to front)
   * Next process A1a (from front) - true depth-first!
   * </pre >
   * <p>
   * < p >Notifies waiting threads when the valve becomes idle (both queues empty and no task executing).
   */
  private void processQueue () {
    while ( running && !Thread.interrupted () ) {
      try {
        Runnable task = null;

        // Depth-First: Poll from front of Transit (FIFO, preserves sibling order)
        // Transit has priority over Ingress (recursive before external)
        task = transitDeque.pollFirst ();  // Take from front (oldest in Transit batch)

        if ( task == null ) {
          // Transit empty, take from Ingress (blocking - parks if both empty)
          task = ingressQueue.take ();  // PARK when both queues empty
        }

        executing = true;
        inCircuitContext.set ( Boolean.TRUE );  // Mark as circuit context for await() check
        try {
          task.run ();  // Execute (may push to FRONT of Transit deque recursively)
        } finally {
          inCircuitContext.set ( Boolean.FALSE );  // Clear circuit context
          executing = false;

          // Notify awaiting threads if valve is now idle (BOTH queues empty)
          if ( ingressQueue.isEmpty () && transitDeque.isEmpty () ) {
            synchronized ( idleLock ) {
              idleLock.notifyAll ();
            }
          }
        }
      } catch ( InterruptedException e ) {
        Thread.currentThread ().interrupt ();
        break;
      } catch ( Exception e ) {
        // Log error but continue processing
        System.err.println ( "Error executing task in valve '" + name + "': " + e.getMessage () );
        inCircuitContext.set ( Boolean.FALSE );  // Clear circuit context on error
        executing = false;

        // Still notify on error in case both queues are empty
        if ( ingressQueue.isEmpty () && transitDeque.isEmpty () ) {
          synchronized ( idleLock ) {
            idleLock.notifyAll ();
          }
        }
      }
    }
  }

  /**
   * Closes the valve and stops the processor thread.
   * Waits up to 1 second for graceful shutdown.
   * Notifies any threads waiting in await().
   */
  @Override
  public void close () {
    if ( running ) {
      running = false;

      // Only interrupt processor if it was started
      if ( processor != null ) {
        processor.interrupt ();

        try {
          processor.join ( 1000 );
        } catch ( InterruptedException e ) {
          Thread.currentThread ().interrupt ();
        }
      }

      // Notify any threads waiting in await()
      synchronized ( idleLock ) {
        idleLock.notifyAll ();
      }
    }
  }

  /**
   * Returns the valve name.
   *
   * @return the name provided at construction
   */
  public String getName () {
    return name;
  }
}
