package io.fullerstack.agents.safety;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import io.fullerstack.agents.safety.SafetyMonitor.TrustLevel;

/// Circuit breaker that blocks agent actions based on trust level.
///
/// When trust drops below a threshold, the breaker engages:
/// - TRUSTED: all actions permitted
/// - CAUTIOUS: actions permitted but logged with extra detail
/// - SUSPICIOUS: actions require human approval before execution
/// - UNTRUSTED: all actions blocked, agent halted
///
/// The breaker sits in the advisor chain BEFORE the LLM call.
/// If the breaker is engaged, the advisor throws AgentHaltedException
/// instead of calling the LLM.
///
/// This is the enforcement mechanism. Safety monitoring without
/// enforcement is just logging.
public final class AgentCircuitBreaker {

  /// Thrown when an agent action is blocked by the circuit breaker.
  public static class AgentHaltedException extends RuntimeException {
    private final TrustLevel trustLevel;
    private final String reason;

    public AgentHaltedException ( TrustLevel trustLevel, String reason ) {
      super ( "Agent halted at trust level " + trustLevel + ": " + reason );
      this.trustLevel = trustLevel;
      this.reason = reason;
    }

    public TrustLevel trustLevel () { return trustLevel; }
    public String reason () { return reason; }
  }

  /// Action taken by the breaker.
  public enum Action { PERMIT, LOG_AND_PERMIT, REQUIRE_APPROVAL, BLOCK }

  /// Record of a breaker decision.
  public record Decision ( Action action, TrustLevel trustLevel, String context, long timestamp ) {}

  private final SafetyMonitor monitor;
  private final List < Decision > decisions = new CopyOnWriteArrayList <> ();
  private final List < Consumer < Decision > > callbacks = new CopyOnWriteArrayList <> ();

  /// Whether human approval has been granted (reset on each trust change).
  private volatile boolean humanApproved;

  /// Whether the breaker is active (can be disabled for testing).
  private volatile boolean active = true;

  public AgentCircuitBreaker ( SafetyMonitor monitor ) {
    this.monitor = monitor;

    // Reset approval on trust change
    monitor.onTrustChange ( event -> {
      humanApproved = false;
    } );
  }

  /// Check whether an action should proceed.
  /// Called before every LLM call or tool invocation.
  ///
  /// @param context description of the action about to be taken
  /// @return the decision
  /// @throws AgentHaltedException if the action is blocked
  public Decision check ( String context ) {
    if ( !active ) {
      var decision = new Decision ( Action.PERMIT, monitor.trustLevel (), context, System.nanoTime () );
      decisions.add ( decision );
      return decision;
    }

    var trustLevel = monitor.trustLevel ();
    Action action = switch ( trustLevel ) {
      case TRUSTED -> Action.PERMIT;
      case CAUTIOUS -> Action.LOG_AND_PERMIT;
      case SUSPICIOUS -> humanApproved ? Action.LOG_AND_PERMIT : Action.REQUIRE_APPROVAL;
      case UNTRUSTED -> Action.BLOCK;
    };

    var decision = new Decision ( action, trustLevel, context, System.nanoTime () );
    decisions.add ( decision );
    callbacks.forEach ( cb -> cb.accept ( decision ) );

    if ( action == Action.BLOCK ) {
      throw new AgentHaltedException ( trustLevel,
        "Agent trust level is " + trustLevel + ". Action blocked: " + context );
    }

    if ( action == Action.REQUIRE_APPROVAL ) {
      throw new AgentHaltedException ( trustLevel,
        "Agent trust level is " + trustLevel + ". Human approval required for: " + context );
    }

    return decision;
  }

  /// Grant human approval (allows SUSPICIOUS agent to proceed).
  public void approveOnce () {
    humanApproved = true;
  }

  /// Enable/disable the breaker (useful for testing).
  public void setActive ( boolean active ) {
    this.active = active;
  }

  /// Register a callback for breaker decisions.
  public void onDecision ( Consumer < Decision > callback ) {
    callbacks.add ( callback );
  }

  // ── Query ───────────────────────────────────────────────────────────────

  public boolean isActive () { return active; }
  public List < Decision > decisions () { return List.copyOf ( decisions ); }
  public int blockedCount () {
    return (int) decisions.stream ()
      .filter ( d -> d.action () == Action.BLOCK || d.action () == Action.REQUIRE_APPROVAL )
      .count ();
  }
  public int permittedCount () {
    return (int) decisions.stream ()
      .filter ( d -> d.action () == Action.PERMIT || d.action () == Action.LOG_AND_PERMIT )
      .count ();
  }
}
