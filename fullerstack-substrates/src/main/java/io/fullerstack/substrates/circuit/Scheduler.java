package io.fullerstack.substrates.circuit;

/**
 * Internal interface for scheduling work within a Circuit's execution context.
 * <p>
 * < p >This abstraction allows components (Conduit, Cell, Pipe, etc.) to schedule
 * work on the Circuit's queue without directly depending on Queue implementation details.
 * <p>
 * < p >Circuit implements this interface and passes itself to components, maintaining
 * proper encapsulation and following the "use this circuit" pattern from the API.
 */
public interface Scheduler {

  /**
   * Schedules a runnable to execute on the Circuit's queue.
   *
   * @param task the task to schedule
   */
  void schedule ( Runnable task );


  /**
   * Blocks until all scheduled tasks complete.
   */
  void await ();
}
