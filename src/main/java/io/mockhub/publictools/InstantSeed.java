package io.mockhub.publictools;

import com.fasterxml.jackson.databind.JsonNode;
import io.mockhub.example.ExampleInstaller;
import java.util.List;

public record InstantSeed(List<JsonNode> stubs, List<ExampleInstaller.Seed> records, boolean strict, int leftOut, List<String> skipped) {
  public InstantSeed(List<JsonNode> stubs, List<ExampleInstaller.Seed> records, boolean strict, int leftOut) {
    this(stubs, records, strict, leftOut, List.of());
  }

  static InstantSeed exactly(List<JsonNode> stubs) {
    return new InstantSeed(stubs, List.of(), true, 0);
  }
}
