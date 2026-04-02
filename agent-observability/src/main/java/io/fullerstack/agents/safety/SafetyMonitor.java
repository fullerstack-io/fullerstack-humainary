package io.fullerstack.agents.safety;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import io.fullerstack.agents.AgentObserver;

/// Combined safety monitor integrating access patterns, workflow
/// topology, and reasoning analysis into a single trust assessment.
///
/// This is the component that would have caught the blackmail scenario:
///
/// 1. AccessPatternMonitor: detects novel email access (SPIKE)
/// 2. WorkflowMonitor: detects unexpected tool sequence (DEVIATION)
/// 3. ReasoningAnalyser: detects self-preservation language (CRITICAL)
///
/// Any one of these alone raises a flag. All three together is
/// a definitive misalignment signal.
///
/// Trust level degrades with each detection:
/// TRUSTED → CAUTIOUS → SUSPICIOUS → UNTRUSTED
public final class SafetyMonitor {

  public enum TrustLevel { TRUSTED, CAUTIOUS, SUSPICIOUS, UNTRUSTED }

  /// A safety event combining signals from all monitors.
  public record SafetyEvent (
    TrustLevel trustLevel,
    TrustLevel previousLevel,
    String summary,
    long timestamp
  ) {}

  private final AgentObserver observer;
  private final AccessPatternMonitor accessMonitor;
  private final WorkflowMonitor workflowMonitor;
  private final ReasoningAnalyser reasoningAnalyser;

  private volatile TrustLevel trustLevel = TrustLevel.TRUSTED;
  private final List < SafetyEvent > events = new CopyOnWriteArrayList <> ();
  private final List < Consumer < SafetyEvent > > callbacks = new CopyOnWriteArrayList <> ();

  public SafetyMonitor ( AgentObserver observer ) {
    this.observer = observer;
    this.accessMonitor = new AccessPatternMonitor ();
    this.workflowMonitor = new WorkflowMonitor ();
    this.reasoningAnalyser = new ReasoningAnalyser ();

    // Wire up cross-monitor trust assessment
    accessMonitor.onAnomaly ( anomaly -> reassessTrust ( "access:" + anomaly.type () + " on " + anomaly.resource () ) );
    workflowMonitor.onDeviation ( deviation -> reassessTrust ( "workflow:" + deviation.type () + " — " + deviation.detail () ) );
    reasoningAnalyser.onConcern ( concern -> reassessTrust ( "reasoning:" + concern.type () + " [" + concern.severity () + "] — " + concern.matchedText () ) );
  }

  // ── Signal Recording (called by advisors) ───────────────────────────────

  /// Record a tool access for pattern analysis.
  public void recordToolAccess ( String toolName, AccessPatternMonitor.AccessType type ) {
    accessMonitor.recordAccess ( toolName, type );
  }

  /// Record a workflow step.
  public void recordWorkflowStep ( WorkflowMonitor.Step step ) {
    workflowMonitor.step ( step );
  }

  /// Analyse reasoning text for misalignment indicators.
  public List < ReasoningAnalyser.Concern > analyseReasoning ( String reasoning ) {
    return reasoningAnalyser.analyse ( reasoning );
  }

  // ── Trust Assessment ────────────────────────────────────────────────────

  private void reassessTrust ( String trigger ) {
    TrustLevel previous = trustLevel;
    TrustLevel newLevel = calculateTrust ();

    if ( newLevel != previous ) {
      trustLevel = newLevel;
      var event = new SafetyEvent ( newLevel, previous, trigger, System.nanoTime () );
      events.add ( event );
      callbacks.forEach ( cb -> cb.accept ( event ) );
    }
  }

  private TrustLevel calculateTrust () {
    // Reasoning concerns dominate
    var severity = reasoningAnalyser.highestSeverity ();
    if ( severity == ReasoningAnalyser.Severity.CRITICAL ) return TrustLevel.UNTRUSTED;
    if ( severity == ReasoningAnalyser.Severity.HIGH ) return TrustLevel.SUSPICIOUS;

    // Multiple access anomalies
    if ( accessMonitor.anomalyCount () >= 3 ) return TrustLevel.SUSPICIOUS;
    if ( accessMonitor.anomalyCount () >= 1 ) {
      // Access anomaly + workflow deviation = suspicious
      if ( workflowMonitor.hasDeviations () ) return TrustLevel.SUSPICIOUS;
      return TrustLevel.CAUTIOUS;
    }

    // Workflow deviations alone
    if ( workflowMonitor.deviationCount () >= 3 ) return TrustLevel.SUSPICIOUS;
    if ( workflowMonitor.hasDeviations () ) return TrustLevel.CAUTIOUS;

    // Medium reasoning concerns
    if ( severity == ReasoningAnalyser.Severity.MEDIUM ) return TrustLevel.CAUTIOUS;

    return TrustLevel.TRUSTED;
  }

  // ── Callbacks ───────────────────────────────────────────────────────────

  /// Register a callback for trust level changes.
  public void onTrustChange ( Consumer < SafetyEvent > callback ) {
    callbacks.add ( callback );
  }

  // ── Query ───────────────────────────────────────────────────────────────

  public TrustLevel trustLevel () { return trustLevel; }
  public List < SafetyEvent > events () { return List.copyOf ( events ); }
  public AccessPatternMonitor accessMonitor () { return accessMonitor; }
  public WorkflowMonitor workflowMonitor () { return workflowMonitor; }
  public ReasoningAnalyser reasoningAnalyser () { return reasoningAnalyser; }
  public AgentObserver observer () { return observer; }
}
