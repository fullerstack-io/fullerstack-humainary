package io.fullerstack.substrates.circuit;

import static io.humainary.substrates.api.Substrates.cortex;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import io.fullerstack.substrates.cell.CellNode;
import io.fullerstack.substrates.channel.EmissionChannel;
import io.fullerstack.substrates.conduit.RoutingConduit;
import io.fullerstack.substrates.subject.ContextualSubject;
import io.fullerstack.substrates.subscription.CallbackSubscription;
import io.humainary.substrates.api.Substrates.Cell;
import io.humainary.substrates.api.Substrates.Channel;
import io.humainary.substrates.api.Substrates.Circuit;
import io.humainary.substrates.api.Substrates.Composer;
import io.humainary.substrates.api.Substrates.Conduit;
import io.humainary.substrates.api.Substrates.Flow;
import io.humainary.substrates.api.Substrates.Name;
import io.humainary.substrates.api.Substrates.Percept;
import io.humainary.substrates.api.Substrates.Pipe;
import io.humainary.substrates.api.Substrates.Registrar;
import io.humainary.substrates.api.Substrates.State;
import io.humainary.substrates.api.Substrates.Subject;
import io.humainary.substrates.api.Substrates.Subscriber;
import io.humainary.substrates.api.Substrates.Subscription;

/**
 * Sequential implementation of Substrates.Circuit using Virtual Threads.
 * Uses LinkedBlockingQueue for automatic producer-consumer coordination.
 */
public class SequentialCircuit implements Circuit {

    private final LinkedBlockingQueue<Runnable> ingressQueue = new LinkedBlockingQueue<>();
    private final ArrayDeque<Runnable> transitQueue = new ArrayDeque<>();
    private final Thread circuitThread;
    private final CountDownLatch threadStarted = new CountDownLatch(1);
    private final Subject<Circuit> circuitSubject;
    private final String circuitName;
    private final CopyOnWriteArrayList<Subscriber<State>> stateSubscribers = new CopyOnWriteArrayList<>();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private volatile int processing = 0;

    public SequentialCircuit(Name name, Subject<?> parentSubject) {
        Objects.requireNonNull(name, "Circuit name cannot be null");
        Objects.requireNonNull(parentSubject, "Parent subject cannot be null");
        // Circuit Subject has Cortex Subject as parent (Circuit → Cortex hierarchy)
        this.circuitSubject = new ContextualSubject<>(name, Circuit.class, parentSubject);
        this.circuitName = name.part().toString();

        // Start VIRTUAL thread for circuit processing
        this.circuitThread = Thread.ofVirtual()
            .name("Circuit-" + circuitName)
            .start(this::runLoop);

        try {
            threadStarted.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while starting circuit thread", e);
        }
    }

    private void runLoop() {
        threadStarted.countDown();

        while (running.get() || !transitQueue.isEmpty() || !ingressQueue.isEmpty()) {
                Runnable task;
                boolean processedItem = false;
                // Process transit queue first (depth-first priority)
                while ((task = transitQueue.pollFirst()) != null) {
                    processing++;
                    try { task.run(); } catch (Exception e) { }
                    finally { processing--; processedItem = true; }
                }

                if (!processedItem) {
                  Runnable taskIngress = ingressQueue.poll();
                  if (taskIngress != null) {
                      processing++;
                      try { taskIngress.run(); } catch (Exception e) { }
                      finally { processing--; processedItem = true; }
                  }
                }

                if (!processedItem) {
                  Thread.onSpinWait();
                }
        }
    }

    private boolean isCircuitThread() {
        return Thread.currentThread() == circuitThread;
    }

    public void execute(Runnable task) {
        // Silently ignore emissions after close (per TCK specification)
        if (!running.get()) return;

        // Fast-path for circuit thread
        if (isCircuitThread()) {
            // Circuit thread is the only writer to transitQueue - safe direct access
            transitQueue.addLast(task);
            return;
        }

        // External thread path - LinkedBlockingQueue handles all coordination
        ingressQueue.add(task);  // Automatically wakes blocked consumer!
    }

    @Override
    public void await() {
        if (isCircuitThread()) throw new IllegalStateException("Cannot call Circuit::await from within a circuit's thread");

        // Wait until queues are empty AND no work is currently being processed
        while (!ingressQueue.isEmpty() || !transitQueue.isEmpty() || processing > 0) {
            Thread.yield();  // Give circuit thread CPU time to drain queues
        }
    }

    @Override
    public void close() {
        // Signal the circuit to stop accepting new work and drain queues
        running.compareAndSet(true, false);
        // Virtual thread is daemon - will exit naturally when queues drain
    }

    @Override
    public <E> Pipe<E> pipe(Pipe<? super E> target) {
        Objects.requireNonNull(target, "Target pipe cannot be null");
        return new Pipe<E>() {
            @Override
            public void emit(E value) {
                execute(() -> target.emit(value));
            }

            @Override
            public void flush() {
                execute(target::flush);
            }
        };
    }

    @Override
    public <E> Pipe<E> pipe(Pipe<? super E> target, Consumer<Flow<E>> configurer) {
        Objects.requireNonNull(target, "Target pipe cannot be null");
        Objects.requireNonNull(configurer, "Flow configurer cannot be null");

        io.fullerstack.substrates.flow.FlowRegulator<E> flow = new io.fullerstack.substrates.flow.FlowRegulator<>();
        configurer.accept(flow);

        return new Pipe<E>() {
            @Override
            public void emit(E value) {
                execute(() -> {
                    E transformed = flow.apply(value);
                    if (transformed != null) {
                        target.emit(transformed);
                    }
                });
            }

            @Override
            public void flush() {
                execute(target::flush);
            }
        };
    }

    @Override
    public <P extends Percept, E> Conduit<P, E> conduit(Composer<E, ? extends P> composer) {
        Objects.requireNonNull(composer, "Composer cannot be null");
        return new RoutingConduit<>(circuitSubject.name(), composer, this);
    }

    @Override
    public <P extends Percept, E> Conduit<P, E> conduit(Name name, Composer<E, ? extends P> composer) {
        Objects.requireNonNull(name, "Conduit name cannot be null");
        Objects.requireNonNull(composer, "Composer cannot be null");
        return new RoutingConduit<>(name, composer, this);
    }

    @Override
    public <P extends Percept, E> Conduit<P, E> conduit(Name name, Composer<E, ? extends P> composer, Consumer<Flow<E>> configurer) {
        Objects.requireNonNull(name, "Conduit name cannot be null");
        Objects.requireNonNull(composer, "Composer cannot be null");
        Objects.requireNonNull(configurer, "Flow configurer cannot be null");
        return new RoutingConduit<>(name, composer, this, configurer);
    }

    @Override
    public <I, E> Cell<I, E> cell(Composer<E, Pipe<I>> ingress, Composer<E, Pipe<E>> egress, Pipe<? super E> pipe) {
        Objects.requireNonNull(ingress, "Ingress composer cannot be null");
        Objects.requireNonNull(egress, "Egress composer cannot be null");
        Objects.requireNonNull(pipe, "Pipe cannot be null");
        return cell(circuitSubject.name(), ingress, egress, pipe);
    }

    @Override
    public <I, E> Cell<I, E> cell(Name name, Composer<E, Pipe<I>> ingress, Composer<E, Pipe<E>> egress, Pipe<? super E> pipe) {
        Objects.requireNonNull(name, "Name cannot be null");
        Objects.requireNonNull(ingress, "Ingress composer cannot be null");
        Objects.requireNonNull(egress, "Egress composer cannot be null");
        Objects.requireNonNull(pipe, "Pipe cannot be null");
        Conduit<Pipe<E>, E> cellConduit = conduit(name, Composer.pipe());
        RoutingConduit<Pipe<E>, E> transformingConduit = (RoutingConduit<Pipe<E>, E>) cellConduit;
        Channel<E> channel = new EmissionChannel<>(name, transformingConduit, null);

        // Cache the egress pipe to avoid creating multiple ProducerPipes
        Pipe<E> egressPipe = egress.compose(channel);

        Channel<E> ingressChannel = new Channel<E>() {
            @Override
            public Subject<Channel<E>> subject() { return channel.subject(); }
            @Override
            public Pipe<E> pipe() { return egressPipe; }
            @Override
            public Pipe<E> pipe(Consumer<Flow<E>> configurer) {
                return SequentialCircuit.this.pipe(egressPipe, configurer);
            }
        };
        Pipe<I> ingressPipe = ingress.compose(ingressChannel);

        // Subscribe to route cell emissions to output pipe
        cellConduit.subscribe(cortex().subscriber(
            cortex().name("cell-outlet-" + name),
            (Subject<Channel<E>> subject, Registrar<E> registrar) -> {
                registrar.register(pipe::emit);
            }
        ));
        Conduit<?, E> conduit = (Conduit<?, E>) cellConduit;
        return new CellNode<>(null, name, ingressPipe, egressPipe, conduit, ingress, egress, this.circuitSubject);
    }

    @Override
    public Subject<Circuit> subject() {
        return circuitSubject;
    }

    @Override
    public Subscription subscribe(Subscriber<State> subscriber) {
        stateSubscribers.add(subscriber);
        return new CallbackSubscription(() -> stateSubscribers.remove(subscriber), circuitSubject);
    }
}
