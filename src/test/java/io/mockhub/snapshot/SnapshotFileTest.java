package io.mockhub.snapshot;

import static io.mockhub.support.Fixtures.json;
import static io.mockhub.support.Fixtures.tree;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.mockhub.api.ApiException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SnapshotFileTest {

  @Test
  void writesAndReadsBackASnapshotWithoutTheApiKeyValue() {
    // Given
    Snapshot snapshot = new Snapshot("s1", "p1", "p1", "Shop", "v1", "before the demo", "2026-11-04T09:00:00Z", "Alex", false,
        json("{'latency':{'mode':'fixed','fixedMs':100}}"), "X-Shop-Key",
        List.of(json("{'id':'a','request':{'method':'GET','urlPath':'/books'},'response':{'status':200}}")), Map.of("prices.csv", "MQ=="));

    // When
    Snapshot read = SnapshotFile.read(SnapshotFile.write(snapshot));

    // Then
    assertThat(SnapshotFile.write(snapshot).get("format").asText()).isEqualTo("mockhub-snapshot");
    assertThat(read.name()).isEqualTo("v1");
    assertThat(read.purpose()).isEqualTo("before the demo");
    assertThat(read.sourceProjectId()).isEqualTo("p1");
    assertThat(read.sourceProjectName()).isEqualTo("Shop");
    assertThat(read.apiKeyHeader()).isEqualTo("X-Shop-Key");
    assertThat(read.runtime().at("/latency/fixedMs").asInt()).isEqualTo(100);
    assertThat(read.stubs()).hasSize(1);
    assertThat(read.files()).containsEntry("prices.csv", "MQ==");
    assertThat(read.id()).isNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "{'format':'mockhub-backup','projects':[]}",
      "{'format':'mockhub-snapshot','version':99,'stubs':[]}",
      "{'format':'mockhub-snapshot'}",
      "{'format':'mockhub-snapshot','stubs':[1]}",
      "{'format':'mockhub-snapshot','stubs':[{'id':'a'},{'id':'a'}]}",
      "{'format':'mockhub-snapshot','stubs':[],'files':{'../x':'MQ=='}}",
      "{'format':'mockhub-snapshot','stubs':[],'files':{'a.csv':'not base64!'}}"})
  void refusesFilesThatAreNotSafeSnapshots(String file) {
    // When / Then
    assertThatThrownBy(() -> SnapshotFile.read(tree(file))).isInstanceOf(ApiException.class);
  }
}
