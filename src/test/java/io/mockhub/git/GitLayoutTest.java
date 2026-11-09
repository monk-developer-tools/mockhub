package io.mockhub.git;

import static io.mockhub.support.Fixtures.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.api.ApiException;
import io.mockhub.snapshot.Snapshot;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

class GitLayoutTest {

  private static Snapshot snapshot(List<ObjectNode> stubs, Map<String, String> files) {
    return new Snapshot("s1", "p1", "p1", "Orders", "Now", "", "", "", false, json("{'rateLimit':{'requests':5,'per':'minute'}}"),
        "X-API-Key", stubs, files);
  }

  private static byte[] utf8(String text) {
    return text.getBytes(StandardCharsets.UTF_8);
  }

  @Test
  void writesOneReadableFilePerStubAndKeepsTheMatchingOrder() {
    // Given
    Snapshot s = snapshot(List.of(
        json("{'id':'7f3a9c21-0000-0000-0000-000000000000','name':'Get order','request':{'method':'GET','urlPathTemplate':'/orders/{id}'},'response':{'status':200}}"),
        json("{'id':'11111111-0000-0000-0000-000000000000','request':{'method':'POST','urlPath':'/orders'},'response':{'status':201}}")),
        Map.of("logo.png", Base64.getEncoder().encodeToString(new byte[] {1, 2, 3})));

    // When
    Map<String, byte[]> files = GitLayout.write(s);
    Snapshot back = GitLayout.read(files, "main");

    // Then
    assertThat(files).containsOnlyKeys("mockhub.json", "mappings/get-order-7f3a9c21.json", "mappings/post-orders-11111111.json", "__files/logo.png");
    assertThat(new String(files.get("mappings/get-order-7f3a9c21.json"), StandardCharsets.UTF_8)).contains("\n  \"name\" : \"Get order\"");
    assertThat(back.stubs()).extracting(n -> n.path("request").path("method").asText()).containsExactly("GET", "POST");
    assertThat(back.sourceProjectId()).isEqualTo("p1");
    assertThat(back.runtime().at("/rateLimit/requests").asInt()).isEqualTo(5);
    assertThat(back.apiKeyHeader()).isEqualTo("X-API-Key");
    assertThat(Base64.getDecoder().decode(back.files().get("logo.png"))).containsExactly(1, 2, 3);
  }

  @Test
  void readsAPlainWireMockFolderWithoutAManifest() {
    // Given
    Map<String, byte[]> files = new TreeMap<>();
    files.put("mappings/b.json", utf8("{\"request\":{\"method\":\"GET\",\"url\":\"/b\"},\"response\":{\"status\":200}}"));
    files.put("mappings/a.json", utf8("{\"mappings\":[{\"request\":{\"method\":\"GET\",\"url\":\"/a1\"},\"response\":{\"status\":200}},"
        + "{\"request\":{\"method\":\"GET\",\"url\":\"/a2\"},\"response\":{\"status\":200}}]}"));
    files.put("mappings/nested/ignored.json", utf8("{}"));

    // When
    Snapshot read = GitLayout.read(files, "main abc1234");

    // Then
    assertThat(read.stubs()).extracting(n -> n.path("request").path("url").asText()).containsExactly("/a1", "/a2", "/b");
    assertThat(read.name()).isEqualTo("main abc1234");
    assertThat(read.runtime()).isNull();
  }

  @Test
  void namesTheFileOfABrokenStub() {
    // Given
    Map<String, byte[]> broken = Map.of("mappings/oops.json", utf8("{\"request\": "));
    Map<String, byte[]> noRequest = Map.of("mappings/empty.json", utf8("{\"response\":{\"status\":200}}"));
    Map<String, byte[]> foreign = Map.of("mockhub.json", utf8("{\"format\":\"something-else\"}"));

    // When / Then
    assertThatThrownBy(() -> GitLayout.read(broken, "main")).isInstanceOf(ApiException.class).hasMessageContaining("mappings/oops.json");
    assertThatThrownBy(() -> GitLayout.read(noRequest, "main")).hasMessageContaining("mappings/empty.json isn't a WireMock stub");
    assertThatThrownBy(() -> GitLayout.read(foreign, "main")).hasMessageContaining("isn't a MockHub Git file");
  }

  @Test
  void givesStubsWithTheSameNameDifferentFiles() {
    // Given
    java.util.Set<String> used = new java.util.HashSet<>();

    // When
    String first = GitLayout.fileName(json("{'name':'Health','request':{}}"), used);
    String second = GitLayout.fileName(json("{'name':'Health','request':{}}"), used);

    // Then
    assertThat(first).isEqualTo("health.json");
    assertThat(second).isEqualTo("health-2.json");
  }

  @Test
  void carriesEnvironmentsInTheManifestAndLeavesThemOutOfAPlainWireMockFolder() {
    // Given
    com.fasterxml.jackson.databind.JsonNode environments = io.mockhub.api.Jsons.MAPPER.createArrayNode().add(json("{'name':'qa','variables':{'bank':'qa-bank'}}"));
    Snapshot s = new Snapshot("s1", "p1", "p1", "Orders", "Now", "", "", "", false, null, null, List.of(), Map.of(), environments);
    Map<String, byte[]> plain = Map.of("mappings/a.json", utf8("{\"request\":{\"method\":\"GET\",\"url\":\"/a\"},\"response\":{\"status\":200}}"));

    // When
    Snapshot back = GitLayout.read(GitLayout.write(s), "main");
    Snapshot fromWireMock = GitLayout.read(plain, "main");

    // Then
    assertThat(back.environments()).isEqualTo(environments);
    assertThat(fromWireMock.environments()).isNull();
  }
}
