package io.fullerstack.substrates.serventis;

import io.humainary.substrates.api.Substrates.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static io.humainary.substrates.api.Substrates.cortex;
import static io.humainary.substrates.ext.serventis.ext.Actors.*;
import static org.assertj.core.api.Assertions.assertThat;

import io.humainary.substrates.ext.serventis.ext.Actors;

/**
 * Demonstration of the Actors API (RC6) - Speech Act Theory for conversational agents.
 * <p>
 * The Actors API enables observability of communicative actions (speech acts) performed
 * by conversational agents, whether human-to-human, human-to-AI, or AI-to-AI.
 * <p>
 * Key Concepts:
 * - 11 speech act types for practical coordination
 * - Reports speech acts, doesn't implement actors
 * - Supports dialogue analysis and coordination patterns
 * <p>
 * Speech Acts:
 * - Questions: ASK
 * - Assertions: AFFIRM, EXPLAIN, REPORT
 * - Coordination: REQUEST, COMMAND, ACKNOWLEDGE
 * - Refinement: DENY, CLARIFY
 * - Commitment: PROMISE, DELIVER
 * <p>
 * Use Cases:
 * - Operator-system interactions
 * - AI assistant conversations
 * - Multi-agent dialogue
 * - Command/control observability
 */
@DisplayName("Actors API (RC6) - Speech Act Communication")
class ActorsApiDemoTest {

    private Circuit circuit;
    private Conduit<Actor, Sign> actors;

    @BeforeEach
    void setUp() {
        circuit = cortex().circuit(cortex().name("actors-demo"));
        actors = circuit.conduit(
            cortex().name("actors"),
            Actors::composer
        );
    }

    @AfterEach
    void tearDown() {
        if (circuit != null) {
            circuit.close();
        }
    }

    @Test
    @DisplayName("Question-Answer pattern: ASK → EXPLAIN → ACKNOWLEDGE")
    void questionAnswerPattern() {
        // Scenario: Operator asks about consumer lag

        Actor operator = actors.percept(cortex().name("operator.alice"));
        Actor system = actors.percept(cortex().name("kafka-ops-bot"));

        List<Sign> operatorActs = new ArrayList<>();
        List<Sign> systemActs = new ArrayList<>();

        actors.subscribe(cortex().subscriber(
            cortex().name("observer"),
            (Subject<Channel<Sign>> subject, Registrar<Sign> registrar) -> {
                registrar.register(sign -> {
                    if (subject.name().toString().contains("operator")) {
                        operatorActs.add(sign);
                    } else if (subject.name().toString().contains("bot")) {
                        systemActs.add(sign);
                    }
                });
            }
        ));

        // ACT: Execute dialogue pattern

        // 1. Operator asks question
        operator.ask();  // "What's the current consumer lag?"

        // 2. System explains
        system.explain();  // "Consumer-1 is 10K messages behind, within normal range"

        // 3. Operator acknowledges
        operator.acknowledge();  // "Got it, thanks"

        circuit.await();

        // ASSERT: Dialogue sequence captured
        assertThat(operatorActs).containsExactly(
            Sign.ASK,
            Sign.ACKNOWLEDGE
        );

        assertThat(systemActs).containsExactly(
            Sign.EXPLAIN
        );
    }

    @Test
    @DisplayName("Request-Delivery workflow: REQUEST → ACKNOWLEDGE → PROMISE → DELIVER")
    void requestDeliveryWorkflow() {
        // Scenario: Operator requests action, system delivers

        Actor operator = actors.percept(cortex().name("operator.bob"));
        Actor system = actors.percept(cortex().name("auto-scaler"));

        List<Sign> timeline = new ArrayList<>();

        actors.subscribe(cortex().subscriber(
            cortex().name("observer"),
            (Subject<Channel<Sign>> subject, Registrar<Sign> registrar) -> {
                registrar.register(sign -> {
                    timeline.add(sign);
                });
            }
        ));

        // ACT: Complete request workflow

        // 1. Operator requests action
        operator.request();  // "Please scale up consumers to 5 instances"

        // 2. System acknowledges
        system.acknowledge();  // "Understood, scaling consumer group"

        // 3. System promises delivery
        system.promise();  // "I will scale to 5 instances"

        // 4. System delivers result
        system.deliver();  // "Scaled consumer group to 5 instances"

        // 5. Operator acknowledges completion
        operator.acknowledge();  // "Perfect, thank you"

        circuit.await();

        // ASSERT: Complete workflow tracked
        assertThat(timeline).containsExactly(
            Sign.REQUEST,
            Sign.ACKNOWLEDGE,
            Sign.PROMISE,
            Sign.DELIVER,
            Sign.ACKNOWLEDGE
        );
    }

    @Test
    @DisplayName("Correction-Clarification: ASSERT → DENY → CLARIFY → ACKNOWLEDGE")
    void correctionClarificationPattern() {
        // Scenario: Misunderstanding requiring clarification

        Actor human = actors.percept(cortex().name("user.charlie"));
        Actor ai = actors.percept(cortex().name("assistant.claude"));

        List<String> dialogue = new ArrayList<>();

        actors.subscribe(cortex().subscriber(
            cortex().name("observer"),
            (Subject<Channel<Sign>> subject, Registrar<Sign> registrar) -> {
                registrar.register(sign -> {
                    dialogue.add(subject.name() + ":" + sign);
                });
            }
        ));

        // ACT: Correction cycle

        // 1. AI makes assertion
        ai.affirm();  // "The lag is critical"

        // 2. Human denies/corrects
        human.deny();  // "No, that's just temporary backpressure"

        // 3. AI clarifies
        ai.clarify();  // "I meant lag > 50K messages. Current lag is 12K"

        // 4. Human acknowledges
        human.acknowledge();  // "Ah, understood"

        circuit.await();

        // ASSERT: Correction pattern captured
        assertThat(dialogue).contains(
            "assistant.claude:AFFIRM",
            "user.charlie:DENY",
            "assistant.claude:CLARIFY",
            "user.charlie:ACKNOWLEDGE"
        );
    }

    @Test
    @DisplayName("Command vs Request distinction")
    void commandVsRequest() {
        // REQUEST = peer-level, no authority
        // COMMAND = hierarchical, presumed authority

        Actor peer = actors.percept(cortex().name("peer-service"));
        Actor subordinate = actors.percept(cortex().name("managed-process"));

        List<Sign> acts = new ArrayList<>();
        actors.subscribe(cortex().subscriber(
            cortex().name("observer"),
            (subject, registrar) -> {
                registrar.register(acts::add);
            }
        ));

        // ACT: Different coordination styles

        // Peer-level coordination (request)
        peer.request();  // "Could you please restart?"

        // Hierarchical coordination (command)
        peer.command();  // "Restart now"

        circuit.await();

        // ASSERT: Both styles available
        assertThat(acts).containsExactly(
            Sign.REQUEST,
            Sign.COMMAND
        );
    }

    @Test
    @DisplayName("Reporting vs Explaining vs Asserting")
    void informationSharingDistinctions() {
        // REPORT = neutral facts
        // EXPLAIN = reasoning/elaboration
        // ASSERT = confident claims/judgments

        Actor actor = actors.percept(cortex().name("test-actor"));

        List<Sign> acts = new ArrayList<>();
        actors.subscribe(cortex().subscriber(
            cortex().name("observer"),
            (subject, registrar) -> {
                registrar.register(acts::add);
            }
        ));

        // ACT: Different information styles

        actor.report();   // "Lag is 10K messages" (factual)
        actor.explain();  // "Lag increased because producer rate doubled" (reasoning)
        actor.affirm();  // "This lag level is acceptable" (judgment)

        circuit.await();

        // ASSERT: All three styles captured
        assertThat(acts).containsExactly(
            Sign.REPORT,
            Sign.EXPLAIN,
            Sign.AFFIRM
        );
    }

    @Test
    @DisplayName("Human-AI dialogue pattern")
    void humanAIDialogue() {
        // Realistic operator-AI assistant interaction

        Actor human = actors.percept(cortex().name("operator.diana"));
        Actor ai = actors.percept(cortex().name("claude-ops"));

        List<String> conversation = new ArrayList<>();

        actors.subscribe(cortex().subscriber(
            cortex().name("observer"),
            (Subject<Channel<Sign>> subject, Registrar<Sign> registrar) -> {
                registrar.register(sign -> {
                    String actor = subject.name().toString().contains("operator") ? "HUMAN" : "AI";
                    conversation.add(actor + ":" + sign);
                });
            }
        ));

        // ACT: Natural conversation flow

        human.ask();          // "Why is consumer-1 lagging?"
        ai.explain();         // "Producer rate increased 3x, consumer can't keep up"
        human.request();      // "Can you scale it up?"
        ai.acknowledge();     // "Yes, scaling now"
        ai.deliver();         // "Scaled to 5 instances, lag dropping"
        human.acknowledge();  // "Great, thanks!"

        circuit.await();

        // ASSERT: Natural dialogue captured
        assertThat(conversation).containsExactly(
            "HUMAN:ASK",
            "AI:EXPLAIN",
            "HUMAN:REQUEST",
            "AI:ACKNOWLEDGE",
            "AI:DELIVER",
            "HUMAN:ACKNOWLEDGE"
        );
    }

    @Test
    @DisplayName("Multi-agent collaborative refinement")
    void collaborativeRefinement() {
        // Multiple agents refining a solution together

        Actor alice = actors.percept(cortex().name("engineer.alice"));
        Actor bob = actors.percept(cortex().name("engineer.bob"));
        Actor system = actors.percept(cortex().name("deployment-bot"));

        List<String> collaboration = new ArrayList<>();

        actors.subscribe(cortex().subscriber(
            cortex().name("observer"),
            (Subject<Channel<Sign>> subject, Registrar<Sign> registrar) -> {
                registrar.register(sign -> {
                    String name = subject.name().toString();
                    String actor = name.contains("alice") ? "ALICE" :
                                   name.contains("bob") ? "BOB" : "BOT";
                    collaboration.add(actor + ":" + sign);
                });
            }
        ));

        // ACT: Collaborative problem-solving

        alice.request();      // "Deploy new consumer version"
        bob.deny();           // "Wait, we need to test first"
        alice.clarify();      // "I meant to staging, not production"
        bob.acknowledge();    // "Ah, yes, go ahead"
        system.acknowledge(); // "Deploying to staging"
        system.deliver();     // "Deployment complete"
        alice.acknowledge();  // "Thanks"
        bob.acknowledge();    // "Looks good"

        circuit.await();

        // ASSERT: Collaborative pattern captured
        assertThat(collaboration).hasSize(8);
        assertThat(collaboration).contains(
            "ALICE:REQUEST",
            "BOB:DENY",
            "ALICE:CLARIFY",
            "BOT:DELIVER"
        );
    }

    @Test
    @DisplayName("Promise-Deliver commitment tracking")
    void commitmentTracking() {
        // Track commitments from promise to delivery

        Actor actor = actors.percept(cortex().name("service-agent"));

        List<Sign> timeline = new ArrayList<>();
        actors.subscribe(cortex().subscriber(
            cortex().name("observer"),
            (subject, registrar) -> {
                registrar.register(timeline::add);
            }
        ));

        // ACT: Commitment lifecycle

        actor.promise();   // "I will complete the migration"
        // ... time passes ...
        actor.deliver();   // "Migration completed"

        circuit.await();

        // ASSERT: Commitment tracked from promise to delivery
        assertThat(timeline).containsExactly(
            Sign.PROMISE,
            Sign.DELIVER
        );
    }

    @Test
    @DisplayName("All 11 speech acts available")
    void allSpeechActsAvailable() {
        // Verify complete API surface

        Actor actor = actors.percept(cortex().name("test-actor"));

        // ACT: Emit all 11 signs

        // Questions
        actor.ask();

        // Assertions
        actor.affirm();
        actor.explain();
        actor.report();

        // Coordination
        actor.request();
        actor.command();
        actor.acknowledge();

        // Disagreement/Refinement
        actor.deny();
        actor.clarify();

        // Commitment
        actor.promise();
        actor.deliver();

        circuit.await();

        // ASSERT: All sign types exist
        Sign[] allSigns = Sign.values();
        assertThat(allSigns).hasSize(11);
        assertThat(allSigns).contains(
            Sign.ASK,
            Sign.AFFIRM,
            Sign.EXPLAIN,
            Sign.REPORT,
            Sign.REQUEST,
            Sign.COMMAND,
            Sign.ACKNOWLEDGE,
            Sign.DENY,
            Sign.CLARIFY,
            Sign.PROMISE,
            Sign.DELIVER
        );
    }

    @Test
    @DisplayName("Note: assert_() uses trailing underscore to avoid keyword conflict")
    void assertMethodNaming() {
        // Java "assert" is a keyword, so API uses "assert_()"

        Actor actor = actors.percept(cortex().name("test-actor"));

        AtomicReference<Sign> captured = new AtomicReference<>();
        actors.subscribe(cortex().subscriber(
            cortex().name("observer"),
            (subject, registrar) -> {
                registrar.register(captured::set);
            }
        ));

        // ACT: Use assert_() method
        actor.affirm();  // Note the trailing underscore

        circuit.await();

        // ASSERT: ASSERT sign emitted
        assertThat(captured.get()).isEqualTo(Sign.AFFIRM);
    }
}
