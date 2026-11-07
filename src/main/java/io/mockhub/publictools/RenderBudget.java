package io.mockhub.publictools;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class RenderBudget {
  static final int MAX_HELPER_CALLS = 20_000;
  static final int MAX_ITERATIONS = 2_000;
  static final int MAX_ITEMS = 10_000;
  static final int MAX_SIZE = 10_000;
  static final long MAX_CONTENT = 16L * 1024 * 1024;

  private static final Pattern DIGITS = Pattern.compile("\\d+");
  private static final ThreadLocal<RenderBudget> CURRENT = new ThreadLocal<>();

  private final long deadline;
  private int calls;
  private int iterations;
  private long content;

  private RenderBudget(long deadline) {
    this.deadline = deadline;
  }

  static final class Exceeded extends RuntimeException {
    Exceeded(String message) {
      super(message, null, false, false);
    }
  }

  static <T> T within(long timeoutMillis, Supplier<T> work) {
    CURRENT.set(new RenderBudget(System.nanoTime() + timeoutMillis * 1_000_000L));
    try {
      return work.get();
    } finally {
      CURRENT.remove();
    }
  }

  static RenderBudget current() {
    RenderBudget budget = CURRENT.get();
    if (budget == null) throw new IllegalStateException("Rendering outside a budget");
    return budget;
  }

  void call() {
    if (++calls > MAX_HELPER_CALLS) throw new Exceeded("The template calls helpers more than " + MAX_HELPER_CALLS + " times.");
    checkTime();
  }

  void iterate(Object items) {
    iterations += count(items);
    if (iterations > MAX_ITERATIONS) throw new Exceeded("Loops in the public playground run up to " + MAX_ITERATIONS + " times in total.");
    checkTime();
  }

  void loop(int times) {
    iterations += times;
    if (iterations > MAX_ITERATIONS) throw new Exceeded("Loops in the public playground run up to " + MAX_ITERATIONS + " times in total.");
    checkTime();
  }

    void content(long chars) {
    content += chars;
    if (content > MAX_CONTENT) throw new Exceeded("The template produces too much text.");
    checkTime();
  }

  void items(Collection<?> items) {
    if (items.size() > MAX_ITEMS) throw new Exceeded("Lists in the public playground hold up to " + MAX_ITEMS + " items.");
  }

  void size(Object value) {
    Long number = number(value);
    if (number != null && Math.abs(number) > MAX_SIZE) {
      throw new Exceeded("Sizes and ranges in the public playground go up to " + MAX_SIZE + ".");
    }
  }

  void sizesIn(Object value) {
    if (value instanceof CharSequence text && largestNumber(text.toString()) > MAX_SIZE) {
      throw new Exceeded("Sizes and ranges in the public playground go up to " + MAX_SIZE + ".");
    }
  }

  static long largestNumber(String text) {
    long largest = 0;
    Matcher digits = DIGITS.matcher(text);
    while (digits.find()) {
      String run = digits.group();
      largest = Math.max(largest, run.length() > 18 ? Long.MAX_VALUE : Long.parseLong(run));
    }
    return largest;
  }

  private void checkTime() {
    if (System.nanoTime() > deadline || Thread.currentThread().isInterrupted()) throw new Exceeded("The template took too long to render.");
  }

  private static int count(Object items) {
    if (items instanceof Collection<?> c) return c.size();
    if (items instanceof Map<?, ?> m) return m.size();
    if (items != null && items.getClass().isArray()) return Array.getLength(items);
    return 1;
  }

  private static Long number(Object value) {
    if (value instanceof Number n) return n.longValue();
    if (value instanceof CharSequence text) {
      String trimmed = text.toString().trim();
      if (!trimmed.matches("-?\\d{1,30}")) return null;
      return trimmed.length() > 18 ? Long.MAX_VALUE : Long.parseLong(trimmed);
    }
    return null;
  }
}
