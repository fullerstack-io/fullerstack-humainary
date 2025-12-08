package io.fullerstack.substrates.name;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Height Optimized Trie (HOT) inspired implementation.
 *
 * HOT optimizes for:
 * - High fanout nodes (many children)
 * - Cache-friendly memory layout
 * - Minimal height through adaptive node sizing
 *
 * Key ideas adapted for segment-based trie:
 * - Compound nodes: collapse single-child chains
 * - SIMD-friendly sorted arrays for child lookup
 * - Discriminator bits for fast child selection
 *
 * For our segment-based use case:
 * - Use sorted array of (hash, child) pairs for cache locality
 * - Binary search with hash comparison (faster than string compare)
 * - Collapse single-child paths into compound segments
 */
public final class HotName implements Comparable<HotName> {

  private static final ConcurrentHashMap<String, HotName> PATH_CACHE = new ConcurrentHashMap<>();

  private final String segment;
  private final int segmentHash;  // Cached for fast comparison
  private final int depth;
  private final HotName parent;
  private final Optional<HotName> enclosureCache;
  private volatile String path;

  // Children stored as parallel sorted arrays (cache-friendly)
  // Sorted by hash for binary search
  private volatile int[] childHashes = new int[0];
  private volatile HotName[] childNodes = new HotName[0];
  private final Object childLock = new Object();

  private HotName(String segment, HotName parent) {
    this.segment = segment;
    this.segmentHash = segment.hashCode();
    this.depth = parent == null ? 1 : parent.depth + 1;
    this.parent = parent;
    this.enclosureCache = Optional.ofNullable(parent);
    this.path = parent == null ? segment : null;
  }

  // =========================================================================
  // Factory methods
  // =========================================================================

  public static HotName root(String segment) {
    Objects.requireNonNull(segment, "segment must not be null");
    if (segment.isEmpty() || segment.indexOf('.') >= 0) {
      throw new IllegalArgumentException("invalid segment: " + segment);
    }
    return PATH_CACHE.computeIfAbsent(segment, s -> new HotName(s, null));
  }

  public static HotName parse(String path) {
    Objects.requireNonNull(path, "path must not be null");
    if (path.isEmpty()) {
      throw new IllegalArgumentException("path cannot be empty");
    }

    HotName cached = PATH_CACHE.get(path);
    if (cached != null) {
      return cached;
    }

    int dot = path.indexOf('.');
    if (dot < 0) {
      return root(path);
    }

    HotName current = root(path.substring(0, dot));
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

  public HotName child(String segment) {
    Objects.requireNonNull(segment, "segment must not be null");
    if (segment.isEmpty() || segment.indexOf('.') >= 0) {
      throw new IllegalArgumentException("invalid segment: " + segment);
    }
    return childInternal(segment);
  }

  private HotName childInternal(String segment) {
    int hash = segment.hashCode();

    // Fast path: binary search in sorted hash array
    int[] hashes = childHashes;
    HotName[] nodes = childNodes;

    int idx = Arrays.binarySearch(hashes, hash);
    if (idx >= 0) {
      // Hash found - verify segment matches (handle hash collisions)
      HotName candidate = nodes[idx];
      if (candidate.segment.equals(segment)) {
        return candidate;
      }
      // Hash collision - linear scan nearby
      for (int i = idx; i < hashes.length && hashes[i] == hash; i++) {
        if (nodes[i].segment.equals(segment)) {
          return nodes[i];
        }
      }
      for (int i = idx - 1; i >= 0 && hashes[i] == hash; i--) {
        if (nodes[i].segment.equals(segment)) {
          return nodes[i];
        }
      }
    }

    // Slow path: create new child
    synchronized (childLock) {
      // Double-check after acquiring lock
      hashes = childHashes;
      nodes = childNodes;
      idx = Arrays.binarySearch(hashes, hash);

      if (idx >= 0) {
        for (int i = idx; i < hashes.length && hashes[i] == hash; i++) {
          if (nodes[i].segment.equals(segment)) {
            return nodes[i];
          }
        }
        for (int i = idx - 1; i >= 0 && hashes[i] == hash; i--) {
          if (nodes[i].segment.equals(segment)) {
            return nodes[i];
          }
        }
      }

      // Create and insert
      HotName child = new HotName(segment, this);

      int insertPoint = idx >= 0 ? idx : -(idx + 1);
      int[] newHashes = new int[hashes.length + 1];
      HotName[] newNodes = new HotName[nodes.length + 1];

      System.arraycopy(hashes, 0, newHashes, 0, insertPoint);
      System.arraycopy(nodes, 0, newNodes, 0, insertPoint);
      newHashes[insertPoint] = hash;
      newNodes[insertPoint] = child;
      System.arraycopy(hashes, insertPoint, newHashes, insertPoint + 1, hashes.length - insertPoint);
      System.arraycopy(nodes, insertPoint, newNodes, insertPoint + 1, nodes.length - insertPoint);

      childNodes = newNodes;
      childHashes = newHashes;

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
  public Optional<HotName> enclosure() { return enclosureCache; }

  public void forEach(Consumer<HotName> consumer) {
    if (parent != null) parent.forEach(consumer);
    consumer.accept(this);
  }

  @Override
  public int compareTo(HotName other) {
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
