package io.fullerstack.substrates.name;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Simple sorted array implementation with binary search.
 *
 * Optimizations:
 * - Children stored in sorted String array
 * - Binary search for O(log n) lookup
 * - Cache-friendly linear memory layout
 * - No hash computation overhead
 *
 * Best for:
 * - Small to medium fanout (< 100 children per node)
 * - Read-heavy workloads
 * - When cache locality matters more than O(1) lookup
 */
public final class ArrayName implements Comparable<ArrayName> {

  private static final ConcurrentHashMap<String, ArrayName> PATH_CACHE = new ConcurrentHashMap<>();

  private final String segment;
  private final int depth;
  private final ArrayName parent;
  private final Optional<ArrayName> enclosureCache;
  private volatile String path;

  // Sorted parallel arrays for children
  private volatile String[] childSegments = new String[0];
  private volatile ArrayName[] childNodes = new ArrayName[0];
  private final Object childLock = new Object();

  private ArrayName(String segment, ArrayName parent) {
    this.segment = segment;
    this.depth = parent == null ? 1 : parent.depth + 1;
    this.parent = parent;
    this.enclosureCache = Optional.ofNullable(parent);
    this.path = parent == null ? segment : null;
  }

  // =========================================================================
  // Factory methods
  // =========================================================================

  public static ArrayName root(String segment) {
    Objects.requireNonNull(segment, "segment must not be null");
    if (segment.isEmpty() || segment.indexOf('.') >= 0) {
      throw new IllegalArgumentException("invalid segment: " + segment);
    }
    return PATH_CACHE.computeIfAbsent(segment, s -> new ArrayName(s, null));
  }

  public static ArrayName parse(String path) {
    Objects.requireNonNull(path, "path must not be null");
    if (path.isEmpty()) {
      throw new IllegalArgumentException("path cannot be empty");
    }

    ArrayName cached = PATH_CACHE.get(path);
    if (cached != null) {
      return cached;
    }

    int dot = path.indexOf('.');
    if (dot < 0) {
      return root(path);
    }

    ArrayName current = root(path.substring(0, dot));
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

  public ArrayName child(String segment) {
    Objects.requireNonNull(segment, "segment must not be null");
    if (segment.isEmpty() || segment.indexOf('.') >= 0) {
      throw new IllegalArgumentException("invalid segment: " + segment);
    }
    return childInternal(segment);
  }

  private ArrayName childInternal(String segment) {
    // Fast path: binary search
    String[] segments = childSegments;
    ArrayName[] nodes = childNodes;

    int idx = Arrays.binarySearch(segments, segment);
    if (idx >= 0) {
      return nodes[idx];
    }

    // Slow path: create new child
    synchronized (childLock) {
      segments = childSegments;
      nodes = childNodes;

      idx = Arrays.binarySearch(segments, segment);
      if (idx >= 0) {
        return nodes[idx];
      }

      ArrayName child = new ArrayName(segment, this);
      int insertPoint = -(idx + 1);

      String[] newSegments = new String[segments.length + 1];
      ArrayName[] newNodes = new ArrayName[nodes.length + 1];

      System.arraycopy(segments, 0, newSegments, 0, insertPoint);
      System.arraycopy(nodes, 0, newNodes, 0, insertPoint);
      newSegments[insertPoint] = segment;
      newNodes[insertPoint] = child;
      System.arraycopy(segments, insertPoint, newSegments, insertPoint + 1, segments.length - insertPoint);
      System.arraycopy(nodes, insertPoint, newNodes, insertPoint + 1, nodes.length - insertPoint);

      childSegments = newSegments;
      childNodes = newNodes;

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
  public Optional<ArrayName> enclosure() { return enclosureCache; }

  public void forEach(Consumer<ArrayName> consumer) {
    if (parent != null) parent.forEach(consumer);
    consumer.accept(this);
  }

  @Override
  public int compareTo(ArrayName other) {
    if (this == other) return 0;
    int depthCmp = Integer.compare(this.depth, other.depth);
    if (depthCmp != 0) return depthCmp;
    return this.path().compareTo(other.path());
  }

  @Override public boolean equals(Object obj) { return this == obj; }
  @Override public int hashCode() { return System.identityHashCode(this); }
  @Override public String toString() { return path(); }

  public static void clearCache() { PATH_CACHE.clear(); }
  public static int cacheSize() { return PATH_CACHE.size(); }
}
