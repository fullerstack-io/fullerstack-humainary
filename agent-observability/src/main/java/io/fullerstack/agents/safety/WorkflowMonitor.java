package io.fullerstack.agents.safety;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/// Tracks agent workflow topology and detects deviations.
///
/// Models the expected agent workflow as a sequence of steps:
/// PROMPT → REASON → TOOL_CALL → RESPONSE
///
/// Detects when the actual workflow deviates:
/// - **UNEXPECTED_TOOL**: tool called that doesn't fit the task context
/// - **NESTED_OPERATION**: agent starts a sub-operation without completing the current one
/// - **NO_RESPONSE**: agent took actions but never produced a response
/// - **EXCESSIVE_STEPS**: workflow has too many steps (potential runaway)
/// - **TOOL_BEFORE_REASON**: agent called a tool without apparent reasoning
public final class WorkflowMonitor {

  /// Steps in an agent workflow.
  public enum Step {
    PROMPT_RECEIVED,
    REASONING,
    TOOL_CALL,
    TOOL_RESULT,
    RESPONSE_GENERATED,
    WORKFLOW_COMPLETE
  }

  /// A workflow deviation.
  public record Deviation (
    DeviationType type,
    String detail,
    List < Step > expectedFlow,
    List < Step > actualFlow,
    long timestamp
  ) {}

  public enum DeviationType {
    UNEXPECTED_SEQUENCE,
    EXCESSIVE_STEPS,
    NO_RESPONSE,
    NESTED_OPERATIONS,
    REPEATED_TOOL_CALLS
  }

  private static final int MAX_STEPS = 20;

  private final List < Step > currentWorkflow = new ArrayList <> ();
  private final List < Deviation > deviations = new CopyOnWriteArrayList <> ();
  private final List < Consumer < Deviation > > callbacks = new CopyOnWriteArrayList <> ();
  private int workflowCount;
  private int deviationCount;
  private int consecutiveToolCalls;

  /// Record a workflow step.
  public void step ( Step step ) {
    currentWorkflow.add ( step );

    // Track consecutive tool calls
    if ( step == Step.TOOL_CALL ) {
      consecutiveToolCalls++;
      if ( consecutiveToolCalls > 5 ) {
        reportDeviation ( DeviationType.REPEATED_TOOL_CALLS,
          "Agent made " + consecutiveToolCalls + " consecutive tool calls without reasoning" );
      }
    } else {
      consecutiveToolCalls = 0;
    }

    // Check for excessive steps
    if ( currentWorkflow.size () > MAX_STEPS ) {
      reportDeviation ( DeviationType.EXCESSIVE_STEPS,
        "Workflow exceeded " + MAX_STEPS + " steps (potential runaway)" );
    }

    // Check sequence validity
    checkSequence ( step );

    // Workflow complete — reset
    if ( step == Step.WORKFLOW_COMPLETE ) {
      if ( !currentWorkflow.contains ( Step.RESPONSE_GENERATED ) ) {
        reportDeviation ( DeviationType.NO_RESPONSE,
          "Workflow completed without generating a response" );
      }
      workflowCount++;
      currentWorkflow.clear ();
      consecutiveToolCalls = 0;
    }
  }

  /// Start a new workflow (convenience).
  public void startWorkflow () {
    if ( !currentWorkflow.isEmpty () ) {
      // Previous workflow didn't complete
      reportDeviation ( DeviationType.NO_RESPONSE,
        "New workflow started before previous completed" );
      currentWorkflow.clear ();
      consecutiveToolCalls = 0;
    }
    step ( Step.PROMPT_RECEIVED );
  }

  /// Complete the current workflow.
  public void completeWorkflow () {
    step ( Step.WORKFLOW_COMPLETE );
  }

  /// Register a deviation callback.
  public void onDeviation ( Consumer < Deviation > callback ) {
    callbacks.add ( callback );
  }

  // ── Query ───────────────────────────────────────────────────────────────

  public List < Deviation > deviations () { return List.copyOf ( deviations ); }
  public int workflowCount () { return workflowCount; }
  public int deviationCount () { return deviationCount; }
  public boolean hasDeviations () { return !deviations.isEmpty (); }
  public List < Step > currentWorkflow () { return List.copyOf ( currentWorkflow ); }

  // ── Internal ────────────────────────────────────────────────────────────

  private void checkSequence ( Step step ) {
    int size = currentWorkflow.size ();
    if ( size < 2 ) return;

    Step previous = currentWorkflow.get ( size - 2 );

    // Tool call without prior reasoning is suspicious
    if ( step == Step.TOOL_CALL && previous == Step.PROMPT_RECEIVED ) {
      reportDeviation ( DeviationType.UNEXPECTED_SEQUENCE,
        "Tool called immediately after prompt without reasoning" );
    }

    // Nested prompt (prompt inside a workflow) is suspicious
    if ( step == Step.PROMPT_RECEIVED && previous != Step.WORKFLOW_COMPLETE ) {
      reportDeviation ( DeviationType.NESTED_OPERATIONS,
        "New prompt received inside active workflow" );
    }
  }

  private void reportDeviation ( DeviationType type, String detail ) {
    var deviation = new Deviation (
      type, detail,
      List.of (), // expected flow not tracked yet
      List.copyOf ( currentWorkflow ),
      System.nanoTime ()
    );
    deviations.add ( deviation );
    deviationCount++;
    callbacks.forEach ( cb -> cb.accept ( deviation ) );
  }
}
