package io.fullerstack.substrates;

import io.humainary.substrates.api.Substrates.Name;
import io.humainary.substrates.api.Substrates.Provided;
import io.humainary.substrates.api.Substrates.Slot;

/// An immutable name-value pair - record for minimal overhead.
@Provided
record FsSlot<T>(Name name, T value, Class<T> type) implements Slot<T> {}
