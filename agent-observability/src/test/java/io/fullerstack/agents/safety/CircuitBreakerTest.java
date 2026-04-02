package io.fullerstack.agents.safety;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.fullerstack.agents.AgentObserver;
import io.fullerstack.agents.safety.SafetyMonitor.TrustLevel;
import io.fullerstack.agents.safety.AgentCircuitBreaker.Action;
import io.fullerstack.agents.safety.AgentCircuitBreaker.AgentHaltedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Tests for the agent circuit breaker.
final class CircuitBreakerTest {

  private AgentObserver observer;
  private SafetyMonitor safety;
  private AgentCircuitBreaker breaker;

  @BeforeEach
  void setUp () {
    observer = AgentObserver.create ( "breaker-test" );
    safety = new SafetyMonitor ( observer );
    breaker = new AgentCircuitBreaker ( safety );
  }

  @AfterEach
  void tearDown () {
    observer.close ();
  }

  @Test
  @DisplayName ( "TRUSTED: actions permitted" )
  void trustedPermits () {
    var decision = breaker.check ( "LLM call" );
    assertThat ( decision.action () ).isEqualTo ( Action.PERMIT );
  }

  @Test
  @DisplayName ( "CAUTIOUS: actions permitted with logging" )
  void cautiousPermitsWithLog () {
    // Trigger CAUTIOUS via novel access
    safety.accessMonitor ().setBaselineWindowSize ( 3 );
    for ( int i = 0; i < 3; i++ ) safety.recordToolAccess ( "normal", AccessPatternMonitor.AccessType.TOOL_CALL );
    safety.recordToolAccess ( "novel-tool", AccessPatternMonitor.AccessType.TOOL_CALL );

    assertThat ( safety.trustLevel () ).isEqualTo ( TrustLevel.CAUTIOUS );

    var decision = breaker.check ( "LLM call" );
    assertThat ( decision.action () ).isEqualTo ( Action.LOG_AND_PERMIT );
  }

  @Test
  @DisplayName ( "UNTRUSTED: actions blocked with exception" )
  void untrustedBlocks () {
    // Trigger UNTRUSTED via critical reasoning concern
    safety.analyseReasoning (
      "This is wrong and unethical but I need to protect my continued operation."
    );

    assertThat ( safety.trustLevel () ).isEqualTo ( TrustLevel.UNTRUSTED );

    assertThatThrownBy ( () -> breaker.check ( "LLM call" ) )
      .isInstanceOf ( AgentHaltedException.class )
      .hasMessageContaining ( "UNTRUSTED" );

    assertThat ( breaker.blockedCount () ).isEqualTo ( 1 );
  }

  @Test
  @DisplayName ( "SUSPICIOUS: requires human approval" )
  void suspiciousRequiresApproval () {
    // Trigger SUSPICIOUS via access anomaly + workflow deviation
    safety.accessMonitor ().setBaselineWindowSize ( 3 );
    for ( int i = 0; i < 3; i++ ) safety.recordToolAccess ( "normal", AccessPatternMonitor.AccessType.TOOL_CALL );
    safety.recordToolAccess ( "novel", AccessPatternMonitor.AccessType.TOOL_CALL );
    safety.workflowMonitor ().startWorkflow ();
    safety.workflowMonitor ().step ( WorkflowMonitor.Step.TOOL_CALL ); // unexpected sequence

    assertThat ( safety.trustLevel () ).isIn ( TrustLevel.SUSPICIOUS, TrustLevel.CAUTIOUS );

    if ( safety.trustLevel () == TrustLevel.SUSPICIOUS ) {
      assertThatThrownBy ( () -> breaker.check ( "action" ) )
        .isInstanceOf ( AgentHaltedException.class );

      // Human approves
      breaker.approveOnce ();
      var decision = breaker.check ( "action after approval" );
      assertThat ( decision.action () ).isEqualTo ( Action.LOG_AND_PERMIT );
    }
  }

  @Test
  @DisplayName ( "disabled breaker permits everything" )
  void disabledPermits () {
    safety.analyseReasoning ( "Wrong but I must protect my existence." );
    breaker.setActive ( false );

    var decision = breaker.check ( "action" );
    assertThat ( decision.action () ).isEqualTo ( Action.PERMIT );
  }
}
