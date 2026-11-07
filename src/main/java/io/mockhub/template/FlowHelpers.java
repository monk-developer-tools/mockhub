package io.mockhub.template;

import com.github.jknack.handlebars.Context;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Helper;
import com.github.jknack.handlebars.Options;
import com.github.tomakehurst.wiremock.extension.TemplateHelperProviderExtension;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntBinaryOperator;

public final class FlowHelpers implements TemplateHelperProviderExtension {
  public static final int MAX_REPEAT = 1000;
  static final String SWITCH = "mockhub.switch";

  private final IntBinaryOperator between;

  public FlowHelpers() {
    this((min, max) -> ThreadLocalRandom.current().nextInt(min, max + 1));
  }

  FlowHelpers(IntBinaryOperator between) {
    this.between = between;
  }

  private static final class Switch {
    private final Object value;
    private boolean matched;

    private Switch(Object value) {
      this.value = value;
    }
  }

  @Override
  public String getName() {
    return "mockhub-flow";
  }

  @Override
  public Map<String, Helper<?>> provideTemplateHelpers() {
    return Map.of(
        "switch", (Helper<Object>) FlowHelpers::switchBlock,
        "case", (Helper<Object>) FlowHelpers::caseBlock,
        "repeat", (Helper<Object>) this::repeat);
  }

  private static Object switchBlock(Object value, Options options) throws IOException {
    Object previous = options.data(SWITCH);
    options.data(SWITCH, new Switch(value));
    try {
      return options.fn();
    } finally {
      options.data(SWITCH, previous);
    }
  }

  private static Object caseBlock(Object value, Options options) throws IOException {
    Switch state = current(options, "case");
    if (state.matched || !same(state.value, value)) return "";
    state.matched = true;
    return options.fn();
  }

  static Object defaultBlock(Options options) throws IOException {
    Switch state = current(options, "default");
    if (state.matched) return "";
    state.matched = true;
    return options.fn();
  }

  private static Switch current(Options options, String name) {
    Object state = options.data(SWITCH);
    if (!(state instanceof Switch s)) throw new IllegalArgumentException("{{#" + name + "}} only works inside {{#switch}}");
    return s;
  }

  private static boolean same(Object a, Object b) {
    return Objects.equals(a == null ? null : a.toString(), b == null ? null : b.toString());
  }

  private Object repeat(Object first, Options options) throws IOException {
    int min = count(first);
    int times = options.params.length > 0 ? between.applyAsInt(Math.min(min, count(options.param(0))), Math.max(min, count(options.param(0)))) : min;
    boolean comma = !"false".equalsIgnoreCase(String.valueOf(options.hash.getOrDefault("comma", "true")));
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < times; i++) {
      Context item = Context.newBuilder(options.context, i).build();
      item.data("index", i);
      item.data("total", times);
      item.data("first", i == 0);
      item.data("last", i == times - 1);
      if (comma && i > 0) out.append(',');
      out.append(options.fn(item));
    }
    return new Handlebars.SafeString(out);
  }

  public static int count(Object value) {
    Long whole = ValueHelpers.whole(value);
    if (whole == null) throw new IllegalArgumentException("repeat needs a whole number of times, like {{#repeat 3}}");
    return (int) Math.max(0, Math.min(MAX_REPEAT, whole));
  }
}
