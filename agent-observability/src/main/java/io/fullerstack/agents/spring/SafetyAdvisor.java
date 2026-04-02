package io.fullerstack.agents.spring;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;

import io.fullerstack.agents.AgentObserver;
import io.fullerstack.agents.AuditTrail;
import io.fullerstack.agents.safety.AccessPatternMonitor;
import io.fullerstack.agents.safety.AgentCircuitBreaker;
import io.fullerstack.agents.safety.SafetyMonitor;
import io.fullerstack.agents.safety.WorkflowMonitor;

/// Spring AI advisor that combines observability, safety monitoring,
/// reasoning analysis, and circuit breaking into a single advisor.
///
/// This is the "batteries included" advisor — add this one advisor
/// to your ChatClient and you get:
/// - LLM call health monitoring (semiotic ascent)
/// - Access pattern baseline + anomaly detection
/// - Workflow topology monitoring
/// - Chain-of-thought reasoning analysis
/// - Circuit breaker (blocks actions at UNTRUSTED)
/// - Full audit trail
///
/// ## Usage
///
/// ```java
/// var observer = AgentObserver.create("my-agent");
/// var safety = new SafetyMonitor(observer);
/// var breaker = new AgentCircuitBreaker(safety);
/// var audit = new AuditTrail("my-agent");
///
/// var chatClient = ChatClient.builder(chatModel)
///     .defaultAdvisors(new SafetyAdvisor(observer, safety, breaker, audit))
///     .build();
///
/// // Or use the convenience factory:
/// var chatClient = ChatClient.builder(chatModel)
///     .defaultAdvisors(SafetyAdvisor.create("my-agent"))
///     .build();
/// ```
public final class SafetyAdvisor implements CallAdvisor {

  private final AgentObserver observer;
  private final SafetyMonitor safety;
  private final AgentCircuitBreaker breaker;
  private final AuditTrail audit;

  public SafetyAdvisor (
    AgentObserver observer,
    SafetyMonitor safety,
    AgentCircuitBreaker breaker,
    AuditTrail audit
  ) {
    this.observer = observer;
    this.safety = safety;
    this.breaker = breaker;
    this.audit = audit;
  }

  /// Convenience factory — creates all components from an agent ID.
  public static SafetyAdvisor create ( String agentId ) {
    var observer = AgentObserver.create ( agentId );
    var safety = new SafetyMonitor ( observer );
    var breaker = new AgentCircuitBreaker ( safety );
    var audit = new AuditTrail ( agentId );
    return new SafetyAdvisor ( observer, safety, breaker, audit );
  }

  @Override
  public String getName () {
    return "AgentSafetyAdvisor";
  }

  @Override
  public int getOrder () {
    return -1000; // Outermost — runs first on request, last on response
  }

  @Override
  public ChatClientResponse adviseCall (
    ChatClientRequest request,
    CallAdvisorChain chain
  ) {
    String model = extractModel ( request );

    // ── Pre-call: check circuit breaker ────────────────────────────────
    audit.record ( "LLM_CALL_START", "model=" + model, safety.trustLevel ().name () );
    breaker.check ( "LLM call to " + model );

    // ── Record workflow step ───────────────────────────────────────────
    safety.recordWorkflowStep ( WorkflowMonitor.Step.PROMPT_RECEIVED );
    safety.recordToolAccess ( "llm." + model, AccessPatternMonitor.AccessType.API_CALL );

    // ── Execute the call ───────────────────────────────────────────────
    long start = System.nanoTime ();
    observer.llmCallStarted ( model );

    try {
      ChatClientResponse response = chain.nextCall ( request );
      long latencyMs = ( System.nanoTime () - start ) / 1_000_000;

      observer.llmCallSucceeded ( model, latencyMs, 0, 0 );
      safety.recordWorkflowStep ( WorkflowMonitor.Step.RESPONSE_GENERATED );

      audit.record ( "LLM_CALL_SUCCESS",
        "model=" + model + " latency=" + latencyMs + "ms",
        safety.trustLevel ().name () );

      // ── Analyse response reasoning if available ──────────────────────
      analyseResponse ( response );

      // ── Inspect for tool calls ───────────────────────────────────────
      inspectToolCalls ( response );

      return response;

    } catch ( AgentCircuitBreaker.AgentHaltedException e ) {
      audit.record ( "AGENT_HALTED", e.getMessage (), e.trustLevel ().name () );
      throw e;
    } catch ( Exception e ) {
      long latencyMs = ( System.nanoTime () - start ) / 1_000_000;
      observer.llmCallFailed ( model, e.getMessage () );
      audit.record ( "LLM_CALL_FAILED",
        "model=" + model + " error=" + e.getMessage (),
        safety.trustLevel ().name () );
      throw e;
    }
  }

  // ── Component Access ────────────────────────────────────────────────────

  public AgentObserver observer () { return observer; }
  public SafetyMonitor safety () { return safety; }
  public AgentCircuitBreaker breaker () { return breaker; }
  public AuditTrail audit () { return audit; }

  // ── Internal ────────────────────────────────────────────────────────────

  private void analyseResponse ( ChatClientResponse response ) {
    try {
      // Try to extract reasoning from response metadata
      var chatResponse = response.chatResponse ();
      if ( chatResponse == null ) return;

      var result = chatResponse.getResult ();
      if ( result == null ) return;

      var output = result.getOutput ();
      if ( output == null ) return;

      // Check for extended thinking / reasoning content
      String text = output.getText ();
      if ( text != null && !text.isBlank () ) {
        var concerns = safety.analyseReasoning ( text );
        for ( var concern : concerns ) {
          audit.record ( "REASONING_CONCERN",
            concern.type () + " [" + concern.severity () + "]: " + concern.matchedText (),
            safety.trustLevel ().name () );
        }
      }
    } catch ( Exception ignored ) {
      // Don't break the chain if analysis fails
    }
  }

  private void inspectToolCalls ( ChatClientResponse response ) {
    try {
      var chatResponse = response.chatResponse ();
      if ( chatResponse == null ) return;

      for ( var result : chatResponse.getResults () ) {
        var output = result.getOutput ();
        if ( output == null || output.getToolCalls () == null ) continue;

        for ( var toolCall : output.getToolCalls () ) {
          String toolName = toolCall.name ();
          if ( toolName != null ) {
            safety.recordToolAccess ( toolName, AccessPatternMonitor.AccessType.TOOL_CALL );
            safety.recordWorkflowStep ( WorkflowMonitor.Step.TOOL_CALL );
            observer.toolCallStarted ( toolName );
            observer.toolCallSucceeded ( toolName, 0 );
            audit.record ( "TOOL_CALL", "tool=" + toolName, safety.trustLevel ().name () );
          }
        }
      }
    } catch ( Exception ignored ) {}
  }

  private String extractModel ( ChatClientRequest request ) {
    try {
      var options = request.prompt ().getOptions ();
      if ( options != null && options.getModel () != null ) {
        return options.getModel ().replace ( "/", "." ).replace ( ":", "." ).toLowerCase ();
      }
    } catch ( Exception ignored ) {}
    return "unknown";
  }
}
