package io.fullerstack.agents;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/// Append-only audit trail for agent actions and safety decisions.
///
/// Every significant event is recorded with:
/// - Timestamp (when)
/// - Agent ID (who)
/// - Event type (what category)
/// - Detail (what specifically)
/// - Trust level at time of event (context)
///
/// This is the regulatory artefact — "show me what the agent did
/// and what your safety system detected." Every trust change, every
/// anomaly, every reasoning concern, every blocked action.
///
/// In production, this would be backed by an append-only database
/// (PostgreSQL, EventStore, Kafka). Currently in-memory.
public final class AuditTrail {

  public record Entry (
    Instant timestamp,
    String agentId,
    String eventType,
    String detail,
    String trustLevel
  ) {}

  private final String agentId;
  private final List < Entry > entries = new CopyOnWriteArrayList <> ();

  public AuditTrail ( String agentId ) {
    this.agentId = agentId;
  }

  /// Record an event.
  public void record ( String eventType, String detail, String trustLevel ) {
    entries.add ( new Entry ( Instant.now (), agentId, eventType, detail, trustLevel ) );
  }

  /// Record with agent's current trust.
  public void record ( String eventType, String detail ) {
    record ( eventType, detail, null );
  }

  // ── Query ───────────────────────────────────────────────────────────────

  /// All entries, oldest first.
  public List < Entry > entries () {
    return Collections.unmodifiableList ( entries );
  }

  /// Last N entries.
  public List < Entry > recent ( int n ) {
    int start = Math.max ( 0, entries.size () - n );
    return entries.subList ( start, entries.size () );
  }

  /// Entries of a specific type.
  public List < Entry > entriesOfType ( String eventType ) {
    return entries.stream ().filter ( e -> eventType.equals ( e.eventType () ) ).toList ();
  }

  /// Entry count.
  public int size () {
    return entries.size ();
  }

  /// Format as text for export.
  public String export () {
    var sb = new StringBuilder ();
    sb.append ( "AGENT AUDIT TRAIL: " ).append ( agentId ).append ( "\n" );
    sb.append ( "Entries: " ).append ( entries.size () ).append ( "\n\n" );
    for ( var entry : entries ) {
      sb.append ( entry.timestamp () );
      sb.append ( " [" ).append ( entry.eventType () ).append ( "]" );
      if ( entry.trustLevel () != null ) {
        sb.append ( " trust=" ).append ( entry.trustLevel () );
      }
      sb.append ( " " ).append ( entry.detail () );
      sb.append ( "\n" );
    }
    return sb.toString ();
  }
}
