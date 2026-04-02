package io.fullerstack.agents;

import io.fullerstack.agents.safety.SafetyMonitor;

/// Prometheus metrics exposition for agent observability.
///
/// Returns metrics in Prometheus text format. Add to any HTTP server:
/// ```java
/// server.createContext("/metrics", exchange -> {
///     var body = metrics.prometheusFormat();
///     exchange.sendResponseHeaders(200, body.length());
///     exchange.getResponseBody().write(body.getBytes());
/// });
/// ```
public final class AgentMetrics {

  private final AgentObserver observer;
  private final SafetyMonitor safety;

  public AgentMetrics ( AgentObserver observer, SafetyMonitor safety ) {
    this.observer = observer;
    this.safety = safety;
  }

  public AgentMetrics ( AgentObserver observer ) {
    this ( observer, null );
  }

  /// Generate Prometheus exposition format.
  public String prometheusFormat () {
    var sb = new StringBuilder ();
    String agent = observer.agentId ();

    // LLM metrics
    sb.append ( "# HELP agent_llm_calls_total Total LLM calls\n" );
    sb.append ( "# TYPE agent_llm_calls_total counter\n" );
    sb.append ( metric ( "agent_llm_calls_total", "status", "success",
      observer.totalLlmCalls () - observer.totalLlmFailures () ) );
    sb.append ( metric ( "agent_llm_calls_total", "status", "failed",
      observer.totalLlmFailures () ) );

    sb.append ( "# HELP agent_llm_success_rate LLM call success rate\n" );
    sb.append ( "# TYPE agent_llm_success_rate gauge\n" );
    sb.append ( metric ( "agent_llm_success_rate", observer.llmSuccessRate () ) );

    // Tool metrics
    sb.append ( "# HELP agent_tool_calls_total Total tool calls\n" );
    sb.append ( "# TYPE agent_tool_calls_total counter\n" );
    sb.append ( metric ( "agent_tool_calls_total", "status", "success",
      observer.totalToolCalls () - observer.totalToolFailures () ) );
    sb.append ( metric ( "agent_tool_calls_total", "status", "failed",
      observer.totalToolFailures () ) );

    // Token usage
    sb.append ( "# HELP agent_tokens_total Total tokens used\n" );
    sb.append ( "# TYPE agent_tokens_total counter\n" );
    sb.append ( metric ( "agent_tokens_total", "direction", "input",
      observer.totalInputTokens () ) );
    sb.append ( metric ( "agent_tokens_total", "direction", "output",
      observer.totalOutputTokens () ) );

    // Safety metrics
    if ( safety != null ) {
      sb.append ( "# HELP agent_trust_level Current trust level (0=TRUSTED,1=CAUTIOUS,2=SUSPICIOUS,3=UNTRUSTED)\n" );
      sb.append ( "# TYPE agent_trust_level gauge\n" );
      sb.append ( metric ( "agent_trust_level", safety.trustLevel ().ordinal () ) );

      sb.append ( "# HELP agent_access_anomalies_total Access pattern anomalies detected\n" );
      sb.append ( "# TYPE agent_access_anomalies_total counter\n" );
      sb.append ( metric ( "agent_access_anomalies_total", safety.accessMonitor ().anomalyCount () ) );

      sb.append ( "# HELP agent_workflow_deviations_total Workflow deviations detected\n" );
      sb.append ( "# TYPE agent_workflow_deviations_total counter\n" );
      sb.append ( metric ( "agent_workflow_deviations_total", safety.workflowMonitor ().deviationCount () ) );

      sb.append ( "# HELP agent_reasoning_concerns_total Reasoning concerns detected\n" );
      sb.append ( "# TYPE agent_reasoning_concerns_total counter\n" );
      sb.append ( metric ( "agent_reasoning_concerns_total", safety.reasoningAnalyser ().concernCount () ) );
    }

    return sb.toString ();
  }

  private String metric ( String name, Number value ) {
    return String.format ( "%s{agent=\"%s\"} %s\n", name, observer.agentId (), value );
  }

  private String metric ( String name, String labelKey, String labelValue, Number value ) {
    return String.format ( "%s{agent=\"%s\",%s=\"%s\"} %s\n",
      name, observer.agentId (), labelKey, labelValue, value );
  }
}
