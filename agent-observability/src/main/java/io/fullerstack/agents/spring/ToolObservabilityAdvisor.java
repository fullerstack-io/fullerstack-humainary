package io.fullerstack.agents.spring;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.model.ChatResponse;

import io.fullerstack.agents.AgentObserver;

/// Instruments tool call execution within the Spring AI advisor chain.
///
/// Runs after the ToolCallAdvisor has executed tool calls. Inspects
/// the response for tool call results and emits corresponding
/// Serventis signals for each tool invocation.
///
/// ## Usage
///
/// ```java
/// var chatClient = ChatClient.builder(chatModel)
///     .defaultAdvisors(
///         new ObservabilityAdvisor(observer),           // outermost
///         new ToolCallAdvisor(),                         // Spring AI tool execution
///         new ToolObservabilityAdvisor(observer)          // tool-specific signals
///     )
///     .build();
/// ```
public final class ToolObservabilityAdvisor implements CallAdvisor {

  private final AgentObserver observer;

  public ToolObservabilityAdvisor ( AgentObserver observer ) {
    this.observer = observer;
  }

  @Override
  public String getName () {
    return "ToolObservabilityAdvisor";
  }

  @Override
  public int getOrder () {
    return 100; // After tool execution advisors
  }

  @Override
  public ChatClientResponse adviseCall (
    ChatClientRequest request,
    CallAdvisorChain chain
  ) {
    ChatClientResponse response = chain.nextCall ( request );

    // Inspect response for tool calls
    try {
      var chatResponse = response.chatResponse ();
      if ( chatResponse != null ) {
        inspectToolCalls ( chatResponse );
      }
    } catch ( Exception ignored ) {
      // Don't break the chain if inspection fails
    }

    return response;
  }

  private void inspectToolCalls ( ChatResponse chatResponse ) {
    var results = chatResponse.getResults ();
    if ( results == null ) return;

    for ( var result : results ) {
      var output = result.getOutput ();
      if ( output == null ) continue;

      var toolCalls = output.getToolCalls ();
      if ( toolCalls == null || toolCalls.isEmpty () ) continue;

      for ( var toolCall : toolCalls ) {
        String toolName = toolCall.name ();
        if ( toolName != null ) {
          // Tool was called — emit signal
          // Note: at this point the tool has already executed
          // (ToolCallAdvisor ran before us). We're recording the fact.
          observer.toolCallStarted ( toolName );
          observer.toolCallSucceeded ( toolName, 0 ); // latency not available here
        }
      }
    }
  }
}
