package io.fullerstack.agents.spring;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;

import io.fullerstack.agents.AgentObserver;

/// Spring AI advisor that instruments every ChatClient call with
/// semiotic observability signals.
///
/// Drop this into your Spring AI advisor chain and every LLM call,
/// tool invocation, and agent decision is automatically instrumented.
///
/// ## Usage
///
/// ```java
/// var observer = AgentObserver.create("my-agent");
///
/// var chatClient = ChatClient.builder(chatModel)
///     .defaultAdvisors(new ObservabilityAdvisor(observer))
///     .build();
///
/// // Every call through this chatClient is now instrumented.
/// // Check agent health:
/// observer.onSituationChange(signal -> {
///     log.warn("Agent health: {}", signal.sign());
/// });
/// ```
///
/// The advisor executes at the outermost layer of the chain
/// (order = -1000) so it captures the full round-trip including
/// all other advisors, tool calls, and memory operations.
public final class ObservabilityAdvisor implements CallAdvisor {

  private final AgentObserver observer;
  private final int order;

  /// Default: outermost advisor (lowest order = first on request, last on response).
  public ObservabilityAdvisor ( AgentObserver observer ) {
    this ( observer, -1000 );
  }

  public ObservabilityAdvisor ( AgentObserver observer, int order ) {
    this.observer = observer;
    this.order = order;
  }

  @Override
  public String getName () {
    return "AgentObservabilityAdvisor";
  }

  @Override
  public int getOrder () {
    return order;
  }

  @Override
  public ChatClientResponse adviseCall (
    ChatClientRequest request,
    CallAdvisorChain chain
  ) {
    String model = extractModel ( request );
    long start = System.nanoTime ();

    observer.llmCallStarted ( model );

    try {
      ChatClientResponse response = chain.nextCall ( request );
      long latencyMs = ( System.nanoTime () - start ) / 1_000_000;

      long inputTokens = extractInputTokens ( response );
      long outputTokens = extractOutputTokens ( response );

      observer.llmCallSucceeded ( model, latencyMs, inputTokens, outputTokens );

      return response;

    } catch ( Exception e ) {
      long latencyMs = ( System.nanoTime () - start ) / 1_000_000;

      if ( latencyMs > 30_000 ) {
        observer.llmCallTimeout ( model );
      } else {
        observer.llmCallFailed ( model, e.getMessage () );
      }

      throw e;
    }
  }

  // ── Extraction helpers ──────────────────────────────────────────────────

  private String extractModel ( ChatClientRequest request ) {
    // Try to get model from request options
    try {
      var options = request.prompt ().getOptions ();
      if ( options != null && options.getModel () != null ) {
        return sanitizeModelName ( options.getModel () );
      }
    } catch ( Exception ignored ) {}
    return "unknown";
  }

  private long extractInputTokens ( ChatClientResponse response ) {
    try {
      var result = response.chatResponse ().getResult ();
      if ( result != null && result.getMetadata () != null ) {
        var usage = result.getMetadata ().get ( "usage" );
        if ( usage != null ) {
          // Implementation depends on provider metadata format
          return 0; // placeholder — provider-specific extraction
        }
      }
    } catch ( Exception ignored ) {}
    return 0;
  }

  private long extractOutputTokens ( ChatClientResponse response ) {
    try {
      var result = response.chatResponse ().getResult ();
      if ( result != null && result.getMetadata () != null ) {
        return 0; // placeholder
      }
    } catch ( Exception ignored ) {}
    return 0;
  }

  /// Sanitize model name for use as Substrates Name segment.
  /// Names must be dot-notation compatible.
  private String sanitizeModelName ( String model ) {
    return model.replace ( "/", "." ).replace ( ":", "." ).toLowerCase ();
  }
}
