package io.mockhub.bench;

import java.util.SplittableRandom;

record Scenario(String name, String title, int connections, int expectedStatus, RequestSource source) {
  interface RequestSource {
    byte[] next(SplittableRandom random);
  }

  Scenario withConnections(int count) {
    return new Scenario(name.replaceAll("-c\\d+$", "") + "-c" + count, title, count, expectedStatus, source);
  }
}
