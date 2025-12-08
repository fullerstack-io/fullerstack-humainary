package io.fullerstack.substrates.name;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Double Array Trie implementation for Name hierarchy.
 *
 * Classic double-array trie uses two arrays:
 * - BASE[]: starting position for transitions from each state
 * - CHECK[]: validates the transition belongs to correct parent
 *
 * Adapted for segment-based (not character-based) keys:
 * - Segments are mapped to integer codes
 * - Transitions use segment codes instead of characters
 *
 * Trade-offs:
 * - Very compact memory representation
 * - Cache-friendly linear array access
 * - Slower inserts (may require array rebuilding)
 * - Best for mostly-static datasets
 */
public final class DoubleArrayName implements Comparable<DoubleArrayName> {

  // Segment interning: String -> Integer code
  private static final ConcurrentHashMap<String, Integer> SEGMENT_CODES = new ConcurrentHashMap<>();
  private static final AtomicInteger NEXT_CODE = new AtomicInteger(1);

  // Double Array storage (grows as needed)
  private static volatile int[] BASE = new int[1024];
  private static volatile int[] CHECK = new int[1024];
  private static volatile DoubleArrayName[] NODES = new DoubleArrayName[1024];

  // Path cache for O(1) parse lookups
  private static final ConcurrentHashMap<String, DoubleArrayName> PATH_CACHE = new ConcurrentHashMap<>();

  // Root state
  private static final int ROOT_STATE = 1;
  private static final Object LOCK = new Object();

  static {
    BASE[ROOT_STATE] = ROOT_STATE;
  }

  private final String segment;
  private final int state;  // Position in double array
  private final int depth;
  private final DoubleArrayName parent;
  private final Optional<DoubleArrayName> enclosureCache;
  private volatile String path;

  private DoubleArrayName(String segment, int state, DoubleArrayName parent) {
    this.segment = segment;
    this.state = state;
    this.depth = parent == null ? 1 : parent.depth + 1;
    this.parent = parent;
    this.enclosureCache = Optional.ofNullable(parent);
    this.path = parent == null ? segment : null;
  }

  // =========================================================================
  // Factory methods
  // =========================================================================

  public static DoubleArrayName root(String segment) {
    Objects.requireNonNull(segment, "segment must not be null");
    if (segment.isEmpty() || segment.indexOf('.') >= 0) {
      throw new IllegalArgumentException("invalid segment: " + segment);
    }

    int code = getOrCreateCode(segment);
    int targetState = BASE[ROOT_STATE] + code;

    synchronized (LOCK) {
      ensureCapacity(targetState);

      if (CHECK[targetState] == ROOT_STATE && NODES[targetState] != null) {
        return NODES[targetState];
      }

      // Create new root
      DoubleArrayName node = new DoubleArrayName(segment, targetState, null);
      BASE[targetState] = targetState;  // Base for this node's children
      CHECK[targetState] = ROOT_STATE;
      NODES[targetState] = node;
      PATH_CACHE.put(segment, node);
      return node;
    }
  }

  public static DoubleArrayName parse(String path) {
    Objects.requireNonNull(path, "path must not be null");
    if (path.isEmpty()) {
      throw new IllegalArgumentException("path cannot be empty");
    }

    DoubleArrayName cached = PATH_CACHE.get(path);
    if (cached != null) {
      return cached;
    }

    int dot = path.indexOf('.');
    if (dot < 0) {
      return root(path);
    }

    DoubleArrayName current = root(path.substring(0, dot));
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

  public DoubleArrayName child(String segment) {
    Objects.requireNonNull(segment, "segment must not be null");
    if (segment.isEmpty() || segment.indexOf('.') >= 0) {
      throw new IllegalArgumentException("invalid segment: " + segment);
    }
    return childInternal(segment);
  }

  private DoubleArrayName childInternal(String segment) {
    int code = getOrCreateCode(segment);
    int targetState = BASE[state] + code;

    synchronized (LOCK) {
      ensureCapacity(targetState);

      if (CHECK[targetState] == state && NODES[targetState] != null) {
        return NODES[targetState];
      }

      // Handle collision - need to relocate
      if (CHECK[targetState] != 0 && CHECK[targetState] != state) {
        targetState = findEmptySlot(code);
        // In a full implementation, would relocate conflicting node
        // For now, use simple linear probing
      }

      DoubleArrayName node = new DoubleArrayName(segment, targetState, this);
      BASE[targetState] = targetState;
      CHECK[targetState] = state;
      NODES[targetState] = node;
      PATH_CACHE.put(node.path(), node);
      return node;
    }
  }

  // =========================================================================
  // Helper methods
  // =========================================================================

  private static int getOrCreateCode(String segment) {
    return SEGMENT_CODES.computeIfAbsent(segment, s -> NEXT_CODE.getAndIncrement());
  }

  private static void ensureCapacity(int required) {
    if (required >= BASE.length) {
      int newSize = Math.max(BASE.length * 2, required + 1024);
      int[] newBase = new int[newSize];
      int[] newCheck = new int[newSize];
      DoubleArrayName[] newNodes = new DoubleArrayName[newSize];
      System.arraycopy(BASE, 0, newBase, 0, BASE.length);
      System.arraycopy(CHECK, 0, newCheck, 0, CHECK.length);
      System.arraycopy(NODES, 0, newNodes, 0, NODES.length);
      BASE = newBase;
      CHECK = newCheck;
      NODES = newNodes;
    }
  }

  private static int findEmptySlot(int startCode) {
    int slot = startCode;
    while (CHECK[slot] != 0) {
      slot++;
      ensureCapacity(slot);
    }
    return slot;
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
  public Optional<DoubleArrayName> enclosure() { return enclosureCache; }

  public void forEach(Consumer<DoubleArrayName> consumer) {
    if (parent != null) parent.forEach(consumer);
    consumer.accept(this);
  }

  @Override
  public int compareTo(DoubleArrayName other) {
    if (this == other) return 0;
    int depthCmp = Integer.compare(this.depth, other.depth);
    if (depthCmp != 0) return depthCmp;
    return this.path().compareTo(other.path());
  }

  @Override public boolean equals(Object obj) { return this == obj; }
  @Override public int hashCode() { return System.identityHashCode(this); }
  @Override public String toString() { return path(); }

  public static void clearCache() {
    synchronized (LOCK) {
      SEGMENT_CODES.clear();
      NEXT_CODE.set(1);
      BASE = new int[1024];
      CHECK = new int[1024];
      NODES = new DoubleArrayName[1024];
      BASE[ROOT_STATE] = ROOT_STATE;
      PATH_CACHE.clear();
    }
  }

  public static int cacheSize() { return PATH_CACHE.size(); }
}
