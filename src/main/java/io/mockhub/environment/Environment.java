package io.mockhub.environment;

import java.util.Map;
import java.util.TreeMap;

public record Environment(String name, Map<String, String> variables) {

  public Environment {
    variables = variables == null ? Map.of() : java.util.Collections.unmodifiableMap(new TreeMap<>(variables));
  }
}
