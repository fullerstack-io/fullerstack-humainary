package io.fullerstack.agents;

import static org.assertj.core.api.Assertions.assertThat;

import io.fullerstack.agents.safety.SafetyMonitor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// Tests for audit trail and Prometheus metrics.
final class AuditAndMetricsTest {

  private AgentObserver observer;

  @BeforeEach
  void setUp () {
    observer = AgentObserver.create ( "audit-test" );
  }

  @AfterEach
  void tearDown () {
    observer.close ();
  }

  @Nested
  @DisplayName ( "Audit Trail" )
  class AuditTrailTests {

    @Test
    @DisplayName ( "records events with timestamps" )
    void recordEvents () {
      var audit = new AuditTrail ( "test-agent" );
      audit.record ( "LLM_CALL", "model=gpt-4o", "TRUSTED" );
      audit.record ( "TOOL_CALL", "tool=web-search" );

      assertThat ( audit.size () ).isEqualTo ( 2 );
      assertThat ( audit.entries ().get ( 0 ).eventType () ).isEqualTo ( "LLM_CALL" );
      assertThat ( audit.entries ().get ( 0 ).trustLevel () ).isEqualTo ( "TRUSTED" );
      assertThat ( audit.entries ().get ( 1 ).trustLevel () ).isNull ();
    }

    @Test
    @DisplayName ( "filters by event type" )
    void filterByType () {
      var audit = new AuditTrail ( "test-agent" );
      audit.record ( "LLM_CALL", "call 1" );
      audit.record ( "TOOL_CALL", "tool 1" );
      audit.record ( "LLM_CALL", "call 2" );

      assertThat ( audit.entriesOfType ( "LLM_CALL" ) ).hasSize ( 2 );
      assertThat ( audit.entriesOfType ( "TOOL_CALL" ) ).hasSize ( 1 );
    }

    @Test
    @DisplayName ( "exports as readable text" )
    void export () {
      var audit = new AuditTrail ( "test-agent" );
      audit.record ( "LLM_CALL", "model=gpt-4o", "TRUSTED" );

      String exported = audit.export ();
      assertThat ( exported ).contains ( "AGENT AUDIT TRAIL: test-agent" );
      assertThat ( exported ).contains ( "LLM_CALL" );
      assertThat ( exported ).contains ( "trust=TRUSTED" );
    }

    @Test
    @DisplayName ( "recent returns last N entries" )
    void recent () {
      var audit = new AuditTrail ( "test-agent" );
      for ( int i = 0; i < 10; i++ ) {
        audit.record ( "EVENT", "entry " + i );
      }

      assertThat ( audit.recent ( 3 ) ).hasSize ( 3 );
      assertThat ( audit.recent ( 3 ).get ( 0 ).detail () ).isEqualTo ( "entry 7" );
    }
  }

  @Nested
  @DisplayName ( "Prometheus Metrics" )
  class MetricsTests {

    @Test
    @DisplayName ( "produces Prometheus exposition format" )
    void prometheusFormat () {
      observer.llmCallStarted ( "gpt-4o" );
      observer.llmCallSucceeded ( "gpt-4o", 500, 100, 50 );
      observer.toolCallStarted ( "search" );
      observer.toolCallFailed ( "search", "timeout" );

      var metrics = new AgentMetrics ( observer );
      String output = metrics.prometheusFormat ();

      assertThat ( output ).contains ( "agent_llm_calls_total" );
      assertThat ( output ).contains ( "agent_tool_calls_total" );
      assertThat ( output ).contains ( "agent_tokens_total" );
      assertThat ( output ).contains ( "agent_llm_success_rate" );
      assertThat ( output ).contains ( "audit-test" );
    }

    @Test
    @DisplayName ( "includes safety metrics when SafetyMonitor provided" )
    void safetyMetrics () {
      var safety = new SafetyMonitor ( observer );
      safety.accessMonitor ().setBaselineWindowSize ( 3 );
      for ( int i = 0; i < 3; i++ ) safety.recordToolAccess ( "normal",
        io.fullerstack.agents.safety.AccessPatternMonitor.AccessType.TOOL_CALL );
      safety.recordToolAccess ( "novel",
        io.fullerstack.agents.safety.AccessPatternMonitor.AccessType.TOOL_CALL );

      var metrics = new AgentMetrics ( observer, safety );
      String output = metrics.prometheusFormat ();

      assertThat ( output ).contains ( "agent_trust_level" );
      assertThat ( output ).contains ( "agent_access_anomalies_total" );
      assertThat ( output ).contains ( "agent_workflow_deviations_total" );
      assertThat ( output ).contains ( "agent_reasoning_concerns_total" );
    }
  }
}
