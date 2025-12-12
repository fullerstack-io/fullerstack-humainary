package io.fullerstack.substrates;

import io.humainary.substrates.api.Substrates.Configurer;
import io.humainary.substrates.api.Substrates.Flow;
import io.humainary.substrates.api.Substrates.Pipe;
import io.humainary.substrates.api.Substrates.Receptor;
import io.humainary.substrates.api.Substrates.Sift;
import io.humainary.substrates.api.Substrates.Subject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/// A configurable processing pipeline for data transformation.
///
/// Flow provides operators like diff, guard, limit, sample, etc.
/// Each operator returns a new Flow representing the extended pipeline.
/// Operators are composed in the order they are added (left-to-right).
///
/// @param <E> the class type of emitted value
public final class FsFlow<E> implements Flow<E> {

  /// The subject for pipes created by this flow.
  private final Subject<Pipe<E>> subject;

  /// The target pipe that receives emissions after all transformations.
  private final Pipe<E> target;

  /// List of operator factories. Each factory takes a downstream consumer
  /// and returns a consumer that applies the operator before calling downstream.
  private final List<Function<Consumer<E>, Consumer<E>>> operators = new ArrayList<>();

  /// Creates a new flow targeting the given pipe.
  ///
  /// @param subject the subject for created pipes
  /// @param target the pipe that receives emissions
  public FsFlow(Subject<Pipe<E>> subject, Pipe<E> target) {
    this.subject = subject;
    this.target = target;
  }

  /// Returns the pipe that applies this flow's transformations.
  /// Operators are composed in order: first operator wraps second, etc.
  public Pipe<E> pipe() {
    // Start with the target as the final downstream consumer
    Consumer<E> consumer = target::emit;
    // Apply operators in reverse order so the first-added operator executes first
    for (int i = operators.size() - 1; i >= 0; i--) {
      consumer = operators.get(i).apply(consumer);
    }
    return new FsConsumerPipe<>(subject, consumer);
  }

  @Override
  public Flow<E> diff() {
    operators.add(downstream -> {
      Object[] prev = { null };
      return v -> {
        if (!Objects.equals(v, prev[0])) {
          prev[0] = v;
          downstream.accept(v);
        }
      };
    });
    return this;
  }

  @Override
  public Flow<E> diff(E initial) {
    operators.add(downstream -> {
      Object[] prev = { initial };
      return v -> {
        if (!Objects.equals(v, prev[0])) {
          prev[0] = v;
          downstream.accept(v);
        }
      };
    });
    return this;
  }

  @Override
  public Flow<E> guard(Predicate<? super E> predicate) {
    operators.add(
      downstream ->
        v -> {
          if (predicate.test(v)) {
            downstream.accept(v);
          }
        }
    );
    return this;
  }

  @Override
  public Flow<E> guard(E initial, BiPredicate<? super E, ? super E> predicate) {
    operators.add(downstream -> {
      Object[] prev = { initial };
      return v -> {
        @SuppressWarnings("unchecked")
        E p = (E) prev[0];
        if (predicate.test(p, v)) {
          prev[0] = v;
          downstream.accept(v);
        }
      };
    });
    return this;
  }

  @Override
  public Flow<E> limit(int limit) {
    return limit((long) limit);
  }

  @Override
  public Flow<E> limit(long limit) {
    operators.add(downstream -> {
      long[] count = { 0 };
      return v -> {
        if (count[0] < limit) {
          count[0]++;
          downstream.accept(v);
        }
      };
    });
    return this;
  }

  @Override
  public Flow<E> peek(Receptor<? super E> receptor) {
    operators.add(
      downstream ->
        v -> {
          receptor.receive(v);
          downstream.accept(v);
        }
    );
    return this;
  }

  @Override
  public Flow<E> reduce(E initial, BinaryOperator<E> operator) {
    operators.add(downstream -> {
      Object[] acc = { initial };
      return v -> {
        @SuppressWarnings("unchecked")
        E a = (E) acc[0];
        E result = operator.apply(a, v);
        acc[0] = result;
        downstream.accept(result);
      };
    });
    return this;
  }

  @Override
  public Flow<E> replace(UnaryOperator<E> transformer) {
    operators.add(downstream -> v -> downstream.accept(transformer.apply(v)));
    return this;
  }

  @Override
  public Flow<E> sample(int sample) {
    operators.add(downstream -> {
      int[] count = { 0 };
      return v -> {
        // Emit on every Nth element (N-1, 2N-1, 3N-1, ...)
        // e.g., sample(3) emits at indices 2, 5, 8 (0-based)
        if (++count[0] % sample == 0) {
          downstream.accept(v);
        }
      };
    });
    return this;
  }

  @Override
  public Flow<E> sample(double probability) {
    operators.add(
      downstream ->
        v -> {
          if (Math.random() < probability) {
            downstream.accept(v);
          }
        }
    );
    return this;
  }

  @Override
  public Flow<E> sift(Comparator<? super E> comparator, Configurer<Sift<E>> configurer) {
    FsSift<E> sift = new FsSift<>(comparator);
    configurer.configure(sift);
    Predicate<E> filter = sift.predicate();
    operators.add(
      downstream ->
        v -> {
          if (filter.test(v)) {
            downstream.accept(v);
          }
        }
    );
    return this;
  }

  @Override
  public Flow<E> skip(long n) {
    operators.add(downstream -> {
      long[] count = { 0 };
      return v -> {
        if (count[0] >= n) {
          downstream.accept(v);
        } else {
          count[0]++;
        }
      };
    });
    return this;
  }
}
