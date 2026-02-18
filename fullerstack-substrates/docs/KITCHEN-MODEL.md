# The Kitchen Model

A story-based mental model for understanding the Substrates Circuit architecture.

---

## The Restaurant

Imagine a restaurant with a kitchen. This kitchen has **exactly one chef** who works alone. No one else touches the stove, the pans, or the prep area. The chef processes orders one at a time, finishing each completely before moving to the next.

```
┌─────────────────────────────────────────────────────────────────────┐
│                                                                     │
│                          THE RESTAURANT                             │
│                                                                     │
│   ┌─────────────┐                              ┌─────────────┐     │
│   │             │                              │             │     │
│   │  DINING     │                              │   KITCHEN   │     │
│   │  ROOM       │         ORDER                │             │     │
│   │             │         WINDOW               │   One Chef  │     │
│   │  [Tables]   │ ─────────────────────────▶   │             │     │
│   │  [Guests]   │                              │   [Stove]   │     │
│   │  [Waiters]  │ ◀─────────────────────────   │   [Prep]    │     │
│   │             │         THE PASS             │             │     │
│   │             │    (finished plates)         │             │     │
│   └─────────────┘                              └─────────────┘     │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## The Kitchen

Inside the kitchen, the chef has a simple but disciplined workflow.

### The Two Queues

The chef has two places where work accumulates:

```
┌─────────────────────────────────────────────────────────────────┐
│                         KITCHEN                                  │
│                                                                  │
│   ┌──────────────────┐           ┌──────────────────┐           │
│   │   ORDER WINDOW   │           │    PREP LIST     │           │
│   │                  │           │                  │           │
│   │  Tickets from    │           │  Notes to self   │           │
│   │  the waiters     │           │  while cooking   │           │
│   │                  │           │                  │           │
│   │  [Steak Dinner]  │           │  [Drop fries]    │           │
│   │  [Fish & Chips]  │           │  [Heat sauce]    │           │
│   │  [Burger]        │           │  [Plate salad]   │           │
│   │                  │           │                  │           │
│   └────────┬─────────┘           └────────┬─────────┘           │
│            │                              │                      │
│            │         ┌────────────────────┘                      │
│            │         │                                           │
│            ▼         ▼                                           │
│         ┌───────────────┐                                        │
│         │               │                                        │
│         │     CHEF      │                                        │
│         │               │                                        │
│         └───────────────┘                                        │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

**ORDER WINDOW** - Where tickets from the dining room arrive. Waiters put orders here. The chef cannot control when orders arrive; guests order whenever they want.

**PREP LIST** - The chef's personal notepad. While working on a dish, the chef might realize "I need to drop fries for this plate" and adds it to the prep list. Only the chef writes here.

### The Chef's Algorithm

The chef follows a strict routine:

```
┌────────────────────────────────────────────────────────────────┐
│                                                                │
│   THE CHEF'S LOOP                                              │
│                                                                │
│   forever:                                                     │
│       1. Grab a ticket from ORDER WINDOW                       │
│       2. Start working on it                                   │
│          - This may add items to PREP LIST                     │
│       3. Work through ENTIRE PREP LIST until empty             │
│          - Each item may add more to PREP LIST                 │
│          - Keep going until nothing left                       │
│       4. Plate is complete - put on THE PASS                   │
│       5. Go back to step 1                                     │
│                                                                │
└────────────────────────────────────────────────────────────────┘
```

The critical rule: **Finish one order completely before starting the next.**

---

## The Steak Dinner

Let's follow a single order through the kitchen.

A guest orders a Steak Dinner. The waiter writes it up and puts the ticket in the ORDER WINDOW.

```
┌─────────────────────────────────────────┐
│         STEAK DINNER - Table 5          │
│                                         │
│  Main: Ribeye, medium-rare              │
│                                         │
│  Comes with:                            │
│    - Fries                              │
│    - Béarnaise sauce                    │
│    - Side salad                         │
│                                         │
└─────────────────────────────────────────┘
```

### Processing the Order

The chef grabs the ticket and begins:

```
Step 1: Chef reads "Steak Dinner"
        ────────────────────────────────────────
        Realizes this needs: fries, sauce, salad

        Adds to PREP LIST:
          [1] Drop fries
          [2] Heat béarnaise
          [3] Plate salad

        Starts seasoning the steak...


Step 2: Chef checks PREP LIST (not empty!)
        ────────────────────────────────────────
        Pulls [1] Drop fries

        Drops fries in oil
        Realizes: fries need ketchup portion

        Adds to PREP LIST:
          [2] Heat béarnaise
          [3] Plate salad
          [4] Portion ketchup    ← new!


Step 3: Chef checks PREP LIST (not empty!)
        ────────────────────────────────────────
        Pulls [2] Heat béarnaise

        Heats the sauce
        (No additional items needed)


Step 4: Chef checks PREP LIST (not empty!)
        ────────────────────────────────────────
        Pulls [3] Plate salad

        Plates the salad
        Realizes: salad needs dressing

        Adds to PREP LIST:
          [4] Portion ketchup
          [5] Add dressing      ← new!


Step 5: Chef checks PREP LIST (not empty!)
        ────────────────────────────────────────
        Pulls [4] Portion ketchup

        Puts ketchup in ramekin
        (No additional items needed)


Step 6: Chef checks PREP LIST (not empty!)
        ────────────────────────────────────────
        Pulls [5] Add dressing

        Drizzles dressing on salad
        (No additional items needed)


Step 7: Chef checks PREP LIST (empty!)
        ────────────────────────────────────────
        PREP LIST is empty
        Steak is done resting

        PLATE UP: Steak, fries, ketchup,
                  sauce, salad with dressing

        Put on THE PASS → Order complete!


Step 8: Back to ORDER WINDOW
        ────────────────────────────────────────
        Grab next ticket...
```

### Why the Prep List Matters

Why doesn't the chef just do everything inline?

**Bad approach (inline everything):**
```
Start steak
  └─▶ Need fries, start fries
        └─▶ Need ketchup, get ketchup
              └─▶ Ketchup bottle empty, get new bottle
                    └─▶ New bottle in storage, go to storage
                          └─▶ Storage locked, find keys
                                └─▶ ...

Chef is now holding a half-cooked steak, standing in
the storage room, looking for keys, having forgotten
what they were even doing.
```

The chef would be buried under a growing stack of half-finished tasks.

**Good approach (prep list):**
```
Start steak
  Add "fries" to prep list
  Add "sauce" to prep list
  Add "salad" to prep list
  Continue with steak...

Work prep list one item at a time.
Each item fully completed before next.
Never more than one thing in hand.
```

The prep list lets the chef stay organized. One thing at a time. Nothing held in mental "stack."

---

## The Pass and the Waiters

When an order is complete, it goes on **THE PASS** - the window between kitchen and dining room where finished plates wait.

```
┌─────────────────────────────────────────────────────────────────┐
│                                                                 │
│   KITCHEN                           DINING ROOM                 │
│                                                                 │
│   Chef finishes plate                                           │
│         │                                                       │
│         ▼                                                       │
│   ┌───────────────┐                                            │
│   │   THE PASS    │                                            │
│   │               │    "Steak Dinner, Table 5!"                │
│   │  [Plate]      │ ─────────────────────────────▶  Waiter     │
│   │               │                                  picks up   │
│   └───────────────┘                                            │
│                                                                 │
│   Waiters are SUBSCRIBED to the pass.                          │
│   When their table's order appears, they take it.              │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Waiters Subscribe

Before service begins, waiters sign up for their sections:

```
Waiter Alice: "I've got tables 1-5"
Waiter Bob:   "I've got tables 6-10"
Waiter Carol: "I've got tables 11-15"
```

This is **subscribing**. When a plate appears on the pass:
- The system checks which tables each waiter is responsible for
- The appropriate waiter is notified
- They pick up the plate and deliver it

In code terms:
- **The Pass** = Conduit (where emissions appear)
- **Waiter** = Subscriber (registered to receive emissions)
- **"I've got tables 1-5"** = `registrar.register(...)` (claiming responsibility)

---

## The Pipe: Order Slips

Now let's talk about how orders actually flow through the system. This is where **Pipes** come in.

### What is a Pipe?

A pipe is like an **order slip** - a piece of paper that carries information from one place to another.

```
┌──────────────────────────────────────────┐
│                                          │
│           ORDER SLIP (Pipe)              │
│                                          │
│  ┌────────────────────────────────────┐  │
│  │                                    │  │
│  │  Destination: Grill Station        │  │
│  │  Item: Ribeye steak, medium-rare   │  │
│  │                                    │  │
│  └────────────────────────────────────┘  │
│                                          │
│  This slip knows WHERE to go and         │
│  carries WHAT needs to be done.          │
│                                          │
└──────────────────────────────────────────┘
```

When you **emit** to a pipe, you're writing on the slip and putting it in the system.

### Where Does the Slip Go?

Here's the key insight: **It depends on WHO is writing the slip.**

```
┌─────────────────────────────────────────────────────────────────┐
│                                                                 │
│  SCENARIO A: Waiter writes a slip (external thread)            │
│  ─────────────────────────────────────────────────              │
│                                                                 │
│    Waiter writes "Steak Dinner"                                │
│         │                                                       │
│         ▼                                                       │
│    Goes to ORDER WINDOW (ingress queue)                        │
│         │                                                       │
│         ▼                                                       │
│    Chef picks it up when ready                                 │
│                                                                 │
│                                                                 │
│  SCENARIO B: Chef writes a slip (circuit thread)               │
│  ─────────────────────────────────────────────────              │
│                                                                 │
│    Chef realizes "need fries for this plate"                   │
│         │                                                       │
│         ▼                                                       │
│    Goes to PREP LIST (transit queue)                           │
│         │                                                       │
│         ▼                                                       │
│    Chef handles it as part of current order                    │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

**The same pipe, different routing based on who's writing:**

```java
// The pipe checks: "Am I the chef?"
public void emit(E emission) {
    if (Thread.currentThread() == worker) {
        // I'm the chef - use my PREP LIST
        valve.submitTransit(receiver, emission);
    } else {
        // I'm a waiter - use ORDER WINDOW
        valve.submitIngress(receiver, emission);
    }
}
```

This is why the same `pipe.emit()` call behaves differently:
- **Waiter calling** → Order Window (ingress)
- **Chef calling** → Prep List (transit)

### The Pipe's Journey

Let's trace a complete journey:

```
1. GUEST orders steak
   ──────────────────────────────────────────────────
   Guest tells waiter


2. WAITER writes order slip
   ──────────────────────────────────────────────────
   pipe.emit("Steak Dinner")

   Waiter is NOT the chef, so...
   Slip goes to ORDER WINDOW


3. CHEF grabs slip from ORDER WINDOW
   ──────────────────────────────────────────────────
   Reads: "Steak Dinner"
   Knows this needs fries, sauce, salad


4. CHEF writes three slips for components
   ──────────────────────────────────────────────────
   friesPipe.emit(order)    → PREP LIST
   saucePipe.emit(order)    → PREP LIST
   saladPipe.emit(order)    → PREP LIST

   Chef IS the chef, so...
   All go to PREP LIST


5. CHEF works through PREP LIST
   ──────────────────────────────────────────────────
   Each item processed in turn
   Each may add more to PREP LIST
   Continue until PREP LIST empty


6. ORDER COMPLETE
   ──────────────────────────────────────────────────
   Plate goes on THE PASS
   Subscribed waiter picks it up
   Delivers to guest
```

---

## Multiple Kitchens (Cross-Circuit)

Now imagine the restaurant has **two kitchens**:
- **Hot Kitchen** - steaks, fish, burgers (Chef Mario)
- **Pastry Kitchen** - desserts, bread (Chef Pierre)

```
┌─────────────────────────────────────────────────────────────────┐
│                                                                 │
│   HOT KITCHEN                      PASTRY KITCHEN               │
│   ───────────                      ──────────────               │
│                                                                 │
│   Chef: Mario                      Chef: Pierre                 │
│                                                                 │
│   ┌──────────────┐                 ┌──────────────┐            │
│   │ ORDER WINDOW │                 │ ORDER WINDOW │            │
│   │   (Mario's)  │                 │  (Pierre's)  │            │
│   └──────────────┘                 └──────────────┘            │
│                                                                 │
│   ┌──────────────┐                 ┌──────────────┐            │
│   │  PREP LIST   │                 │  PREP LIST   │            │
│   │  (Mario's)   │                 │  (Pierre's)  │            │
│   └──────────────┘                 └──────────────┘            │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### The Rule: Use Their Order Window

What if Mario needs bread from Pierre's kitchen?

**WRONG:**
```
Mario walks into Pastry Kitchen
Writes on Pierre's PREP LIST directly
Pierre doesn't know about it!

→ CHAOS! Pierre's workflow is disrupted.
```

**RIGHT:**
```
Mario writes a slip
Puts it in Pierre's ORDER WINDOW
Pierre picks it up when ready

→ Proper handoff through official channels.
```

### Cross-Kitchen in Code

```java
// Mario (Hot Kitchen) needs bread from Pierre (Pastry Kitchen)
pastryPipe.emit("Need bread for table 5");

// Inside pastryPipe.emit():
if (Thread.currentThread() == pierreWorker) {
    // Pierre calling - use his PREP LIST
    valve.submitTransit(...);
} else {
    // Someone else (Mario!) - use ORDER WINDOW
    valve.submitIngress(...);  // ← This happens!
}
```

Mario is not Pierre, so the request goes through Pierre's ORDER WINDOW (ingress queue), not his prep list. This ensures proper handoff between kitchens.

---

## The Complete Picture

```
┌─────────────────────────────────────────────────────────────────────┐
│                           THE RESTAURANT                            │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  DINING ROOM                                                        │
│  ────────────                                                       │
│  Guests sit at tables                                               │
│  Waiters take orders                                                │
│  Waiters SUBSCRIBE to receive finished plates                       │
│                                                                     │
│         │                                                           │
│         │ Orders go to ORDER WINDOW                                 │
│         ▼                                                           │
│                                                                     │
│  ┌────────────────────────────────────────────────────────────┐    │
│  │                        KITCHEN                              │    │
│  │                                                             │    │
│  │  ORDER WINDOW          PREP LIST                            │    │
│  │  (ingress)             (transit)                            │    │
│  │       │                    │                                │    │
│  │       └────────┬───────────┘                                │    │
│  │                │                                            │    │
│  │                ▼                                            │    │
│  │           ┌────────┐                                        │    │
│  │           │  CHEF  │  One chef, sequential processing      │    │
│  │           └────┬───┘                                        │    │
│  │                │                                            │    │
│  │                │  Algorithm:                                │    │
│  │                │  1. Grab from ORDER WINDOW                 │    │
│  │                │  2. Work it (may add to PREP LIST)         │    │
│  │                │  3. Drain PREP LIST completely             │    │
│  │                │  4. Plate complete → THE PASS              │    │
│  │                │  5. Repeat                                 │    │
│  │                │                                            │    │
│  │                ▼                                            │    │
│  │          ┌──────────┐                                       │    │
│  │          │ THE PASS │  Finished plates appear here         │    │
│  │          └────┬─────┘                                       │    │
│  │               │                                             │    │
│  └───────────────┼─────────────────────────────────────────────┘    │
│                  │                                                  │
│                  ▼                                                  │
│                                                                     │
│  Subscribed waiters pick up plates and deliver to tables           │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Flow Transformations (Cooking Instructions)

Not every order goes straight to cooking. Some tickets have **special instructions** printed right on them — rules the chef follows after picking up the ticket.

### Instructions on the Ticket

```
┌──────────────────────────────────────────┐
│                                          │
│        ORDER SLIP (Pipe with Flow)       │
│                                          │
│  ┌────────────────────────────────────┐  │
│  │                                    │  │
│  │  COOKING INSTRUCTIONS:             │  │
│  │                                    │  │
│  │  1. CHECK: Is it before 10pm?      │  │
│  │     → No? Discard this ticket.     │  │
│  │                                    │  │
│  │  2. CHECK: Under 50 ribeyes?       │  │
│  │     → No? Discard this ticket.     │  │
│  │                                    │  │
│  │  3. CHECK: Is this the 10th dish?  │  │
│  │     → Yes? Also send to QC.        │  │
│  │                                    │  │
│  │  4. COOK: Ribeye, medium-rare      │  │
│  │                                    │  │
│  └────────────────────────────────────┘  │
│                                          │
│  These instructions are compiled INTO    │
│  the ticket when the pipe is created.    │
│                                          │
└──────────────────────────────────────────┘
```

The key insight: flow rules are NOT a filter at the order window. They're **instructions baked into the ticket** that the chef follows after picking it up. The ticket always reaches the chef — the chef then decides what to do based on the instructions.

### How Instructions Are Followed

The chef picks up the ticket and follows the instructions step by step:

```
Chef picks up ticket from ORDER WINDOW
         │
         ▼
    Chef reads instruction 1:
    ┌─────────┐
    │  GUARD  │  "Is it before 10pm?"
    │         │   ├── YES → read next instruction
    └────┬────┘   └── NO  → discard, grab next ticket
         │
         ▼
    Chef reads instruction 2:
    ┌─────────┐
    │  LIMIT  │  "Have we served < 50 ribeyes?"
    │         │   ├── YES → read next instruction (count: 47 → 48)
    └────┬────┘   └── NO  → discard, grab next ticket
         │
         ▼
    Chef reads instruction 3:
    ┌─────────┐
    │ SAMPLE  │  "Is this the 10th dish?"
    │         │   ├── YES → also log for quality check
    └────┬────┘   └── NO  → continue
         │
         ▼
    Chef follows final instruction:
    COOK the ribeye medium-rare
```

### The Receiver Chain

These instructions form a **chain** that's compiled into the pipe's receiver when the pipe is created. Each instruction is a simple step that either:
- Passes to the next instruction, or
- Discards the ticket (filter), or
- Transforms it (replace, reduce)

```
┌──────────────────────────────────────────────────────────────────┐
│                                                                  │
│   THE TICKET'S LIFE                                              │
│                                                                  │
│                                                                  │
│   emit() ──▶ QUEUE ──▶ Chef dequeues ──▶ follows instructions   │
│                                              │                   │
│                                   ┌──────────┘                   │
│                                   │                              │
│                                   ▼                              │
│                         [Guard] ──▶ [Limit] ──▶ [Sample] ──▶    │
│                            │          │           │         cook()│
│                            │          │           │              │
│                         Direct     Direct      Direct            │
│                         calls      calls       calls             │
│                      (no queue) (no queue)  (no queue)           │
│                                                                  │
│                                                                  │
│   KEY INSIGHT: The flow chain IS the receiver. When the chef     │
│   dequeues the ticket and calls receiver.accept(), that call     │
│   IS the Guard check. The instructions run as part of the        │
│   chef's processing — they were never a separate filter.         │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

### In Code Terms

```java
// Creating a pipe with flow transformations
Pipe<Order> pipe = circuit.pipe(
    order -> cook(order),           // The actual work
    flow -> flow
        .guard(o -> isBeforeCutoff())    // Instruction 1
        .limit(50)                        // Instruction 2
        .sample(10)                       // Instruction 3
);

// What happens at pipe creation:
//   receiver = Guard( Limit( Sample( ReceptorReceiver(cook) ) ) )
//   The flow chain IS the pipe's receiver.
//
// When emit() is called:
// 1. Goes to ORDER WINDOW (ingress) or PREP LIST (transit)
// 2. Chef picks it up, calls receiver.accept()
// 3. receiver.accept() IS the Guard check
//    Guard passes → Limit passes → Sample passes → cook()
//    (all direct calls, no intermediate queueing)
```

---

## Chained Pipes (The Ticket Clip)

In a real kitchen, orders often pass through multiple stages. Think of a **ticket clip** - a rail where tickets move from station to station.

### The Ticket Rail

```
┌─────────────────────────────────────────────────────────────────┐
│                                                                 │
│                    THE TICKET RAIL                              │
│                                                                 │
│     ┌─────────┐      ┌─────────┐      ┌─────────┐              │
│     │  PREP   │ ───▶ │  GRILL  │ ───▶ │  PLATE  │ ───▶ PASS   │
│     │ STATION │      │ STATION │      │ STATION │              │
│     └─────────┘      └─────────┘      └─────────┘              │
│                                                                 │
│     Ticket arrives at PREP                                      │
│       → Prep work done, ticket slides to GRILL                 │
│         → Grill work done, ticket slides to PLATE              │
│           → Plating done, ticket goes to PASS                  │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

Each station does its work, then passes the ticket to the next station.

### In Code: Pipe Targeting Pipe

```java
// Final destination - the pass
Pipe<Dish> platePipe = circuit.pipe(dish -> sendToPass(dish));

// Grill wraps plate - after grilling, send to plating
Pipe<Order> grillPipe = circuit.pipe(order -> {
    Dish cooked = grillSteak(order);
    platePipe.emit(cooked);  // Slide ticket to next station
});

// Prep wraps grill - after prep, send to grill
Pipe<Order> prepPipe = circuit.pipe(order -> {
    Order prepped = prepIngredients(order);
    grillPipe.emit(prepped);  // Slide ticket to next station
});
```

When a waiter submits to `prepPipe`:

```
prepPipe.emit(order)
         │
         ▼
    ORDER WINDOW (waiter is external)
         │
         ▼
    Chef picks up ticket at PREP STATION
         │
         ▼
    Prep work: prepIngredients(order)
         │
         ▼
    grillPipe.emit(prepped)
         │
         ▼
    PREP LIST (chef is on circuit thread!)
         │
         ▼
    Chef picks up ticket at GRILL STATION
         │
         ▼
    Grill work: grillSteak(prepped)
         │
         ▼
    platePipe.emit(cooked)
         │
         ▼
    PREP LIST (still on circuit thread)
         │
         ▼
    Chef picks up ticket at PLATE STATION
         │
         ▼
    sendToPass(dish)  // Done!
```

### The Key Insight

Each `pipe.emit()` from the chef goes to the **PREP LIST**, not inline. This means:

```
PREP → emit to grill → PREP LIST
                          ↓
     GRILL → emit to plate → PREP LIST
                                ↓
          PLATE → sendToPass
```

The chef finishes one station, adds the next station's work to the prep list, then picks it up. **Sequential, never nested.**

### Receiver Extraction (Optimization)

When pipes are chained within the same circuit, the system can optimize by extracting the inner receiver:

```
WITHOUT OPTIMIZATION:
─────────────────────
    prep.emit() → PREP LIST → grill.emit() → PREP LIST → plate.emit() → PREP LIST

    Three queue operations!


WITH OPTIMIZATION (receiver extraction):
────────────────────────────────────────
    prep.emit() → PREP LIST → grillReceiver() → plateReceiver() → sendToPass()

    One queue operation! Rest are direct calls.
```

This happens when the system detects same-circuit pipes and extracts their receivers for direct calling. The rules (from Flow Transformations) work the same way - they're consumers chained together with direct calls.

### When Does This Matter?

**Chained pipes (multiple queues):**
- Each pipe.emit() adds to prep list
- More queue operations = more overhead
- But guarantees no stack overflow

**Extracted receivers (direct calls):**
- System detects same-circuit and optimizes
- Fewer queue operations = faster
- Same safety guarantees

The optimization is automatic - you write chained pipes for clarity, the system optimizes for performance.

---

## Cells (Kitchen Stations)

A **Cell** is like a hierarchical organization of stations within the kitchen. There's still ONE chef, but the kitchen is organized into stations that transform orders as they flow down and results as they flow up.

### The Station Hierarchy

```
┌─────────────────────────────────────────────────────────────────┐
│                         KITCHEN                                  │
│                                                                  │
│   ┌─────────────────────────────────────────────────────────┐   │
│   │                    EXPEDITOR STATION                     │   │
│   │                      (Parent Cell)                       │   │
│   │                                                          │   │
│   │   INGRESS: Receives "Table 5: Steak Dinner"             │   │
│   │            Transforms to: {table: 5, items: [...]}      │   │
│   │            Sends breakdown to sub-stations               │   │
│   │                                                          │   │
│   │   ┌─────────────┐  ┌─────────────┐  ┌─────────────┐     │   │
│   │   │   GRILL     │  │   FRY       │  │   COLD      │     │   │
│   │   │  STATION    │  │  STATION    │  │  STATION    │     │   │
│   │   │ (Child Cell)│  │ (Child Cell)│  │ (Child Cell)│     │   │
│   │   │             │  │             │  │             │     │   │
│   │   │ IN: meat    │  │ IN: potato  │  │ IN: greens  │     │   │
│   │   │     request │  │     request │  │     request │     │   │
│   │   │             │  │             │  │             │     │   │
│   │   │ OUT: cooked │  │ OUT: fried  │  │ OUT: plated │     │   │
│   │   │      steak  │  │      fries  │  │      salad  │     │   │
│   │   └──────┬──────┘  └──────┬──────┘  └──────┬──────┘     │   │
│   │          │                │                │             │   │
│   │          └────────────────┴────────────────┘             │   │
│   │                           │                              │   │
│   │   EGRESS: Collects all results                          │   │
│   │           Assembles into complete plate                  │   │
│   │           Sends to PASS                                  │   │
│   │                                                          │   │
│   └─────────────────────────────────────────────────────────┘   │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### The Flow: Down and Up

```
┌─────────────────────────────────────────────────────────────────┐
│                                                                 │
│   INPUT FLOWS DOWN (Ingress Transformation)                     │
│   ─────────────────────────────────────────                     │
│                                                                 │
│   "Steak Dinner"                                                │
│         │                                                       │
│         ▼                                                       │
│   [Expeditor Ingress] ── Breaks down order                      │
│         │                                                       │
│         ├──▶ Grill: "Cook ribeye medium-rare"                  │
│         ├──▶ Fry: "Make fries, extra crispy"                   │
│         └──▶ Cold: "House salad, ranch"                        │
│                                                                 │
│                                                                 │
│   OUTPUT FLOWS UP (Egress Transformation)                       │
│   ───────────────────────────────────────                       │
│                                                                 │
│   Grill done: {steak: cooked} ──┐                              │
│   Fry done: {fries: crispy} ────┼──▶ [Expeditor Egress]        │
│   Cold done: {salad: plated} ───┘           │                  │
│                                             ▼                   │
│                                    Assembles complete plate     │
│                                             │                   │
│                                             ▼                   │
│                                         THE PASS                │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Still One Chef

Even with stations, there's still ONE chef moving between them:

```
Chef's work order for "Steak Dinner":
──────────────────────────────────────

1. Expeditor: Break down order, add sub-tasks to PREP LIST
   PREP LIST: [grill-task, fry-task, cold-task]

2. Grill station: Cook steak
   PREP LIST: [fry-task, cold-task, grill-result]

3. Fry station: Make fries
   PREP LIST: [cold-task, grill-result, fry-result]

4. Cold station: Make salad
   PREP LIST: [grill-result, fry-result, cold-result]

5. Expeditor: Collect results, assemble plate
   PREP LIST: []

6. Put on PASS → Done!
```

The STATIONS organize the work, but the CHEF still processes everything sequentially via the PREP LIST.

### In Code Terms

```java
// Create a cell with ingress and egress transformations
Cell<OrderRequest, PlatedDish> kitchen = circuit.cell(
    cortex().name("kitchen"),

    // INGRESS: How orders flow DOWN into the cell
    (subject, outlet) -> circuit.pipe(
        order -> {
            // Break down and dispatch to sub-stations
            grillPipe.emit(order.meat());
            fryPipe.emit(order.sides());
            coldPipe.emit(order.salad());
        }
    ),

    // EGRESS: How results flow UP out of the cell
    (subject, outlet) -> circuit.pipe(
        result -> {
            // Collect and assemble
            plate.add(result);
            if (plate.isComplete()) {
                outlet.emit(plate.assemble());
            }
        }
    ),

    // RECEPTOR: Final destination
    dish -> sendToPass(dish)
);
```

### Nested Cells (Sub-Stations)

Cells can contain cells, creating deep hierarchies:

```
┌─────────────────────────────────────────────────────────────────┐
│   RESTAURANT (Root Cell)                                        │
│                                                                  │
│   ┌─────────────────────────────────────────────────────────┐   │
│   │   HOT KITCHEN (Child Cell)                               │   │
│   │                                                          │   │
│   │   ┌──────────────┐   ┌──────────────┐                   │   │
│   │   │ GRILL        │   │ SAUTÉ        │                   │   │
│   │   │ (Grandchild) │   │ (Grandchild) │                   │   │
│   │   └──────────────┘   └──────────────┘                   │   │
│   │                                                          │   │
│   └─────────────────────────────────────────────────────────┘   │
│                                                                  │
│   ┌─────────────────────────────────────────────────────────┐   │
│   │   COLD KITCHEN (Child Cell)                              │   │
│   │                                                          │   │
│   │   ┌──────────────┐   ┌──────────────┐                   │   │
│   │   │ SALADS       │   │ DESSERTS     │                   │   │
│   │   │ (Grandchild) │   │ (Grandchild) │                   │   │
│   │   └──────────────┘   └──────────────┘                   │   │
│   │                                                          │   │
│   └─────────────────────────────────────────────────────────┘   │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘

Orders flow DOWN through the hierarchy.
Results bubble UP through the hierarchy.
One chef processes everything via PREP LIST.
```

### Why Cells?

**Without cells:**
- Flat structure, all pipes at same level
- Hard to organize complex workflows
- No natural grouping of related work

**With cells:**
- Hierarchical organization
- Input/output transformations at each level
- Results automatically flow back up
- Matches how real kitchens are organized

---

## Mapping to Code

| Kitchen | Code | Description |
|---------|------|-------------|
| Restaurant | Application | The overall system |
| Kitchen | `Circuit` | Single-threaded processing unit |
| Chef | Worker thread | The one thread that processes all work |
| Order Window | Ingress queue | MPSC queue for external submissions |
| Prep List | Transit queue | Single-threaded queue for cascades |
| Order Slip | `Pipe` | Carries work to a destination |
| Writing a slip | `pipe.emit()` | Submits work to the system |
| Ticket receiver | `Receptor` / `Consumer` | The code that handles the work |
| The Pass | `Conduit` | Where processed emissions appear |
| Waiter | `Subscriber` | Registered to receive output |
| "I'll take tables 1-5" | `registrar.register()` | Claiming responsibility for emissions |
| Cooking instructions on ticket | `Flow` transforms | Guard, limit, sample, diff, etc. |
| Following instructions | Receiver chain | Direct calls, no queue between steps |
| Station | `Cell` | Hierarchical work organization |
| Expeditor | Parent cell | Breaks down orders, assembles results |
| Grill/Fry/Cold station | Child cell | Specialized sub-processing |
| Ingress transform | Cell ingress | How orders flow down into cell |
| Egress transform | Cell egress | How results flow up out of cell |
| Other Kitchen | Other `Circuit` | Must use their Order Window |
| Chef resting | Thread parked | `LockSupport.parkNanos()` |
| "ORDER IN!" | Thread unpark | `LockSupport.unpark()` |
| Complete one order | Atomic block | All cascades finish before next ingress item |

---

## Key Takeaways

1. **One Chef** - Each circuit has exactly one worker thread. No concurrency inside the kitchen.

2. **Two Queues** - Order Window (external) and Prep List (internal). Same pipe routes differently based on who's calling.

3. **Atomic Orders** - Each order from the Order Window is completed entirely (including all prep list items) before the next order starts.

4. **Cascades Use Prep List** - When processing triggers more work, it goes on the Prep List, not handled inline. This prevents stack overflow.

5. **Cross-Kitchen Protocol** - Other kitchens must use the Order Window, never the Prep List. This ensures thread safety.

6. **Waiters Subscribe** - External consumers register to receive finished work. They're notified when plates appear on The Pass.

7. **Instructions Run After Dequeuing** - Flow transformations (guard, limit, sample) are compiled into the pipe's receiver. They run as direct calls when the chef processes the ticket — not as a pre-filter before it reaches the chef.

8. **Receiver Extraction** - Same-circuit pipes have their receivers extracted for direct calls, avoiding unnecessary queue operations.

9. **Cells Organize Stations** - Cells provide hierarchical structure: orders flow down through ingress transforms, results bubble up through egress transforms. Still one chef, just organized work.

10. **Chef Rests When Idle** - Spin then park. Chef stands ready briefly, then sits down if no work arrives. Waiters only call out if chef is sitting.

---

## The Chef's Rest (Thread Wake-up)

The chef doesn't stand at attention 24/7. When there's no work, they rest. When new orders arrive, they wake up.

### The Chef's Idle Behavior

```
┌────────────────────────────────────────────────────────────────┐
│                                                                │
│   AFTER COMPLETING AN ORDER                                    │
│                                                                │
│   1. Check PREP LIST     → More work? Start immediately.      │
│   2. Check ORDER WINDOW  → More tickets? Start immediately.   │
│   3. Nothing?            → Stay alert for a moment...         │
│                                                                │
│   ┌──────────────────────────────────────────────────────┐    │
│   │                                                      │    │
│   │   STANDING READY (spinning)                          │    │
│   │   ─────────────────────────                          │    │
│   │   Chef stands at the pass, eyes on window.           │    │
│   │   Checks constantly for new tickets.                 │    │
│   │   "Any orders? No? Any orders? No? Any orders?..."   │    │
│   │                                                      │    │
│   │   After ~1000 checks with nothing...                 │    │
│   │                                                      │    │
│   └──────────────────────────────────────────────────────┘    │
│                          │                                     │
│                          ▼                                     │
│   ┌──────────────────────────────────────────────────────┐    │
│   │                                                      │    │
│   │   SITTING DOWN (parked)                              │    │
│   │   ─────────────────────                              │    │
│   │   Chef flips the "RESTING" sign, sits in break area. │    │
│   │   Eyes closed, not checking anything.                │    │
│   │   Fully asleep until explicitly woken.               │    │
│   │                                                      │    │
│   │   "Wake me when there's an order."                   │    │
│   │                                                      │    │
│   └──────────────────────────────────────────────────────┘    │
│                                                                │
└────────────────────────────────────────────────────────────────┘
```

Just two stages. The spin phase catches back-to-back orders with near-zero latency. Once orders stop, the chef sits down quickly to free up CPU.

### Waking the Chef

When a waiter puts a new ticket in the ORDER WINDOW, they glance at the "RESTING" sign:

```
┌─────────────────────────────────────────────────────────────────┐
│                                                                 │
│   WAITER ARRIVES WITH NEW ORDER                                 │
│                                                                 │
│   1. Put ticket in ORDER WINDOW                                 │
│                                                                 │
│   2. Glance at the "RESTING" sign                               │
│      │                                                          │
│      ├─── Sign says NOT resting:                                │
│      │    Chef is standing ready, will see the ticket.          │
│      │    Waiter walks away silently. No overhead.              │
│      │                                                          │
│      └─── Sign says RESTING:                                    │
│           Waiter flips sign to NOT resting,                     │
│           calls out: "ORDER IN!"                                │
│           Chef wakes up, goes to window.                        │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

The "RESTING" sign is cheap to check (an opaque read). Most of the time the chef is actively working, so the waiter just drops the ticket and walks away — no wake-up cost.

### In Code Terms

```java
// The chef's loop
private void workerLoop() {
    final IngressQueue q = ingress;

    for (;;) {
        // Drain transit first (cascades), then ingress (up to 64)
        boolean didWork = drainTransit();
        didWork |= q.drainBatch(this);

        if (didWork) continue;  // More work? Keep going.

        if (shouldExit) return; // Close marker was processed, exit.

        // No work — spin briefly checking for new tickets
        Object found = null;
        for (int i = 0; i < SPIN_COUNT && found == null; i++) {
            Thread.onSpinWait();
            found = q.peek();
        }

        if (found == null) {
            // Nothing arrived during spin — park (sit down)
            PARKED.setRelease(this, true);     // Flip "RESTING" sign

            if (q.peek() == null) {            // Double-check (avoid missed wake)
                LockSupport.park();            // Sleep until woken
            }

            PARKED.setOpaque(this, false);     // Clear sign on wake
        }
    }
}
```

```java
// Waiter submitting an order
void submitIngress(Consumer<Object> receiver, Object value) {
    ingress.add(receiver, value);              // Put ticket in window
    if ((boolean) PARKED.getOpaque(this)) {    // Glance at "RESTING" sign
        wakeWorker();                          // Only call out if sitting
    }
}

private void wakeWorker() {
    if (PARKED.compareAndSet(this, true, false)) {  // Flip sign
        LockSupport.unpark(worker);                 // "ORDER IN!"
    }
}
```

### Why This Matters

**Without rest:**
- Chef spins constantly checking for orders
- Burns CPU even when restaurant is empty
- Wasteful and inefficient

**With rest:**
- Chef conserves energy during quiet periods
- Wakes instantly when orders arrive
- Efficient use of resources

**The two stages:**

| Stage | Chef Behavior | Code | Latency | CPU |
|-------|--------------|------|---------|-----|
| Standing ready | Eyes on window | `onSpinWait()` ~1000 iterations | ~nanoseconds | High |
| Sitting down | Asleep, needs wake | `LockSupport.park()` | Wake latency | Near zero |

The system automatically finds the right balance:
- Busy restaurant → Chef stays standing (low latency, catches bursts)
- Quiet period → Chef sits down quickly (low CPU, explicit wake)

---

## Why This Design?

**Single-threaded simplicity:**
- No locks inside the kitchen
- No race conditions
- Predictable, deterministic processing

**Queue separation:**
- External world can't disrupt chef's workflow
- Chef can organize their own work
- Clean handoff at boundaries

**Atomic processing:**
- Complete visibility into order state
- No half-finished plates
- Easier debugging and reasoning

**Cascade safety:**
- Prep list prevents stack overflow
- Deep cascades become sequential steps
- Memory-safe even with millions of items

This is the Substrates Circuit architecture: simple, safe, and fast.
