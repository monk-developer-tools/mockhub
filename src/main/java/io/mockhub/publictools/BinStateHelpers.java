package io.mockhub.publictools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.github.jknack.handlebars.Helper;
import com.github.jknack.handlebars.Options;
import io.mockhub.api.Jsons;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

final class BinStateHelpers {
  static final String DATA = "binState";

  private BinStateHelpers() {}

  static Map<String, Helper<?>> helpers() {
    return Map.of(
        "counter", helper((state, name, options) -> {
          boolean reset = Boolean.TRUE.equals(options.hash("reset"));
          return state.count(name, number(options.hash("add"), reset ? 0 : 1), reset);
        }),
        "saveValue", helper((state, name, options) -> {
          state.save(name, text(options.params.length > 0 ? options.param(0) : ""));
          return "";
        }),
        "savedValue", helper((state, name, options) -> state.saved(name).map(v -> (Object) v).orElseGet(() -> options.hash("default", ""))),
        "addToList", helper((state, name, options) -> {
          state.add(name, text(options.params.length > 0 ? options.param(0) : ""));
          return "";
        }),
        "savedList", helper((state, name, options) -> new Listed(state.list(name))));
  }

  private interface Use {
    Object apply(BinState state, String name, Options options);
  }

  private static Helper<Object> helper(Use use) {
    return (name, options) -> {
      BinState state = state(options);
      try {
        return use.apply(state, name == null ? "" : name.toString().trim(), options);
      } catch (IllegalArgumentException | BinState.Full e) {
        throw new RenderBudget.Exceeded(e.getMessage());
      }
    };
  }

  static final class Listed extends ArrayList<String> {
    Listed(Collection<String> items) {
      super(items);
    }

    @Override
    public String toString() {
      return compact(List.copyOf(this));
    }
  }

  private static BinState state(Options options) {
    Object state = options.data(DATA);
    if (state instanceof BinState s) return s;
    throw new RenderBudget.Exceeded("Counters, saved values and lists work in a webhook bin's answers.");
  }

  private static long number(Object value, long fallback) {
    if (value instanceof Number n) return n.longValue();
    if (value == null) return fallback;
    try {
      return Long.parseLong(value.toString().trim());
    } catch (NumberFormatException e) {
      return fallback;
    }
  }

  private static String compact(Object value) {
    try {
      return Jsons.MAPPER.writer().without(SerializationFeature.INDENT_OUTPUT).writeValueAsString(value);
    } catch (JsonProcessingException e) {
      return String.valueOf(value);
    }
  }

  private static String text(Object value) {
    if (value == null) return "";
    if (value instanceof JsonNode node) return node.isValueNode() ? node.asText() : compact(node);
    if (value instanceof Map<?, ?> || value instanceof Collection<?>) return compact(value);
    return value.toString();
  }
}
