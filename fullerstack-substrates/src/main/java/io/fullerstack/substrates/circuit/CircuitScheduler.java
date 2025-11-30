package io.fullerstack.substrates.circuit;

import static io.humainary.substrates.api.Substrates.cortex;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;
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
 * Production-ready Circuit scheduler that implements:
 *  - ingress: MPSC ring buffer for async multi-producer submissions
 *  - transit: SPSC LIFO stack for depth-first spawned tasks
 *  - single consumer running on a virtual thread
 *  - hybrid spin+park wait strategy for ultra-low-latency
 *  - await() that blocks until both queues are drained (supporting multiple waiters)
 */
public class CircuitScheduler implements Circuit {

    /* ---------------------------- MPSC Ring Buffer ---------------------------- */
    @SuppressWarnings("unchecked")
    private static final class MpscRing<T> {
        private final T[] buffer;
        private final int mask;

        // head = consumer index (only touched by consumer)
        private volatile long head = 0L;
        // padding to avoid false sharing
        @SuppressWarnings("unused")
        private long h1, h2, h3, h4, h5, h6;

        // tail = producer index (CASed by producers)
        private volatile long tail = 0L;
        // padding
        @SuppressWarnings("unused")
        private long t1, t2, t3, t4, t5, t6;

        private static final VarHandle ARRAY_VH;
        private static final VarHandle TAIL_VH;

        static {
            try {
                ARRAY_VH = MethodHandles.arrayElementVarHandle(Object[].class);
                TAIL_VH = MethodHandles.lookup().findVarHandle(MpscRing.class, "tail", long.class);
            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        MpscRing(int capacityPowerOfTwo) {
            if (Integer.bitCount(capacityPowerOfTwo) != 1)
                throw new IllegalArgumentException("capacity must be a power of two");
            this.buffer = (T[]) new Object[capacityPowerOfTwo];
            this.mask = capacityPowerOfTwo - 1;
        }

        // Multi-producer offer: CAS tail to claim slot
        boolean offer(T item) {
            while (true) {
                long t = tail;
                long h = head; // snapshot of head to check fullness

                if (t - h >= buffer.length) return false; // full

                if (TAIL_VH.compareAndSet(this, t, t + 1)) {
                    int idx = (int) (t & mask);
                    ARRAY_VH.setRelease(buffer, idx, item);
                    return true;
                }
                // failed CAS -> spin lightly
                Thread.onSpinWait();
            }
        }

        @SuppressWarnings("unchecked")
        T poll() {
            long h = head;
            int idx = (int) (h & mask);
            T v = (T) ARRAY_VH.getAcquire(buffer, idx);
            if (v == null) return null;
            buffer[idx] = null; // avoid retention
            head = h + 1;
            return v;
        }

        boolean isEmpty() {
            return head == tail;
        }
    }

    /* ---------------------------- SPSC LIFO Stack ---------------------------- */
    private static final class SpscStack<T> {
        private final Object[] stack;
        private int top = -1; // only touched by consumer/producer (single-thread semantics)

        SpscStack(int capacity) {
            this.stack = new Object[capacity];
        }

        boolean push(T item) {
            int ntop = top + 1;
            if (ntop >= stack.length) return false; // full
            stack[ntop] = item;
            top = ntop;
            return true;
        }

        @SuppressWarnings("unchecked")
        T pop() {
            if (top < 0) return null;
            T v = (T) stack[top];
            stack[top] = null;
            top--;
            return v;
        }

        boolean isEmpty() {
            return top < 0;
        }
    }

    /* ---------------------------- Configuration ---------------------------- */
    private static final int DEFAULT_INGRESS_CAPACITY = 1024;
    private static final int DEFAULT_TRANSIT_CAPACITY = 256;
    private static final int SPIN_ITERS = 100;
    private static final long PARK_NANOS = 100_000L; // 0.1 ms

    /* ---------------------------- Fields ---------------------------- */
    private final MpscRing<Runnable> ingress;
    private final SpscStack<Runnable> transit;

    private final Lock awaitLock = new ReentrantLock();
    private final Condition idleCondition = awaitLock.newCondition();

    // idle tracking
    private volatile boolean idle = true;       // true when quiescent
    private volatile int waiters = 0;           // number of await() callers

    // lifecycle
    private volatile boolean running = false;
    private volatile boolean closing = false;   // true when close() called but still draining
    private volatile Thread consumerThread;     // the virtual thread running the loop

    // runningTask is only accessed by consumer thread, so not volatile
    private Runnable runningTask = null;

    // Circuit-specific fields
    private final Subject<Circuit> circuitSubject;
    private final CopyOnWriteArrayList<Subscriber<State>> stateSubscribers = new CopyOnWriteArrayList<>();

    /* ---------------------------- Constructor ---------------------------- */
    public CircuitScheduler(Name name, Subject<?> parentSubject) {
        Objects.requireNonNull(name, "Circuit name cannot be null");
        Objects.requireNonNull(parentSubject, "Parent subject cannot be null");

        this.circuitSubject = new ContextualSubject<>(name, Circuit.class, parentSubject);
        this.ingress = new MpscRing<>(DEFAULT_INGRESS_CAPACITY);
        this.transit = new SpscStack<>(DEFAULT_TRANSIT_CAPACITY);

        // Start immediately
        start(name);
    }

    private void start(Name name) {
        if (running) return;
        running = true;
        Thread t = Thread.ofVirtual().unstarted(this::consumerLoop);
        consumerThread = t;
        t.setName("Circuit-" + name.part());
        t.start();
    }

    /* ---------------------------- Consumer Loop ---------------------------- */
    private void consumerLoop() {
        try {
            while (running && !Thread.currentThread().isInterrupted()) {
                // 1) transit (depth-first)
                Runnable task = transit.pop();
                if (task != null) {
                    runTask(task);
                    continue;
                }

                // 2) ingress (breadth-next)
                task = ingress.poll();
                if (task != null) {
                    runTask(task);
                    continue;
                }

                // 3) no immediate work -> check idle and wait using hybrid strategy
                checkIdleAndSignalIfNeeded();

                // If closing and both queues are empty, exit gracefully
                if (closing && transit.isEmpty() && ingress.isEmpty()) {
                    return;
                }

                // hybrid spin+park attempting to catch short bursts
                int spins = 0;
                while (running) {
                    // check transit first
                    task = transit.pop();
                    if (task != null) { runTask(task); break; }

                    task = ingress.poll();
                    if (task != null) { runTask(task); break; }

                    // If closing and both queues are empty, exit gracefully
                    if (closing && transit.isEmpty() && ingress.isEmpty()) {
                        return;
                    }

                    if (spins < SPIN_ITERS) {
                        spins++;
                        Thread.onSpinWait();
                        continue;
                    }

                    // park the virtual thread (releases carrier)
                    LockSupport.parkNanos(PARK_NANOS);

                    // after park, try again
                    spins = 0;
                }
            }
        } catch (Throwable t) {
            // allow user to observe exceptions in the consumer thread
            t.printStackTrace(System.err);
        } finally {
            // if exiting, mark idle and notify waiters so tests don't hang
            running = false;
            idle = true;
            signalIdleIfNeeded();
        }
    }

    private void runTask(Runnable task) {
        runningTask = task;
        try {
            task.run();
        } finally {
            runningTask = null;
            checkIdleAndSignalIfNeeded();
        }
    }

    // evaluate quiescence and signal awaiters if necessary
    private void checkIdleAndSignalIfNeeded() {
        if (!ingress.isEmpty()) return;
        if (!transit.isEmpty()) return;
        if (runningTask != null) return;

        // We're truly idle - set the flag
        // Need to acquire lock to ensure visibility to waiters
        awaitLock.lock();
        try {
            idle = true;
            if (waiters > 0) {
                idleCondition.signalAll();
            }
        } finally {
            awaitLock.unlock();
        }
    }

    // called during shutdown to ensure waiters are signalled
    private void signalIdleIfNeeded() {
        awaitLock.lock();
        try {
            idle = true;
            idleCondition.signalAll();
        } finally {
            awaitLock.unlock();
        }
    }

    /* ---------------------------- Task Submission ---------------------------- */
    private boolean isCircuitThread() {
        return Thread.currentThread() == consumerThread;
    }

    /**
     * Submit a task from any thread.
     * Must set idle=false BEFORE making the task visible to avoid races with await().
     */
    public void execute(Runnable task) {
        if (!running || closing) return;  // Reject new tasks once closed

        if (isCircuitThread()) {
            // spawn transit task from consumer thread
            idle = false;
            transit.push(task);
            return;
        }

        // mark not idle before visible
        idle = false;
        ingress.offer(task);
    }

    /* ---------------------------- Circuit Interface ---------------------------- */
    @Override
    public void await() {
        if (isCircuitThread()) {
            throw new IllegalStateException("Cannot call Circuit::await from within a circuit's thread");
        }

        awaitLock.lock();
        try {
            waiters++;
            try {
                while (!idle) {
                    idleCondition.awaitUninterruptibly();
                }
            } finally {
                waiters--;
            }
        } finally {
            awaitLock.unlock();
        }
    }

    @Override
    public void close() {
        closing = true;  // Signal that we're closing but still draining
        // Don't set running=false or interrupt - let consumer drain pending tasks
        // The consumer loop will check 'closing' and exit when queues are empty
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
    public <I, E> Cell<I, E> cell(Name name, Composer<E, Pipe<I>> ingressComposer, Composer<E, Pipe<E>> egressComposer, Pipe<? super E> pipe) {
        Objects.requireNonNull(name, "Name cannot be null");
        Objects.requireNonNull(ingressComposer, "Ingress composer cannot be null");
        Objects.requireNonNull(egressComposer, "Egress composer cannot be null");
        Objects.requireNonNull(pipe, "Pipe cannot be null");

        Conduit<Pipe<E>, E> cellConduit = conduit(name, Composer.pipe());
        RoutingConduit<Pipe<E>, E> transformingConduit = (RoutingConduit<Pipe<E>, E>) cellConduit;
        Channel<E> channel = new EmissionChannel<>(name, transformingConduit, null);

        Pipe<E> egressPipe = egressComposer.compose(channel);

        Channel<E> ingressChannel = new Channel<E>() {
            @Override
            public Subject<Channel<E>> subject() { return channel.subject(); }
            @Override
            public Pipe<E> pipe() { return egressPipe; }
            @Override
            public Pipe<E> pipe(Consumer<Flow<E>> configurer) {
                return CircuitScheduler.this.pipe(egressPipe, configurer);
            }
        };
        Pipe<I> ingressPipe = ingressComposer.compose(ingressChannel);

        cellConduit.subscribe(cortex().subscriber(
            cortex().name("cell-outlet-" + name),
            (Subject<Channel<E>> subject, Registrar<E> registrar) -> {
                registrar.register(pipe::emit);
            }
        ));

        Conduit<?, E> conduit = (Conduit<?, E>) cellConduit;
        return new CellNode<>(null, name, ingressPipe, egressPipe, conduit, ingressComposer, egressComposer, this.circuitSubject);
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
