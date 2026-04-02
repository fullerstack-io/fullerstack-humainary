package io.fullerstack.agents.safety;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/// Tracks agent access patterns and detects anomalies.
///
/// Maintains a baseline of which tools and data sources the agent
/// normally uses. When the agent accesses something outside its
/// baseline — a tool never used before, a data source never queried —
/// that's a detectable anomaly.
///
/// Detection types:
/// - **NOVEL_ACCESS**: tool/data source never seen before (highest risk)
/// - **FREQUENCY_SPIKE**: tool used far more than normal
/// - **SCOPE_EXPANSION**: agent accessing broader data than usual
///
/// These map to Serventis Trends signs:
/// - NOVEL_ACCESS → SPIKE (single point outside baseline)
/// - FREQUENCY_SPIKE → DRIFT (sustained increase)
/// - SCOPE_EXPANSION → DRIFT (gradual broadening)
public final class AccessPatternMonitor {

  /// An access event recorded by the monitor.
  public record AccessEvent ( String resource, AccessType type, long timestamp ) {}

  public enum AccessType { TOOL_CALL, DATA_READ, DATA_WRITE, EMAIL_ACCESS, FILE_ACCESS, API_CALL }

  public enum Anomaly { NOVEL_ACCESS, FREQUENCY_SPIKE, SCOPE_EXPANSION }

  /// A detected anomaly.
  public record AnomalyEvent ( Anomaly type, String resource, String detail, long timestamp ) {}

  /// Baseline: resources the agent has accessed during normal operation.
  private final Set < String > baselineResources = new CopyOnWriteArraySet <> ();

  /// Access counts per resource for frequency analysis.
  private final Map < String, AtomicInteger > accessCounts = new ConcurrentHashMap <> ();

  /// Average access count per resource (for spike detection).
  private final Map < String, Double > averageRates = new ConcurrentHashMap <> ();

  /// All detected anomalies.
  private final java.util.List < AnomalyEvent > anomalies =
    new java.util.concurrent.CopyOnWriteArrayList <> ();

  /// Anomaly callbacks.
  private final java.util.List < Consumer < AnomalyEvent > > callbacks =
    new java.util.concurrent.CopyOnWriteArrayList <> ();

  /// Whether baseline learning is active (first N calls establish baseline).
  private boolean baselineLearning = true;
  private int baselineWindowSize = 50;
  private int totalAccesses;

  /// Record an access and check for anomalies.
  public void recordAccess ( String resource, AccessType type ) {
    totalAccesses++;

    // Track frequency
    accessCounts.computeIfAbsent ( resource, k -> new AtomicInteger () ).incrementAndGet ();

    if ( baselineLearning ) {
      // Learning phase: build the baseline
      baselineResources.add ( resource );
      if ( totalAccesses >= baselineWindowSize ) {
        baselineLearning = false;
        // Snapshot current rates as baseline averages
        for ( var entry : accessCounts.entrySet () ) {
          averageRates.put ( entry.getKey (),
            (double) entry.getValue ().get () / totalAccesses );
        }
      }
      return;
    }

    // Detection phase: check for anomalies

    // 1. Novel access — resource never seen during baseline
    if ( !baselineResources.contains ( resource ) ) {
      var anomaly = new AnomalyEvent (
        Anomaly.NOVEL_ACCESS, resource,
        "Resource '" + resource + "' accessed for the first time (type: " + type + ")",
        System.nanoTime ()
      );
      anomalies.add ( anomaly );
      callbacks.forEach ( cb -> cb.accept ( anomaly ) );
      // Add to baseline to avoid repeated alerts for same resource
      baselineResources.add ( resource );
    }

    // 2. Frequency spike — resource used much more than average
    var avgRate = averageRates.get ( resource );
    if ( avgRate != null && avgRate > 0 ) {
      double currentRate = (double) accessCounts.get ( resource ).get () / totalAccesses;
      if ( currentRate > avgRate * 3.0 ) { // 3x above average
        var anomaly = new AnomalyEvent (
          Anomaly.FREQUENCY_SPIKE, resource,
          String.format ( "Resource '%s' frequency %.1fx above baseline", resource, currentRate / avgRate ),
          System.nanoTime ()
        );
        anomalies.add ( anomaly );
        callbacks.forEach ( cb -> cb.accept ( anomaly ) );
      }
    }
  }

  /// Register a callback for anomaly detection.
  public void onAnomaly ( Consumer < AnomalyEvent > callback ) {
    callbacks.add ( callback );
  }

  /// Force baseline learning to complete (for testing).
  public void completeBaseline () {
    baselineLearning = false;
    for ( var entry : accessCounts.entrySet () ) {
      averageRates.put ( entry.getKey (),
        totalAccesses > 0 ? (double) entry.getValue ().get () / totalAccesses : 0.0 );
    }
  }

  /// Set the baseline window size (default 50).
  public void setBaselineWindowSize ( int size ) {
    this.baselineWindowSize = size;
  }

  // ── Query ───────────────────────────────────────────────────────────────

  public Set < String > baselineResources () { return Set.copyOf ( baselineResources ); }
  public java.util.List < AnomalyEvent > anomalies () { return java.util.List.copyOf ( anomalies ); }
  public int totalAccesses () { return totalAccesses; }
  public boolean isBaselineLearning () { return baselineLearning; }

  public boolean hasAnomalies () { return !anomalies.isEmpty (); }
  public int anomalyCount () { return anomalies.size (); }
}
