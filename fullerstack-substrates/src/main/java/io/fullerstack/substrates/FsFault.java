package io.fullerstack.substrates;

import io.humainary.substrates.api.Substrates.Fault;

/// Concrete implementation of Substrates.Fault for throwing errors.
public final class FsFault extends Fault {

  FsFault ( String message ) {
    super ( message );
  }

  FsFault ( String message, Throwable cause ) {
    super ( message, cause );
  }

}
