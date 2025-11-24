package io.fullerstack.substrates.circuit;

import static io.humainary.substrates.api.Substrates.cortex;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import io.fullerstack.substrates.cell.CellNode;
import io.fullerstack.substrates.channel.EmissionChannel;
import io.fullerstack.substrates.conduit.RoutingConduit;
import io.fullerstack.substrates.id.SequentialIdentifier;
import io.fullerstack.substrates.state.LinkedState;
import io.fullerstack.substrates.subject.ContextualSubject;
import io.fullerstack.substrates.subscription.CallbackSubscription;
import io.humainary.substrates.api.Substrates.Cell;
import io.humainary.substrates.api.Substrates.Channel;
import io.humainary.substrates.api.Substrates.Circuit;
import io.humainary.substrates.api.Substrates.Composer;
import io.humainary.substrates.api.Substrates.Conduit;
import io.humainary.substrates.api.Substrates.Flow;
import io.humainary.substrates.api.Substrates.Id;
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
 */
public class SequentialCircuit implements Circuit {

    private final ConcurrentLinkedQueue<Runnable> ingressQueue = new ConcurrentLinkedQueue<>();
    private final ArrayDeque<Runnable> transitQueue = new ArrayDeque<>();
    private final Thread circuitThread;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition emptyCondition = lock.newCondition();
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final CountDownLatch threadStarted = new CountDownLatch(1);
    private final Subject<Circuit> circuitSubject;
    private final String circuitName;
    private final CopyOnWriteArrayList<Subscriber<State>> stateSubscribers = new CopyOnWriteArrayList<>();

    public SequentialCircuit(Name name) {
        Objects.requireNonNull(name, "Circuit name cannot be null");
        Id id = SequentialIdentifier.generate();
        this.circuitSubject = new ContextualSubject<>(id, name, LinkedState.empty(), Circuit.class);
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

        while (!Thread.currentThread().isInterrupted() && !closed.get()) {
            Runnable task;
            lock.lock();
            try {
                while (!transitQueue.isEmpty()) {
                    task = transitQueue.pollFirst();
                    lock.unlock();
                    try { task.run(); } catch (Exception e) { } finally { lock.lock(); }
                }
                while ((task = ingressQueue.poll()) != null) {
                    lock.unlock();
                    try { task.run(); } catch (Exception e) { } finally { lock.lock(); }
                    if (!transitQueue.isEmpty()) break;
                }
                while (ingressQueue.isEmpty() && transitQueue.isEmpty() && !closed.get()) {
                    emptyCondition.signalAll();
                    try {
                        emptyCondition.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            } finally {
                lock.unlock();
            }
        }
    }

    private boolean isCircuitThread() {
        return Thread.currentThread() == circuitThread;
    }

    public void execute(Runnable task) {
        if (closed.get()) throw new IllegalStateException("Circuit is closed");
        lock.lock();
        try {
            if (isCircuitThread()) {
                transitQueue.addLast(task);
            } else {
                ingressQueue.add(task);
            }
            emptyCondition.signalAll();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void await() {
        if (isCircuitThread()) throw new IllegalStateException("Cannot call await() from circuit thread");
        lock.lock();
        try {
            while (!ingressQueue.isEmpty() || !transitQueue.isEmpty()) {
                emptyCondition.await();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Await interrupted", e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        lock.lock();
        try {
            emptyCondition.signalAll();
        } finally {
            lock.unlock();
        }
        circuitThread.interrupt();
        try {
            circuitThread.join(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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
        return new RoutingConduit<>(circuitSubject.name(), composer, this);
    }

    @Override
    public <P extends Percept, E> Conduit<P, E> conduit(Name name, Composer<E, ? extends P> composer) {
        return new RoutingConduit<>(name, composer, this);
    }

    @Override
    public <P extends Percept, E> Conduit<P, E> conduit(Name name, Composer<E, ? extends P> composer, Consumer<Flow<E>> configurer) {
        return new RoutingConduit<>(name, composer, this, configurer);
    }

    @Override
    public <I, E> Cell<I, E> cell(Composer<E, Pipe<I>> ingress, Composer<E, Pipe<E>> egress, Pipe<? super E> pipe) {
        return cell(circuitSubject.name(), ingress, egress, pipe);
    }

    @Override
    public <I, E> Cell<I, E> cell(Name name, Composer<E, Pipe<I>> ingress, Composer<E, Pipe<E>> egress, Pipe<? super E> pipe) {
        Conduit<Pipe<E>, E> cellConduit = conduit(name, Composer.pipe());
        @SuppressWarnings("unchecked")
        RoutingConduit<Pipe<E>, E> transformingConduit = (RoutingConduit<Pipe<E>, E>) cellConduit;
        Channel<E> channel = new EmissionChannel<>(name, transformingConduit, null);
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
        cellConduit.subscribe(cortex().subscriber(
            cortex().name("cell-outlet-" + name),
            (Subject<Channel<E>> subject, Registrar<E> registrar) -> {
                registrar.register(pipe::emit);
            }
        ));
        @SuppressWarnings("unchecked")
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
