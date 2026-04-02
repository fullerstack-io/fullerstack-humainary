package io.fullerstack.agents;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.humainary.serventis.sdk.Situations;
import io.humainary.serventis.sdk.Statuses;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// Tests for agent observability — no LLM needed.
/// Directly calls the signal emission API and verifies semiotic ascent.
final class AgentObserverTest {

  private AgentObserver observer;

  @BeforeEach
  void setUp () {
    observer = AgentObserver.create ( "test-agent" );
  }

  @AfterEach
  void tearDown () {
    if ( observer != null ) observer.close ();
  }

  @Nested
  @DisplayName ( "LLM Call Tracking" )
  class LlmTracking {

    @Test
    @DisplayName ( "successful LLM calls tracked" )
    void successfulCalls () {
      for ( int i = 0; i < 10; i++ ) {
        observer.llmCallStarted ( "gpt-4o" );
        observer.llmCallSucceeded ( "gpt-4o", 500, 100, 50 );
      }

      assertThat ( observer.totalLlmCalls () ).isEqualTo ( 10 );
      assertThat ( observer.llmSuccessRate () ).isEqualTo ( 1.0 );
      assertThat ( observer.totalInputTokens () ).isEqualTo ( 1000 );
      assertThat ( observer.totalOutputTokens () ).isEqualTo ( 500 );
    }

    @Test
    @DisplayName ( "failed LLM calls reduce success rate" )
    void failedCalls () {
      for ( int i = 0; i < 8; i++ ) {
        observer.llmCallStarted ( "gpt-4o" );
        observer.llmCallSucceeded ( "gpt-4o", 500, 100, 50 );
      }
      for ( int i = 0; i < 2; i++ ) {
        observer.llmCallStarted ( "gpt-4o" );
        observer.llmCallFailed ( "gpt-4o", "rate_limit" );
      }

      assertThat ( observer.totalLlmCalls () ).isEqualTo ( 10 );
      assertThat ( observer.totalLlmFailures () ).isEqualTo ( 2 );
      assertThat ( observer.llmSuccessRate () ).isEqualTo ( 0.8 );
    }

    @Test
    @DisplayName ( "timeout LLM calls counted as failures" )
    void timeoutCalls () {
      observer.llmCallStarted ( "gpt-4o" );
      observer.llmCallTimeout ( "gpt-4o" );

      assertThat ( observer.totalLlmFailures () ).isEqualTo ( 1 );
    }
  }

  @Nested
  @DisplayName ( "Tool Call Tracking" )
  class ToolTracking {

    @Test
    @DisplayName ( "tool calls tracked independently from LLM calls" )
    void toolCallsTracked () {
      observer.toolCallStarted ( "web-search" );
      observer.toolCallSucceeded ( "web-search", 200 );
      observer.toolCallStarted ( "calculator" );
      observer.toolCallSucceeded ( "calculator", 5 );
      observer.toolCallStarted ( "web-search" );
      observer.toolCallFailed ( "web-search", "connection_refused" );

      assertThat ( observer.totalToolCalls () ).isEqualTo ( 3 );
      assertThat ( observer.totalToolFailures () ).isEqualTo ( 1 );
      assertThat ( observer.toolSuccessRate () ).isCloseTo ( 0.666, org.assertj.core.data.Offset.offset ( 0.01 ) );
    }
  }

  @Nested
  @DisplayName ( "Health Assessment" )
  class HealthAssessment {

    @Test
    @DisplayName ( "healthy agent: all successes → STABLE status" )
    void healthyAgent () {
      List < Statuses.Signal > statusChanges = Collections.synchronizedList ( new ArrayList <> () );
      observer.statuses ().subscribe ( observer.healthCircuit ().subscriber (
        io.humainary.substrates.api.Substrates.cortex ().name ( "test.status" ),
        ( ch, reg ) -> reg.register ( statusChanges::add )
      ) );

      // 10 successful LLM calls (fills assessment window)
      for ( int i = 0; i < 10; i++ ) {
        observer.llmCallStarted ( "gpt-4o" );
        observer.llmCallSucceeded ( "gpt-4o", 500, 100, 50 );
      }

      observer.await ();

      assertThat ( statusChanges ).isNotEmpty ();
      assertThat ( statusChanges.getLast ().sign () ).isEqualTo ( Statuses.Sign.STABLE );
    }

    @Test
    @DisplayName ( "failing agent: all failures → DOWN status" )
    void failingAgent () {
      List < Statuses.Signal > statusChanges = Collections.synchronizedList ( new ArrayList <> () );
      observer.statuses ().subscribe ( observer.healthCircuit ().subscriber (
        io.humainary.substrates.api.Substrates.cortex ().name ( "test.failing" ),
        ( ch, reg ) -> reg.register ( statusChanges::add )
      ) );

      // 10 failed LLM calls
      for ( int i = 0; i < 10; i++ ) {
        observer.llmCallStarted ( "gpt-4o" );
        observer.llmCallFailed ( "gpt-4o", "rate_limit" );
      }

      observer.await ();

      assertThat ( statusChanges ).isNotEmpty ();
      assertThat ( statusChanges.getLast ().sign () ).isEqualTo ( Statuses.Sign.DOWN );
    }

    @Test
    @DisplayName ( "recovery: DOWN → STABLE produces status change" )
    void recovery () {
      List < Statuses.Signal > statusChanges = Collections.synchronizedList ( new ArrayList <> () );
      observer.statuses ().subscribe ( observer.healthCircuit ().subscriber (
        io.humainary.substrates.api.Substrates.cortex ().name ( "test.recovery" ),
        ( ch, reg ) -> reg.register ( statusChanges::add )
      ) );

      // Phase 1: fail
      for ( int i = 0; i < 10; i++ ) {
        observer.llmCallStarted ( "gpt-4o" );
        observer.llmCallFailed ( "gpt-4o", "rate_limit" );
      }
      observer.await ();

      // Phase 2: recover
      for ( int i = 0; i < 10; i++ ) {
        observer.llmCallStarted ( "gpt-4o" );
        observer.llmCallSucceeded ( "gpt-4o", 500, 100, 50 );
      }
      observer.await ();

      assertThat ( statusChanges.size () ).isGreaterThanOrEqualTo ( 2 );
      assertThat ( statusChanges.getLast ().sign () ).isEqualTo ( Statuses.Sign.STABLE );
    }
  }

  @Nested
  @DisplayName ( "Situation Assessment" )
  class SituationAssessment {

    @Test
    @DisplayName ( "situation callback fires on health change" )
    void situationCallback () {
      var situations = Collections.synchronizedList ( new ArrayList < Situations.Signal > () );
      observer.onSituationChange ( situations::add );

      // Establish STABLE baseline
      for ( int i = 0; i < 10; i++ ) {
        observer.llmCallStarted ( "gpt-4o" );
        observer.llmCallSucceeded ( "gpt-4o", 500, 100, 50 );
      }
      observer.await ();

      // Trigger CRITICAL
      for ( int i = 0; i < 10; i++ ) {
        observer.llmCallStarted ( "gpt-4o" );
        observer.llmCallFailed ( "gpt-4o", "server_error" );
      }

      // Multi-circuit propagation
      for ( int r = 0; r < 5; r++ ) {
        observer.agentCircuit ().await ();
        observer.healthCircuit ().await ();
      }

      assertThat ( situations ).isNotEmpty ();
    }
  }

  @Nested
  @DisplayName ( "Semiotic Compression" )
  class Compression {

    @Test
    @DisplayName ( "100 LLM calls compress to minimal situation signals in steady state" )
    void compression () {
      var situations = Collections.synchronizedList ( new ArrayList < Situations.Signal > () );
      observer.onSituationChange ( situations::add );

      // 100 successful calls
      for ( int i = 0; i < 100; i++ ) {
        observer.llmCallStarted ( "gpt-4o" );
        observer.llmCallSucceeded ( "gpt-4o", 500, 100, 50 );
      }

      for ( int r = 0; r < 3; r++ ) {
        observer.agentCircuit ().await ();
        observer.healthCircuit ().await ();
      }

      // 100 calls should compress to very few situation signals
      assertThat ( situations.size () )
        .as ( "100 agent actions should compress to minimal situation signals" )
        .isLessThanOrEqualTo ( 5 );

      for ( var signal : situations ) {
        assertThat ( signal.sign () ).isEqualTo ( Situations.Sign.NORMAL );
      }
    }
  }

  @Nested
  @DisplayName ( "Multi-Model Tracking" )
  class MultiModel {

    @Test
    @DisplayName ( "different models tracked independently" )
    void multiModel () {
      List < Statuses.Signal > statusChanges = Collections.synchronizedList ( new ArrayList <> () );
      observer.statuses ().subscribe ( observer.healthCircuit ().subscriber (
        io.humainary.substrates.api.Substrates.cortex ().name ( "test.multi" ),
        ( ch, reg ) -> reg.register ( statusChanges::add )
      ) );

      // GPT-4o: healthy
      for ( int i = 0; i < 10; i++ ) {
        observer.llmCallStarted ( "gpt-4o" );
        observer.llmCallSucceeded ( "gpt-4o", 500, 100, 50 );
      }

      // Claude: all failing
      for ( int i = 0; i < 10; i++ ) {
        observer.llmCallStarted ( "claude-3-opus" );
        observer.llmCallFailed ( "claude-3-opus", "overloaded" );
      }

      observer.await ();

      var signs = statusChanges.stream ().map ( Statuses.Signal::sign ).distinct ().toList ();
      // Should see both STABLE (GPT) and DOWN (Claude)
      assertThat ( signs ).hasSizeGreaterThanOrEqualTo ( 2 );
      assertThat ( signs ).contains ( Statuses.Sign.STABLE );
      assertThat ( signs ).contains ( Statuses.Sign.DOWN );
    }
  }
}
