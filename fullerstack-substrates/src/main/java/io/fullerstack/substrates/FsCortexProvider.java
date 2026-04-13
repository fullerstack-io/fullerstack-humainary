package io.fullerstack.substrates;

import io.humainary.substrates.api.Substrates.Cortex;
import io.humainary.substrates.spi.CortexProvider;

/// SPI Provider for Fullerstack Substrates implementation.
public final class FsCortexProvider extends CortexProvider {

  @Override
  protected Cortex create () {
    return new FsCortex ();
  }

}
