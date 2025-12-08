package io.fullerstack.substrates.name;

import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * IdentityHashMap-based implementation exploiting interned strings.
 *
 * Key insight: If segment strings are interned (same reference for same value),
 * IdentityHashMap can use reference equality (==) instead of equals().
 *
 * Benefits:
 * - No hash computation (uses System.identityHashCode)
 * - No string comparison (uses == instead of equals)
 * - Faster than HashMap for interned keys
 *
 * Requirement: Callers must pass interned strings or string literals.
 */
public final class IdentityName implements Comparable<IdentityName> {

  private static final ConcurrentHashMap<String, IdentityName> PATH_CACHE = new ConcurrentHashMap<>();

  // Intern pool for segments
  private static final ConcurrentHashMap<String, String> INTERN_POOL = new ConcurrentHashMap<>();

  private final String segment;
  private final int depth;
  private final IdentityName parent;
  private final Optional<IdentityName> enclosureCache;
  private volatile String path;

  // Identity-based children map (requires interned keys)
  // Note: IdentityHashMap is not thread-safe, so we synchronize writes
  private final IdentityHashMap<String, IdentityName> children = new IdentityHashMap<>(4);
  private final Object childLock = new Object();

  private IdentityName(String segment, IdentityName parent) {
    this.segment = segment;
    this.depth = parent == null ? 1 : parent.depth + 1;
    this.parent = parent;
    this.enclosureCache = Optional.ofNullable(parent);
    this.path = parent == null ? segment : null;
  }

  // =========================================================================
  // Factory methods
  // =========================================================================

  private static String intern(String s) {
    // Use our own intern pool (faster than String.intern() which uses native)
    String existing = INTERN_POOL.putIfAbsent(s, s);
    return existing != null ? existing : s;
  }

  public static IdentityName root(String segment) {
    Objects.requireNonNull(segment, "segment must not be null");
    if (segment.isEmpty() || segment.indexOf('.') >= 0) {
      throw new IllegalArgumentException("invalid segment: " + segment);
    }
    String interned = intern(segment);
    return PATH_CACHE.computeIfAbsent(interned, s -> new IdentityName(s, null));
  }

  public static IdentityName parse(String path) {
    Objects.requireNonNull(path, "path must not be null");
    if (path.isEmpty()) {
      throw new IllegalArgumentException("path cannot be empty");
    }

    IdentityName cached = PATH_CACHE.get(path);
    if (cached != null) {
      return cached;
    }

    int dot = path.indexOf('.');
    if (dot < 0) {
      return root(path);
    }

    IdentityName current = root(path.substring(0, dot));
    int start = dot + 1;

    while (start < path.length()) {
      int nextDot = path.indexOf('.', start);
      if (nextDot < 0) {
        current = current.childInternal(path.substring(start));
        break;
      } else {
        current = current.childInternal(path.substring(start, nextDot));
        start = nextDot + 1;
      }
    }

    PATH_CACHE.putIfAbsent(path, current);
    return current;
  }

  public IdentityName child(String segment) {
    Objects.requireNonNull(segment, "segment must not be null");
    if (segment.isEmpty() || segment.indexOf('.') >= 0) {
      throw new IllegalArgumentException("invalid segment: " + segment);
    }
    return childInternal(segment);
  }

  private IdentityName childInternal(String segment) {
    String interned = intern(segment);

    // Fast path: read without lock (IdentityHashMap.get is thread-safe for reads if no concurrent writes)
    IdentityName existing;
    synchronized (childLock) {
      existing = children.get(interned);
      if (existing != null) {
        return existing;
      }

      // Create new child
      IdentityName child = new IdentityName(interned, this);
      children.put(interned, child);
      PATH_CACHE.put(child.path(), child);
      return child;
    }
  }

  // =========================================================================
  // Name-like interface
  // =========================================================================

  public String path() {
    String p = path;
    if (p == null) {
      p = parent.path() + '.' + segment;
      path = p;
    }
    return p;
  }

  public String segment() { return segment; }
  public int depth() { return depth; }
  public Optional<IdentityName> enclosure() { return enclosureCache; }

  public void forEach(Consumer<IdentityName> consumer) {
    if (parent != null) parent.forEach(consumer);
    consumer.accept(this);
  }

  @Override
  public int compareTo(IdentityName other) {
    if (this == other) return 0;
    int depthCmp = Integer.compare(this.depth, other.depth);
    if (depthCmp != 0) return depthCmp;
    return this.path().compareTo(other.path());
  }

  @Override public boolean equals(Object obj) { return this == obj; }
  @Override public int hashCode() { return System.identityHashCode(this); }
  @Override public String toString() { return path(); }

  public static void clearCache() {
    PATH_CACHE.clear();
    INTERN_POOL.clear();
  }

  public static int cacheSize() { return PATH_CACHE.size(); }
}
