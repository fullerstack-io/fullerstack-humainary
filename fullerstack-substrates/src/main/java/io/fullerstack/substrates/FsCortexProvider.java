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

  static {
    try {
      java.nio.file.Files.writeString (
        java.nio.file.Path.of ( "/tmp/fscortex-loaded.log" ),
        "FsCortexProvider class loaded!\n",
        java.nio.file.StandardOpenOption.CREATE,
        java.nio.file.StandardOpenOption.APPEND
      );
    } catch ( Exception e ) { }
  }

  @Override
  protected Cortex create () {
    try {
      java.nio.file.Files.writeString (
        java.nio.file.Path.of ( "/tmp/fscortex-loaded.log" ),
        "FsCortex.create() called!\n",
        java.nio.file.StandardOpenOption.CREATE,
        java.nio.file.StandardOpenOption.APPEND
      );
    } catch ( Exception e ) { }
    return new FsCortex ();
  }

}
