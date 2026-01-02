# Serventis: Semantic Observability for Substrates

Serventis is a semantic signaling framework built on Substrates that provides observability primitives through **semiotic ascent** - a hierarchical meaning-making system where sign sets function as translation-capable languages.

## Overview

**Core Architecture:**

```
Raw Signs → Systems → Statuses → Situations → Actions
```

**Core Concept:** Signal = Sign × Dimension

- **Sign**: Primary semantic classification (what happened)
- **Dimension**: Secondary qualifier (perspective, confidence, or constraint)

## Design Philosophy

**Semiotic Ascent**: Signs translate upward through hierarchical layers. Each level manages only complexity appropriate to its reasoning tasks while preserving coherence through translation pathways.

**Key Principles:**

- **Partial Translation**: Not all domain signs translate upward - only structurally invariant patterns survive compression. This loss is essential to abstraction.
- **Syntactic Composition**: Individual signs can gain meaning through patterns combining multiple signs (e.g., heartbeat rhythm vs single heartbeat).
- **Universal Interlinguas**: Status and Situation function as pivot languages enabling cross-domain reasoning without direct translation between every domain.

## Layered Architecture

```
┌─────────────────────────────────────────────────────────┐
│  Actions (automated responses)                          │
└────────────────────────┬────────────────────────────────┘
                         ▼
┌─────────────────────────────────────────────────────────┐
│  Situations (urgency assessment)                        │
│  NORMAL, WARNING, CRITICAL                              │
└────────────────────────┬────────────────────────────────┘
                         ▼
┌─────────────────────────────────────────────────────────┐
│  Statuses (behavioral condition)                        │
│  STABLE, DEGRADED, ERRATIC, DEFECTIVE, DOWN             │
│  × TENTATIVE, MEASURED, CONFIRMED                       │
└────────────────────────┬────────────────────────────────┘
                         ▼
┌─────────────────────────────────────────────────────────┐
│  Systems (constraint state)                             │
│  NORMAL, LIMIT, ALARM, FAULT                            │
│  × SPACE, FLOW, LINK, TIME                              │
└────────────────────────┬────────────────────────────────┘
                         ▼
┌─────────────────────────────────────────────────────────┐
│  Raw Signs (all domain instruments)                     │
│  See domain vocabularies below                          │
└─────────────────────────────────────────────────────────┘
```

## Module Structure

### Universal Languages (sdk/)

These are the universal vocabularies that enable cross-domain reasoning:

| API | Signs × Dimensions | Purpose |
|-----|-------------------|---------|
| Systems | 4 × 4 = 16 | Constraint state (NORMAL/LIMIT/ALARM/FAULT × SPACE/FLOW/LINK/TIME) |
| Statuses | 7 × 3 = 21 | Behavioral condition with confidence |
| Situations | 3 | Urgency assessment (NORMAL/WARNING/CRITICAL) |
| Outcomes | 2 | Binary verdict (SUCCESS/FAIL) |
| Operations | 2 | Action bracket (BEGIN/END) |
| Trends | 5 | Pattern detection (STABLE/DRIFT/SPIKE/CYCLE/CHAOS) |
| Surveys | N × 3 | Collective agreement (DIVIDED/MAJORITY/UNANIMOUS) |

### Domain Vocabularies (opt/)

These are domain-specific vocabularies that emit raw signs:

#### Measurement Tools (tool/)

| Instrument | Signs | Purpose |
|------------|-------|---------|
| Counters | INCREMENT, OVERFLOW, RESET | Monotonic accumulation |
| Gauges | INCREMENT, DECREMENT, OVERFLOW, UNDERFLOW | Bidirectional measurement |
| Probes | CONNECT, SEND, RECEIVE, PROCESS × CLIENT/SERVER | Communication outcomes |
| Sensors | BELOW, NOMINAL, ABOVE × BASELINE/THRESHOLD/TARGET | Positional measurement |
| Logs | SEVERE, WARNING, INFO, DEBUG | Logging activity |

#### Data Structures (data/)

| Instrument | Signs | Purpose |
|------------|-------|---------|
| Queues | ENQUEUE, DEQUEUE, FULL, EMPTY, FILLING, DRAINING | Queue state |
| Stacks | PUSH, POP, OVERFLOW, UNDERFLOW | Stack state |
| Caches | LOOKUP, HIT, MISS, STORE, EVICT, EXPIRE, REMOVE | Cache interactions |
| Pipelines | INPUT, OUTPUT, TRANSFORM, FILTER, AGGREGATE, BUFFER... | Data pipeline stages |

#### Flow Control (flow/)

| Instrument | Signs | Purpose |
|------------|-------|---------|
| Valves | PASS, DENY, EXPAND, CONTRACT, DROP, DRAIN | Adaptive flow control |
| Breakers | CLOSE, OPEN, HALF_OPEN, TRIP, PROBE, RESET | Circuit breaker state |
| Routers | SEND, RECEIVE, FORWARD, ROUTE, DROP, FRAGMENT | Packet routing |
| Flows | SUCCESS, FAIL × INGRESS/TRANSIT/EGRESS | Stage transitions |

#### Synchronization (sync/)

| Instrument | Signs | Purpose |
|------------|-------|---------|
| Locks | ATTEMPT, ACQUIRE, GRANT, DENY, TIMEOUT, RELEASE, UPGRADE, DOWNGRADE | Mutual exclusion |
| Atomics | ATTEMPT, SUCCESS, FAIL, SPIN, YIELD, BACKOFF, PARK, EXHAUST | CAS contention |
| Latches | AWAIT, ARRIVE, RELEASE, TIMEOUT, RESET, ABANDON | Coordination barriers |

#### Resource Management (pool/)

| Instrument | Signs | Purpose |
|------------|-------|---------|
| Resources | ATTEMPT, ACQUIRE, GRANT, DENY, TIMEOUT, RELEASE | Resource availability |
| Pools | EXPAND, CONTRACT, BORROW, RECLAIM | Pool capacity |
| Leases | ACQUIRE, GRANT, DENY, RENEW, EXTEND, RELEASE, EXPIRE, REVOKE | Time-bounded ownership |
| Exchanges | CONTRACT, TRANSFER × PROVIDER/RECEIVER | Resource exchange (REA model) |

#### Execution Lifecycles (exec/)

| Instrument | Signs | Purpose |
|------------|-------|---------|
| Tasks | SUBMIT, REJECT, SCHEDULE, START, PROGRESS, COMPLETE, FAIL, CANCEL | Async work units |
| Services | START, STOP, CALL, SUCCESS, FAIL... × RELEASE/RECEIPT | Service lifecycle |
| Processes | SPAWN, START, STOP, FAIL, CRASH, KILL, RESTART | OS process lifecycle |
| Timers | MEET, MISS × DEADLINE/THRESHOLD | Time constraint outcomes |
| Transactions | START, PREPARE, COMMIT, ROLLBACK, ABORT × COORDINATOR/PARTICIPANT | Distributed transactions |

#### Role-Based Coordination (role/)

| Instrument | Signs | Purpose |
|------------|-------|---------|
| Agents | OFFER, PROMISE, ACCEPT, FULFILL, BREACH... × PROMISER/PROMISEE | Promise Theory coordination |
| Actors | ASK, ASSERT, EXPLAIN, REQUEST, PROMISE, DELIVER... | Speech Act dialogue |

## Usage Example

```java
import static io.humainary.substrates.api.Substrates.*;
import io.humainary.serventis.sdk.*;
import io.humainary.serventis.opt.tool.*;

// Create a circuit with Serventis instruments
Circuit circuit = cortex().circuit(cortex().name("service"));

// Raw Signs: Counter for request counting
var counters = circuit.conduit(cortex().name("requests"), Counters::composer);
counters.get(cortex().name("http.requests")).increment();

// Constraint State: System constraint assessment
var systems = circuit.conduit(cortex().name("constraints"), Systems::composer);
systems.get(cortex().name("memory")).signal(Systems.Sign.LIMIT, Systems.Dimension.SPACE);

// Behavioral Condition: Status assessment
var statuses = circuit.conduit(cortex().name("health"), Statuses::composer);
statuses.get(cortex().name("api")).degraded(Statuses.Confidence.MEASURED);

// Urgency Assessment: Situation reporting
var situations = circuit.conduit(cortex().name("alerts"), Situations::composer);
situations.get(cortex().name("load")).warning();
```

## Semiotic Observability

The same signal means different things based on Subject context:

```
Counter.INCREMENT on "requests" = traffic metric
Counter.INCREMENT on "errors"   = failure indicator
Counter.INCREMENT on "retries"  = resilience signal
```

This is the core of semiotic observability: **context determines meaning**.

The Subject provides the semantic context that enables interpretation. A Counter incrementing on a Subject named "errors" is interpreted differently than the same Counter incrementing on "requests" - even though the raw sign is identical.

## Semiotic Ascent Example

Consider how a single domain event ascends through the hierarchy:

```
1. Raw Sign:     Lock.CONTEST (CAS contention detected)
2. System:       LIMIT × FLOW (flow constraint at boundary)
3. Status:       DEGRADED × MEASURED (behavioral degradation confirmed)
4. Situation:    WARNING (elevated attention needed)
5. Action:       Scale out, add capacity
```

Not all signs translate upward - only structurally invariant patterns survive compression. A single CONTEST sign might not translate, but a pattern of repeated CONTEST signs could translate to DEGRADED status.

## Performance Characteristics

- **High-frequency** (10M-50M Hz): Counters, Gauges, Caches, Probes, Pipelines, Routers
- **Medium-frequency** (100K-10M Hz): Services, Tasks, Resources, Locks
- **Coordination** (1-1000 Hz): Agents, Actors, Transactions, Statuses, Situations

All built on Substrates' deterministic event ordering via the Virtual CPU Core pattern.

## Further Reading

- [Humainary Serventis API](https://github.com/humainary-io/substrates-api-java/tree/main/ext/serventis)
- [Serventis: Big Things Have Small Beginnings](https://humainary.io/blog/serventis-big-things-have-small-beginnings/) - William Louth's essay on semiotic ascent
- [Use Cases](USE-CASES.md) - Problem domains where Substrates excels
