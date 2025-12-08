package io.fullerstack.substrates.name;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Optimized Trie-based Name implementation.
 *
 * Optimizations:
 * - Single PATH_CACHE (no separate ROOTS cache)
 * - Lazy path string construction (only built when path() called)
 * - No validation on hot path (validate at entry points only)
 * - Cached Optional for enclosure()
 * - Eager small children map (avoids DCL overhead)
 */
public final class TreeName implements Comparable<TreeName> {

  // Single global cache for O(1) path lookups
  private static final ConcurrentHashMap<String, TreeName> PATH_CACHE = new ConcurrentHashMap<>();
  private static final char DOT = '.';

  private final String segment;         // Just this segment
  private final int depth;
  private final TreeName parent;        // Direct reference (null for roots)
  private final Optional<TreeName> enclosureCache;  // Cached Optional

  // Lazy path - only computed when needed
  private volatile String path;

  // Children map - eager allocation with small initial capacity
  private final ConcurrentHashMap<String, TreeName> children = new ConcurrentHashMap<>(4);

  // Private constructor for root names
  private TreeName(String segment) {
    this.segment = segment;
    this.path = segment;  // For roots, path == segment
    this.depth = 1;
    this.parent = null;
    this.enclosureCache = Optional.empty();
  }

  // Private constructor for child names
  private TreeName(String segment, TreeName parent) {
    this.segment = segment;
    this.path = null;  // Lazy - computed on first path() call
    this.depth = parent.depth + 1;
    this.parent = parent;
    this.enclosureCache = Optional.of(parent);
  }

  // =========================================================================
  // Factory methods
  // =========================================================================

  /**
   * Get or create a root name.
   */
  public static TreeName root(String segment) {
    // Validate at entry point only
    Objects.requireNonNull(segment, "segment must not be null");
    if (segment.isEmpty() || segment.indexOf(DOT) >= 0) {
      throw new IllegalArgumentException("invalid segment: " + segment);
    }
    return PATH_CACHE.computeIfAbsent(segment, TreeName::new);
  }

  /**
   * Parse a dot-separated path.
   * O(1) lookup if already cached, otherwise builds trie and caches.
   */
  public static TreeName parse(String path) {
    Objects.requireNonNull(path, "path must not be null");
    if (path.isEmpty()) {
      throw new IllegalArgumentException("path cannot be empty");
    }

    // Fast path: check cache first
    TreeName cached = PATH_CACHE.get(path);
    if (cached != null) {
      return cached;
    }

    // Slow path: build trie (only happens once per unique path)
    int dot = path.indexOf(DOT);
    if (dot < 0) {
      return root(path);
    }

    // Walk segment by segment
    TreeName current = root(path.substring(0, dot));
    int start = dot + 1;

    while (start < path.length()) {
      int nextDot = path.indexOf(DOT, start);
      if (nextDot < 0) {
        current = current.childInternal(path.substring(start));
        break;
      } else {
        current = current.childInternal(path.substring(start, nextDot));
        start = nextDot + 1;
      }
    }

    // Cache the full path for future lookups
    PATH_CACHE.putIfAbsent(path, current);
    return current;
  }

  /**
   * Get or create a child name - the hot path for chaining.
   * Validates segment.
   */
  public TreeName child(String segment) {
    Objects.requireNonNull(segment, "segment must not be null");
    if (segment.isEmpty() || segment.indexOf(DOT) >= 0) {
      throw new IllegalArgumentException("invalid segment: " + segment);
    }
    return childInternal(segment);
  }

  /**
   * Internal child lookup - no validation (already validated at entry).
   */
  private TreeName childInternal(String segment) {
    return children.computeIfAbsent(segment, s -> {
      TreeName child = new TreeName(s, this);
      // Register in path cache
      PATH_CACHE.put(child.path(), child);
      return child;
    });
  }

  /**
   * Chain multiple segments.
   */
  public TreeName child(String first, String... rest) {
    TreeName current = child(first);
    for (String segment : rest) {
      current = current.child(segment);
    }
    return current;
  }

  // =========================================================================
  // Name-like interface
  // =========================================================================

  /**
   * Get full path - lazily computed for non-roots.
   */
  public String path() {
    String p = path;
    if (p == null) {
      // Build path from parent
      p = parent.path() + DOT + segment;
      path = p;
    }
    return p;
  }

  public String segment() {
    return segment;
  }

  public int depth() {
    return depth;
  }

  public Optional<TreeName> enclosure() {
    return enclosureCache;  // Pre-cached, no allocation
  }

  public void forEach(Consumer<TreeName> consumer) {
    if (parent != null) {
      parent.forEach(consumer);
    }
    consumer.accept(this);
  }

  // =========================================================================
  // Comparable / Object methods
  // =========================================================================

  @Override
  public int compareTo(TreeName other) {
    if (this == other) return 0;
    int depthCmp = Integer.compare(this.depth, other.depth);
    if (depthCmp != 0) return depthCmp;
    return this.path().compareTo(other.path());
  }

  @Override
  public boolean equals(Object obj) {
    return this == obj;  // Identity-based (interned)
  }

  @Override
  public int hashCode() {
    return System.identityHashCode(this);
  }

  @Override
  public String toString() {
    return path();
  }

  // =========================================================================
  // Test/Benchmark utilities
  // =========================================================================

  public static void clearCache() {
    PATH_CACHE.clear();
  }

  public static int cacheSize() {
    return PATH_CACHE.size();
  }
}
