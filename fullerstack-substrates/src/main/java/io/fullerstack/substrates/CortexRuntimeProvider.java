package io.fullerstack.substrates;

import io.humainary.substrates.api.Substrates.Cortex;
import io.humainary.substrates.spi.CortexProvider;

/**
 * SPI Provider for fullerstack-substrates implementation.
 * <p>
 * This class is registered in META-INF/services/io.humainary.substrates.spi.CortexProvider
 * to provide the CortexRuntime implementation to the Substrates API.
 * <p>
 * The SPI mechanism allows the Substrates API to discover and use our implementation
 * automatically via {@code Substrates.cortex()}.
 *
 * @author Fullerstack
 * @since 1.0.0
 */
public final class CortexRuntimeProvider
  extends CortexProvider {

  /**
   * Creates a new CortexRuntime instance.
   * <p>
   * This method is called by the CortexProvider base class to create the singleton
   * Cortex instance used throughout the application.
   *
   * @return A new CortexRuntime instance
   */
  @Override
  protected Cortex create () {
    return new CortexRuntime ();
  }

}
