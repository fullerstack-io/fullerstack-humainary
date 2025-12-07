package io.fullerstack.substrates;

import io.humainary.substrates.api.Substrates.Cortex;
import io.humainary.substrates.spi.CortexProvider;

/**
 * SPI Provider for NanoSubstrates minimal implementation.
 * <p>
 * This provider can be used to test the minimal NanoSubstrates implementation
 * against the Humainary TCK.
 */
public final class NanoCortexProvider
  extends CortexProvider {

  @Override
  protected Cortex create () {
    return NanoSubstrates.cortex ();
  }

}
