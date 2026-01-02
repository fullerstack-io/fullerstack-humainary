package io.fullerstack.substrates;

import io.humainary.substrates.api.Substrates.Cortex;
import io.humainary.substrates.spi.CortexProvider;

/**
 * SPI Provider for FsSubstrates implementation.
 * <p>
 * This provider can be used to test the Fullerstack implementation against the
 * Humainary TCK.
 */
public final class FsCortexProvider extends CortexProvider {

  @Override
  protected Cortex create () {
    return new FsCortex ();
  }

}
