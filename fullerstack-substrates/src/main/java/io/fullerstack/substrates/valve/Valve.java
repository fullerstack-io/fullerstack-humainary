package io.fullerstack.substrates.valve;

import java.util.concurrent.BlockingDeque;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Controls the flow of tasks through a virtual thread processor using dual-queue architecture.
 * <p>
 * Ingress Queue (FIFO): External emissions from outside the circuit<br>
 * Transit Deque (FIFO): Recursive emissions from within the circuit, processed with priority
 * <p>
 * Execution is depth-first: recursive emissions are fully drained before processing next external task.
 */
public class Valve implements AutoCloseable {

  private final String name;
  private final BlockingQueue < Runnable > ingressQueue;
  private final BlockingDeque < Runnable > transitDeque;

  private          Thread  processor;
  private volatile boolean running   = true;
  private volatile boolean executing = false;
  private final    Object  idleLock  = new Object ();
  private volatile int     awaiters  = 0;

  private static final ThreadLocal < Boolean > inCircuitContext = ThreadLocal.withInitial ( () -> Boolean.FALSE );

  /**
   * Creates a new Valve with lazy-initialized virtual thread processor.
   *
   * @param name descriptive name for the valve
   */
  public Valve ( String name ) {
    this.name = name;
    this.ingressQueue = new LinkedBlockingQueue <> ();
    this.transitDeque = new LinkedBlockingDeque <> ();
    this.processor = null;
  }

  /**
   * Submits a task to the valve for execution.
   * <p>
   * Routes to transit deque if called from circuit thread (recursive), otherwise to ingress queue.
   *
   * @param task the task to execute
   * @return true if task was accepted, false if valve is closed
   */
  public boolean submit ( Runnable task ) {
    if ( task != null && running ) {
      if ( ( processor != null && Thread.currentThread () == processor ) || inCircuitContext.get () ) {
        return transitDeque.offerLast ( task );
      } else {
        if ( !executing && ingressQueue.isEmpty () && transitDeque.isEmpty () ) {
          synchronized ( idleLock ) {
            if ( !executing && ingressQueue.isEmpty () && transitDeque.isEmpty () ) {
              executing = true;
              inCircuitContext.set ( Boolean.TRUE );
              try {
                task.run ();
                Runnable transitTask;
                while ( ( transitTask = transitDeque.pollFirst () ) != null ) {
                  transitTask.run ();
                }
                return true;
              } finally {
                inCircuitContext.set ( Boolean.FALSE );
                executing = false;
              }
            }
          }
        }

        ensureProcessorStarted ();
        return ingressQueue.offer ( task );
      }
    }
    return false;
  }

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
   * <p>
   * Uses adaptive waiting: spins briefly first, then parks if still busy.
   * This avoids expensive park/unpark cycles when valve completes quickly.
   *
   * @param contextName name of the calling context for error messages
   * @throws IllegalStateException if called from valve's thread
   */
  public void await ( String contextName ) {
    if ( ( processor != null && Thread.currentThread () == processor ) || inCircuitContext.get () ) {
      throw new IllegalStateException (
        "Cannot call " + contextName + "::await from within a " + contextName.toLowerCase () + "'s thread"
      );
    }

    if ( processor == null && !executing && ingressQueue.isEmpty () && transitDeque.isEmpty () ) {
      return;
    }

    if ( !executing && ingressQueue.isEmpty () && transitDeque.isEmpty () ) {
      if ( !executing && ingressQueue.isEmpty () && transitDeque.isEmpty () ) {
        return;
      }
    }

    int spinCount = 0;
    int maxSpins = 1000;

    while ( running && ( executing || !ingressQueue.isEmpty () || !transitDeque.isEmpty () ) ) {
      if ( spinCount < maxSpins ) {
        Thread.onSpinWait ();
        spinCount++;
      } else {
        awaiters++;
        try {
          synchronized ( idleLock ) {
            while ( running && ( executing || !ingressQueue.isEmpty () || !transitDeque.isEmpty () ) ) {
              try {
                idleLock.wait ();
              } catch ( InterruptedException e ) {
                Thread.currentThread ().interrupt ();
                throw new RuntimeException ( contextName + " await interrupted", e );
              }
            }
          }
        } finally {
          awaiters--;
        }
        return;
      }
    }
  }

  /**
   * Checks if the valve is currently idle (no tasks executing or queued).
   *
   * @return true if idle, false if tasks are pending or executing
   */
  public boolean isIdle () {
    return !executing && ingressQueue.isEmpty () && transitDeque.isEmpty ();
  }

  /**
   * Background processor that executes tasks using dual-queue depth-first execution.
   * <p>
   * Transit deque has priority: fully drained before taking next ingress task.
   * Notifies waiting threads when valve becomes idle.
   */
  private void processQueue () {
    while ( running && !Thread.interrupted () ) {
      try {
        Runnable task = transitDeque.pollFirst ();
        if ( task == null ) {
          task = ingressQueue.take ();
        }

        executing = true;
        inCircuitContext.set ( Boolean.TRUE );
        try {
          task.run ();
        } finally {
          inCircuitContext.set ( Boolean.FALSE );
          executing = false;

          if ( ingressQueue.isEmpty () && transitDeque.isEmpty () && awaiters > 0 ) {
            synchronized ( idleLock ) {
              idleLock.notifyAll ();
            }
          }
        }
      } catch ( InterruptedException e ) {
        Thread.currentThread ().interrupt ();
        break;
      } catch ( Exception e ) {
        System.err.println ( "Error executing task in valve '" + name + "': " + e.getMessage () );
        inCircuitContext.set ( Boolean.FALSE );
        executing = false;

        if ( ingressQueue.isEmpty () && transitDeque.isEmpty () && awaiters > 0 ) {
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
   */
  @Override
  public void close () {
    if ( running ) {
      running = false;

      if ( processor != null ) {
        processor.interrupt ();
        try {
          processor.join ( 1000 );
        } catch ( InterruptedException e ) {
          Thread.currentThread ().interrupt ();
        }
      }

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
