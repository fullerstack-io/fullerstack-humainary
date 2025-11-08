package io.fullerstack.substrates.current;

import io.humainary.substrates.api.Substrates;
import io.fullerstack.substrates.subject.ContextualSubject;
import io.fullerstack.substrates.name.InternedName;
import io.fullerstack.substrates.id.UuidIdentifier;

import java.util.Objects;

/**
 * Thread-based implementation of Current interface.
 * <p>
 * This implementation wraps the current thread information as a Current instance,
 * providing access to thread identity via Subject.
 * <p>
 * Each call to Thread.currentThread() creates a new Current wrapper, but the underlying
 * subject is cached per thread to maintain identity consistency.
 */
public final class ThreadCurrent implements Substrates.Current {

  private final Substrates.Subject < Substrates.Current > subject;

  // ThreadLocal cache to ensure consistent identity per thread
  // Cache the ThreadCurrent instance itself, not just the subject
  private static final ThreadLocal < ThreadCurrent > THREAD_CURRENTS =
    ThreadLocal.withInitial ( ThreadCurrent::createForCurrentThread );

  /**
   * Creates a Current for the current thread.
   * Uses cached subject for the thread to maintain identity consistency.
   */
  private ThreadCurrent ( Substrates.Subject < Substrates.Current > subject ) {
    this.subject = subject;
  }

  /**
   * Factory method to create a Current for the calling thread.
   * Returns the same instance for the same thread (singleton per thread).
   *
   * @return A Current representing the current execution context
   */
  public static Substrates.Current of () {
    return THREAD_CURRENTS.get ();
  }

  /**
   * Creates a new ThreadCurrent for the calling thread.
   * Called by ThreadLocal.withInitial() on first access per thread.
   *
   * @return A new ThreadCurrent instance for the current thread
   */
  private static ThreadCurrent createForCurrentThread () {
    return new ThreadCurrent ( createSubjectForCurrentThread () );
  }

  @SuppressWarnings ( "unchecked" )
  private static Substrates.Subject < Substrates.Current > createSubjectForCurrentThread () {
    Thread thread = Thread.currentThread ();
    // Handle empty thread names (common with virtual threads)
    String threadName = thread.getName ();
    if ( threadName == null || threadName.isBlank () ) {
      threadName = "thread-" + thread.threadId ();
    }
    Substrates.Name name = InternedName.of ( threadName );

    // Create a deterministic UUID based on thread ID
    // Using thread ID as the most significant bits ensures different threads get different IDs
    long threadId = thread.threadId ();
    java.util.UUID uuid = new java.util.UUID ( threadId, 0L );
    Substrates.Id id = UuidIdentifier.of ( uuid );

    return new ContextualSubject < Substrates.Current > (
      id,
      name,
      null,  // No state for Current
      (Class < Substrates.Current >) (Class <?>) Substrates.Current.class
    );
  }

  @Override
  public Substrates.Subject < Substrates.Current > subject () {
    return subject;
  }

  @Override
  public boolean equals ( Object obj ) {
    if ( this == obj ) return true;
    if ( ! ( obj instanceof Substrates.Current ) ) return false;
    Substrates.Current other = (Substrates.Current) obj;
    return Objects.equals ( subject, other.subject () );
  }

  @Override
  public int hashCode () {
    return Objects.hash ( subject );
  }

  @Override
  public String toString () {
    return "Current[" + subject + "]";
  }
}
