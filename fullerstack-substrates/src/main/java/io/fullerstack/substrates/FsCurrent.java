package io.fullerstack.substrates;

import io.humainary.substrates.api.Substrates.Current;
import io.humainary.substrates.api.Substrates.Subject;

/// Represents the execution context from which substrate operations originate.
///
/// A [Current] identifies the execution context (thread, coroutine, fiber, etc.)
/// that invokes substrate operations. It is obtained via [Cortex#current()] in
/// a manner analogous to `Thread.currentThread()` in Java.
///
/// ## Temporal Contract
///
/// **IMPORTANT**: Current follows a temporal contract - it is only valid within the
/// execution context (thread) that obtained it.
///
/// @see FsCortex#current()
/// @see Subject
final class FsCurrent
  implements Current {

  /// The subject identity for this current context.
  private final Subject < Current > subject;

  /// Creates a new current context with the given subject identity.
  ///
  /// @param subject the subject identity for this context
  FsCurrent ( Subject < Current > subject ) {
    this.subject = subject;
  }

  /// Returns the subject identity of this current context.
  ///
  /// @return the subject of this current context
  @Override
  public Subject < Current > subject () {
    return subject;
  }

}
