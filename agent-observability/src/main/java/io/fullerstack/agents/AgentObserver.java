package io.fullerstack.agents;

import static io.humainary.substrates.api.Substrates.cortex;

import java.util.function.Consumer;

import io.humainary.substrates.api.Substrates.Circuit;
import io.humainary.substrates.api.Substrates.Conduit;
import io.humainary.substrates.api.Substrates.Cortex;
import io.humainary.substrates.api.Substrates.Tap;
import io.humainary.serventis.opt.exec.Services;
import io.humainary.serventis.opt.exec.Timers;
import io.humainary.serventis.opt.pool.Resources;
import io.humainary.serventis.sdk.Outcomes;
import io.humainary.serventis.sdk.Situations;
import io.humainary.serventis.sdk.Statuses;

/// In-process semiotic observability for AI agent workflows.
///
/// Runs a Substrates circuit inside the application JVM. Every agent
/// action (LLM call, tool invocation, reasoning step) becomes a Serventis
/// sign emitted into the circuit. The semiotic ascent chain compresses
/// thousands of raw signs into health assessments:
///
/// ```
/// Agent Actions → Services (raw signs)
///              → Outcomes (SUCCESS/FAIL, lossy tap)
///              → Statuses (STABLE/DIVERGING/DEGRADED/DOWN, sliding window)
///              → Situations (NORMAL/WARNING/CRITICAL, diff)
/// ```
///
/// In steady state, the observer produces **zero output** — silence = healthy.
/// A single DIVERGING signal after hours of silence means the agent's
/// behaviour pattern has changed. That's the signal worth acting on.
///
/// ## Usage
///
/// ```java
/// var observer = AgentObserver.create("my-agent");
///
/// // In Spring AI advisor:
/// observer.llmCallStarted("gpt-4o");
/// // ... LLM processes ...
/// observer.llmCallSucceeded("gpt-4o", latencyMs, inputTokens, outputTokens);
///
/// // Health check:
/// observer.onSituationChange(signal -> {
///     if (signal.sign() == Situations.Sign.CRITICAL) {
///         alertOps("Agent health critical: " + signal);
///     }
/// });
/// ```
public final class AgentObserver implements AutoCloseable {

  private final Cortex cortex;
  private final String agentId;

  /// Agent activity circuit — all agent actions processed here.
  private final Circuit agentCircuit;

  /// Health assessment circuit — low-frequency assessments.
  private final Circuit healthCircuit;

  // Domain conduits (agent circuit)
  private final Conduit < Services.Service, Services.Signal > llmCalls;
  private final Conduit < Services.Service, Services.Signal > toolCalls;
  private final Conduit < Timers.Timer, Timers.Signal > latency;
  private final Conduit < Resources.Resource, Resources.Sign > tokens;

  // Assessment conduits (health circuit)
  private final Conduit < Statuses.Status, Statuses.Signal > statuses;
  private final Conduit < Situations.Situation, Situations.Signal > situations;

  // Semiotic ascent taps
  private final Tap < Outcomes.Sign > llmOutcomes;
  private final Tap < Outcomes.Sign > toolOutcomes;

  // Metrics
  private int totalLlmCalls;
  private int totalLlmFailures;
  private int totalToolCalls;
  private int totalToolFailures;
  private long totalInputTokens;
  private long totalOutputTokens;

  /// Create an observer for an agent.
  public static AgentObserver create ( String agentId ) {
    return new AgentObserver ( agentId );
  }

  private AgentObserver ( String agentId ) {
    this.agentId = agentId;
    this.cortex = cortex ();

    // ── Circuits ──────────────────────────────────────────────────────────
    agentCircuit = cortex.circuit ( cortex.name ( "agent." + agentId ) );
    healthCircuit = cortex.circuit ( cortex.name ( "agent." + agentId + ".health" ) );

    // ── Domain conduits ──────────────────────────────────────────────────
    llmCalls = agentCircuit.conduit ( cortex.name ( "agent.llm" ), Services::composer );
    toolCalls = agentCircuit.conduit ( cortex.name ( "agent.tools" ), Services::composer );
    latency = agentCircuit.conduit ( cortex.name ( "agent.latency" ), Timers::composer );
    tokens = agentCircuit.conduit ( cortex.name ( "agent.tokens" ), Resources::composer );

    // ── Lossy taps (Services → Outcomes) ─────────────────────────────────
    llmOutcomes = llmCalls.tap ( signal -> switch ( signal.sign () ) {
      case Services.Sign.SUCCESS -> Outcomes.Sign.SUCCESS;
      case Services.Sign.FAIL -> Outcomes.Sign.FAIL;
      default -> null;
    } );

    toolOutcomes = toolCalls.tap ( signal -> switch ( signal.sign () ) {
      case Services.Sign.SUCCESS -> Outcomes.Sign.SUCCESS;
      case Services.Sign.FAIL -> Outcomes.Sign.FAIL;
      default -> null;
    } );

    // ── Assessment conduits ──────────────────────────────────────────────
    statuses = healthCircuit.conduit (
      cortex.name ( "agent.status" ), Statuses::composer,
      flow -> flow.diff ()
    );

    situations = healthCircuit.conduit (
      cortex.name ( "agent.situation" ), Situations::composer,
      flow -> flow.diff ()
    );

    // ── Wire health assessor ─────────────────────────────────────────────
    var assessor = new AgentHealthAssessor ( statuses );
    llmOutcomes.subscribe ( agentCircuit.subscriber (
      cortex.name ( "agent.health.llm" ), assessor
    ) );
    toolOutcomes.subscribe ( agentCircuit.subscriber (
      cortex.name ( "agent.health.tools" ), assessor
    ) );

    // ── Wire situation assessor ──────────────────────────────────────────
    statuses.subscribe ( healthCircuit.subscriber (
      cortex.name ( "agent.situation.assessor" ),
      ( subject, registrar ) -> registrar.register ( signal -> {
        Situations.Sign urgency = switch ( signal.sign () ) {
          case Statuses.Sign.STABLE, Statuses.Sign.CONVERGING -> Situations.Sign.NORMAL;
          case Statuses.Sign.DIVERGING, Statuses.Sign.ERRATIC,
               Statuses.Sign.DEGRADED -> Situations.Sign.WARNING;
          case Statuses.Sign.DEFECTIVE, Statuses.Sign.DOWN -> Situations.Sign.CRITICAL;
          default -> Situations.Sign.NORMAL;
        };
        situations.percept ( subject.name () )
          .signal ( urgency, Situations.Dimension.CONSTANT );
      } )
    ) );
  }

  // ── Signal Emission API (called by advisors/interceptors) ────────────────

  /// LLM call started.
  public void llmCallStarted ( String model ) {
    llmCalls.percept ( cortex.name ( "llm." + model ) )
      .start ( Services.Dimension.CALLER );
    totalLlmCalls++;
  }

  /// LLM call completed successfully.
  public void llmCallSucceeded ( String model, long latencyMs, long inputTokens, long outputTokens ) {
    llmCalls.percept ( cortex.name ( "llm." + model ) )
      .success ( Services.Dimension.CALLER );
    latency.percept ( cortex.name ( "llm." + model ) )
      .signal ( latencyMs <= 2000 ? Timers.Sign.MEET : Timers.Sign.MISS,
        Timers.Dimension.THRESHOLD );

    totalInputTokens += inputTokens;
    totalOutputTokens += outputTokens;
  }

  /// LLM call failed.
  public void llmCallFailed ( String model, String error ) {
    llmCalls.percept ( cortex.name ( "llm." + model ) )
      .fail ( Services.Dimension.CALLER );
    totalLlmFailures++;
  }

  /// LLM call timed out.
  public void llmCallTimeout ( String model ) {
    llmCalls.percept ( cortex.name ( "llm." + model ) )
      .signal ( Services.Sign.EXPIRE, Services.Dimension.CALLER );
    latency.percept ( cortex.name ( "llm." + model ) )
      .miss ( Timers.Dimension.THRESHOLD );
    totalLlmFailures++;
  }

  /// Tool invocation started.
  public void toolCallStarted ( String toolName ) {
    toolCalls.percept ( cortex.name ( "tool." + toolName ) )
      .start ( Services.Dimension.CALLEE );
    totalToolCalls++;
  }

  /// Tool invocation completed.
  public void toolCallSucceeded ( String toolName, long latencyMs ) {
    toolCalls.percept ( cortex.name ( "tool." + toolName ) )
      .success ( Services.Dimension.CALLEE );
    latency.percept ( cortex.name ( "tool." + toolName ) )
      .signal ( latencyMs <= 500 ? Timers.Sign.MEET : Timers.Sign.MISS,
        Timers.Dimension.THRESHOLD );
  }

  /// Tool invocation failed.
  public void toolCallFailed ( String toolName, String error ) {
    toolCalls.percept ( cortex.name ( "tool." + toolName ) )
      .fail ( Services.Dimension.CALLEE );
    totalToolFailures++;
  }

  // ── Situation Callbacks ─────────────────────────────────────────────────

  /// Register a callback for situation changes (NORMAL → WARNING → CRITICAL).
  /// Called on the health circuit thread — keep callbacks fast.
  public void onSituationChange ( Consumer < Situations.Signal > callback ) {
    situations.subscribe ( healthCircuit.subscriber (
      cortex.name ( "agent.situation.callback" ),
      ( subject, registrar ) -> registrar.register ( callback::accept )
    ) );
  }

  /// Register a callback for status changes (STABLE → DIVERGING → DEGRADED).
  public void onStatusChange ( Consumer < Statuses.Signal > callback ) {
    statuses.subscribe ( healthCircuit.subscriber (
      cortex.name ( "agent.status.callback" ),
      ( subject, registrar ) -> registrar.register ( callback::accept )
    ) );
  }

  // ── Query API ───────────────────────────────────────────────────────────

  public String agentId () { return agentId; }
  public int totalLlmCalls () { return totalLlmCalls; }
  public int totalLlmFailures () { return totalLlmFailures; }
  public int totalToolCalls () { return totalToolCalls; }
  public int totalToolFailures () { return totalToolFailures; }
  public long totalInputTokens () { return totalInputTokens; }
  public long totalOutputTokens () { return totalOutputTokens; }

  public double llmSuccessRate () {
    return totalLlmCalls == 0 ? 1.0 : (double) ( totalLlmCalls - totalLlmFailures ) / totalLlmCalls;
  }

  public double toolSuccessRate () {
    return totalToolCalls == 0 ? 1.0 : (double) ( totalToolCalls - totalToolFailures ) / totalToolCalls;
  }

  /// Await all pending signals to be processed.
  public void await () {
    agentCircuit.await ();
    healthCircuit.await ();
  }

  // Package-private for testing
  Circuit agentCircuit () { return agentCircuit; }
  Circuit healthCircuit () { return healthCircuit; }
  Conduit < Statuses.Status, Statuses.Signal > statuses () { return statuses; }
  Conduit < Situations.Situation, Situations.Signal > situations () { return situations; }

  @Override
  public void close () {
    healthCircuit.close ();
    agentCircuit.close ();
  }
}
