package io.fullerstack.substrates;

/// Concrete implementation of Substrates.Fault for throwing errors.
public final class FsFault extends io.humainary.substrates.api.Substrates.Fault {

  public FsFault ( String message ) {
    super ( message );
  }

  public FsFault ( String message, Throwable cause ) {
    super ( message, cause );
  }

}
