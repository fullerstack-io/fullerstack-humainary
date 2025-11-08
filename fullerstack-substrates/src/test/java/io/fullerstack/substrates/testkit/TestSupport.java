package io.fullerstack.substrates.testkit;

import io.humainary.substrates.api.Substrates;
import io.humainary.substrates.api.Substrates.Cortex;
import io.humainary.substrates.api.Substrates.NotNull;

/// Common support for substrate test kit classes that require access to the singleton cortex.
/// The SPI provider is configured via the testkit Maven profile in the root pom.xml.
abstract class TestSupport {

  // Cache the cortex instance to avoid repeated SPI lookups
  private static final Cortex CORTEX_INSTANCE = Substrates.cortex();

  /// Returns the shared cortex instance configured via the SPI provider property.
  @NotNull
  protected static Cortex cortex () {

    return
      CORTEX_INSTANCE;

  }

}
