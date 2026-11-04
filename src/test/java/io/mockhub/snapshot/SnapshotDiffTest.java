package io.mockhub.snapshot;

import static io.mockhub.support.Fixtures.json;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SnapshotDiffTest {

  private static ObjectNode stub(String id, String url, int status, int priority) {
    return json("{'id':'" + id + "','name':'" + url + "','priority':" + priority
        + ",'request':{'method':'GET','urlPath':'" + url + "'},'response':{'status':" + status + "}}");
  }

  private static SnapshotDiff.Side side(String label, List<ObjectNode> stubs, String runtime, Map<String, String> files) {
    return new SnapshotDiff.Side(label, stubs, json(runtime), "X-API-Key", files);
  }

  @Test
  void findsAddedRemovedChangedAndMovedStubsAndFileAndRuntimeChanges() {
    // Given
    SnapshotDiff.Side before = side("v1", List.of(stub("a", "/books", 200, 10), stub("b", "/authors", 200, 20), stub("c", "/old", 200, 30)),
        "{'latency':null}", Map.of("prices.csv", "MQ==", "logo.png", "Mg=="));
    SnapshotDiff.Side after = side("Now", List.of(stub("b", "/authors", 200, 10), stub("a", "/books", 503, 20), stub("d", "/new", 201, 30)),
        "{'latency':{'mode':'fixed','fixedMs':200}}", Map.of("prices.csv", "Mw==", "terms.pdf", "NA=="));

    // When
    JsonNode diff = SnapshotDiff.compare(before, after);

    // Then
    assertThat(diff.at("/counts/added").asInt()).isEqualTo(1);
    assertThat(diff.at("/counts/removed").asInt()).isEqualTo(1);
    assertThat(diff.at("/counts/changed").asInt()).isEqualTo(2);
    assertThat(diff.get("stubs")).extracting(s -> s.get("id").asText() + ":" + s.get("status").asText())
        .containsExactly("b:moved", "a:changed", "d:added", "c:removed");
    assertThat(diff.at("/stubs/3/url").asText()).isEqualTo("/old");
    assertThat(diff.at("/files/added/0").asText()).isEqualTo("terms.pdf");
    assertThat(diff.at("/files/removed/0").asText()).isEqualTo("logo.png");
    assertThat(diff.at("/files/changed/0").asText()).isEqualTo("prices.csv");
    assertThat(diff.at("/runtime/changed").asBoolean()).isTrue();
    assertThat(diff.get("identical").asBoolean()).isFalse();
  }

  @Test
  void theSameStateIsIdentical() {
    // Given
    SnapshotDiff.Side one = side("v1", List.of(stub("a", "/books", 200, 10)), "{}", Map.of("a.csv", "MQ=="));

    // When
    JsonNode diff = SnapshotDiff.compare(one, one);

    // Then
    assertThat(diff.get("identical").asBoolean()).isTrue();
    assertThat(diff.at("/counts/unchanged").asInt()).isEqualTo(1);
    assertThat(diff.get("stubs")).isEmpty();
  }
}
