package io.mockhub.graphql;

import java.util.Set;

final class ScalarInfo {
  private static final Set<String> SPECIFIED = Set.of("Int", "Float", "String", "Boolean", "ID");

  private ScalarInfo() {}

  static boolean isGraphqlSpecifiedScalar(String name) {
    return SPECIFIED.contains(name);
  }
}
