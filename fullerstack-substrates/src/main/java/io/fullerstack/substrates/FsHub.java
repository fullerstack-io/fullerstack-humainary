package io.fullerstack.substrates;

import java.util.ArrayList;

/// Subscriber management hub — holds the subscriber list and version counter.
///
/// One hub per conduit. Tiny object — fits in one cache line.
/// The version counter is checked by channels on each emission (§7.6.2).
/// Channels that detect a mismatch rebuild their downstream receptor list.
///
/// All fields are circuit-thread-only — no synchronization needed.
final class FsHub < E > {

  /// Version counter — incremented on subscriber add/remove.
  int subscriberVersion;

  /// Active subscribers.
  ArrayList < FsSubscriber < E > > subscribersList;

  /// Cached snapshot — rebuilt when version changes.
  @SuppressWarnings ( "unchecked" )
  FsSubscriber < E >[] subscribersSnapshot = (FsSubscriber < E >[]) EMPTY;

  private int snapshotVersion = -1;

  private static final FsSubscriber < ? >[] EMPTY = new FsSubscriber < ? >[0];

  void addSubscriber ( FsSubscriber < E > subscriber ) {
    if ( subscribersList == null ) {
      subscribersList = new ArrayList <> ();
    }
    subscribersList.add ( subscriber );
    subscriberVersion++;
  }

  void removeSubscriber ( FsSubscriber < E > subscriber ) {
    if ( subscribersList != null ) {
      subscribersList.remove ( subscriber );
      subscriberVersion++;
    }
  }

  boolean hasSubscribers () {
    return subscribersList != null && !subscribersList.isEmpty ();
  }

  @SuppressWarnings ( "unchecked" )
  FsSubscriber < E >[] ensureSnapshot () {
    if ( snapshotVersion != subscriberVersion ) {
      subscribersSnapshot = ( subscribersList == null || subscribersList.isEmpty () )
                            ? (FsSubscriber < E >[]) EMPTY
                            : subscribersList.toArray ( new FsSubscriber[0] );
      snapshotVersion = subscriberVersion;
    }
    return subscribersSnapshot;
  }
}
