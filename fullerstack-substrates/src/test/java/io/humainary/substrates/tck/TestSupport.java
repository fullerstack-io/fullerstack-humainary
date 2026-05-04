package io.humainary.substrates.tck;

import io.humainary.substrates.api.Substrates;

/// Common support for substrate TCK classes.
///
/// Provides access to the singleton Cortex and common utility methods
/// for test implementations. All TCK test classes extend this base class
/// to inherit Substrates types and helper methods.
///
/// The SPI provider is configured in the module pom.xml.
///
/// @author William David Louth
/// @since 1.0
abstract class TestSupport
  implements Substrates {

  /// Returns the singleton Cortex instance for test use.
  ///
  /// @return the cortex instance
  static Cortex cortex () {
    return Substrates.cortex ();
  }

  /// In 2.0, conduits are created directly via circuit.conduit(Class).
  /// The old Integer.class / identity() pattern is no longer needed.

}
