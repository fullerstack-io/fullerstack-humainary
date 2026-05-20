package io.fullerstack.substrates;

import io.humainary.substrates.api.Substrates.Window;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FsWindowTest {

  private FsWindow<Integer> w(Integer... values) {
    return new FsWindow<>(values, 0, values.length, false);
  }

  private List<Integer> toList(Window<Integer> w) {
    List<Integer> out = new ArrayList<>();
    w.forEach(out::add);
    return out;
  }

  // ── Terminal operations ─────────────────────────────────────────────────

  @Test @DisplayName("size + isEmpty")
  void sizeAndEmpty() {
    assertThat(w().size()).isEqualTo(0);
    assertThat(w().isEmpty()).isTrue();
    assertThat(w(1, 2, 3).size()).isEqualTo(3);
    assertThat(w(1, 2, 3).isEmpty()).isFalse();
  }

  @Test @DisplayName("first + last")
  void firstAndLast() {
    assertThat(w(1, 2, 3).first()).isEqualTo(1);
    assertThat(w(1, 2, 3).last()).isEqualTo(3);
    assertThatThrownBy(() -> w().first()).isInstanceOf(NoSuchElementException.class);
    assertThatThrownBy(() -> w().last()).isInstanceOf(NoSuchElementException.class);
  }

  @Test @DisplayName("forEach iterates in encounter order")
  void forEach() {
    assertThat(toList(w(1, 2, 3, 4))).containsExactly(1, 2, 3, 4);
  }

  @Test @DisplayName("all / any / none / count")
  void predicates() {
    Window<Integer> w = w(1, 2, 3, 4, 5);
    assertThat(w.all(n -> n > 0)).isTrue();
    assertThat(w.all(n -> n > 2)).isFalse();
    assertThat(w.any(n -> n > 4)).isTrue();
    assertThat(w.any(n -> n > 10)).isFalse();
    assertThat(w.none(n -> n > 10)).isTrue();
    assertThat(w.none(n -> n > 3)).isFalse();
    assertThat(w.count(n -> n > 2)).isEqualTo(3);
  }

  @Test @DisplayName("fold + reduce")
  void foldAndReduce() {
    assertThat(w(1, 2, 3, 4).fold(0, Integer::sum)).isEqualTo(10);
    assertThat(w(1, 2, 3, 4).reduce(0, Integer::sum)).isEqualTo(10);
    assertThat(w().fold(99, Integer::sum)).isEqualTo(99);
    assertThat(w().reduce(99, Integer::sum)).isEqualTo(99);
  }

  // ── Restriction operations ─────────────────────────────────────────────

  @Test @DisplayName("prefix / suffix")
  void prefixSuffix() {
    Window<Integer> w = w(1, 2, 3, 4, 5);
    assertThat(toList(w.prefix(0))).isEmpty();
    assertThat(toList(w.prefix(2))).containsExactly(1, 2);
    assertThat(toList(w.prefix(99))).containsExactly(1, 2, 3, 4, 5);
    assertThat(toList(w.suffix(0))).isEmpty();
    assertThat(toList(w.suffix(2))).containsExactly(4, 5);
    assertThat(toList(w.suffix(99))).containsExactly(1, 2, 3, 4, 5);
  }

  @Test @DisplayName("skip / trim")
  void skipTrim() {
    Window<Integer> w = w(1, 2, 3, 4, 5);
    assertThat(toList(w.skip(0))).containsExactly(1, 2, 3, 4, 5);
    assertThat(toList(w.skip(2))).containsExactly(3, 4, 5);
    assertThat(toList(w.skip(99))).isEmpty();
    assertThat(toList(w.trim(0))).containsExactly(1, 2, 3, 4, 5);
    assertThat(toList(w.trim(2))).containsExactly(1, 2, 3);
    assertThat(toList(w.trim(99))).isEmpty();
  }

  @Test @DisplayName("slice")
  void slice() {
    Window<Integer> w = w(1, 2, 3, 4, 5);
    assertThat(toList(w.slice(0, 5))).containsExactly(1, 2, 3, 4, 5);
    assertThat(toList(w.slice(1, 3))).containsExactly(2, 3, 4);
    assertThat(toList(w.slice(0, 0))).isEmpty();
    assertThat(toList(w.slice(99, 2))).isEmpty();
    assertThat(toList(w.slice(3, 99))).containsExactly(4, 5);   // count clipped
  }

  @Test @DisplayName("reverse")
  void reverse() {
    Window<Integer> w = w(1, 2, 3, 4);
    assertThat(toList(w.reverse())).containsExactly(4, 3, 2, 1);
    assertThat(w.reverse().first()).isEqualTo(4);
    assertThat(w.reverse().last()).isEqualTo(1);
    // reverse-of-reverse
    assertThat(toList(w.reverse().reverse())).containsExactly(1, 2, 3, 4);
  }

  @Test @DisplayName("restriction ops on reversed views")
  void restrictionOnReversed() {
    Window<Integer> r = w(1, 2, 3, 4, 5).reverse();   // encounter: 5, 4, 3, 2, 1
    assertThat(toList(r.prefix(2))).containsExactly(5, 4);
    assertThat(toList(r.suffix(2))).containsExactly(2, 1);
    assertThat(toList(r.skip(2))).containsExactly(3, 2, 1);
    assertThat(toList(r.trim(2))).containsExactly(5, 4, 3);
    assertThat(toList(r.slice(1, 3))).containsExactly(4, 3, 2);
  }

  @Test @DisplayName("negative arguments throw IllegalArgumentException")
  void negativeArgsThrow() {
    Window<Integer> w = w(1, 2, 3);
    assertThatThrownBy(() -> w.prefix(-1)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> w.suffix(-1)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> w.skip(-1)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> w.trim(-1)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> w.slice(-1, 0)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> w.slice(0, -1)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test @DisplayName("null arguments throw NullPointerException")
  void nullArgsThrow() {
    Window<Integer> w = w(1, 2, 3);
    assertThatThrownBy(() -> w.all(null)).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> w.any(null)).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> w.none(null)).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> w.count(null)).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> w.forEach(null)).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> w.fold(null, Integer::sum)).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> w.fold(0, null)).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> w.reduce(null, Integer::sum)).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> w.reduce(0, null)).isInstanceOf(NullPointerException.class);
  }
}
