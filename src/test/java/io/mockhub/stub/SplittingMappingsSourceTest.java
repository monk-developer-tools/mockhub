package io.mockhub.stub;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.common.SingleRootFileSource;
import com.github.tomakehurst.wiremock.common.filemaker.FilenameMaker;
import com.github.tomakehurst.wiremock.stubbing.InMemoryStubMappings;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SplittingMappingsSourceTest {

  private static final String SHARED = """
      {"mappings":[
        {"request":{"urlPath":"/orders/1"},"response":{"status":200}},
        {"request":{"urlPath":"/orders/2"},"response":{"status":200}},
        {"request":{"urlPath":"/orders/3"},"response":{"status":200}}]}""";

  @TempDir
  Path mappings;

  @TempDir
  Path state;

  @BeforeEach
  void writeMappings() throws IOException {
    Files.createDirectories(mappings.resolve("acme/orders"));
    Files.writeString(mappings.resolve("acme/orders/all.json"), SHARED);
    Files.writeString(mappings.resolve("ping.json"), "{\"request\":{\"urlPath\":\"/ping\"},\"response\":{\"status\":204}}");
  }

  private SplittingMappingsSource source() {
    return new SplittingMappingsSource(new SingleRootFileSource(mappings.toFile()), new FilenameMaker(), state.resolve("mapping-order.txt"));
  }

  private static List<StubMapping> load(SplittingMappingsSource source) {
    InMemoryStubMappings stubs = new InMemoryStubMappings();
    source.loadMappingsInto(stubs);
    return stubs.getAll();
  }

  private static StubMapping byUrl(List<StubMapping> stubs, String url) {
    return stubs.stream().filter(s -> url.equals(s.getRequest().getUrlPath())).findFirst().orElseThrow();
  }

  private List<String> files() throws IOException {
    try (Stream<Path> walk = Files.walk(mappings)) {
      return walk.filter(Files::isRegularFile).map(p -> mappings.relativize(p).toString().replace('\\', '/')).sorted().toList();
    }
  }

  @Test
  void loadsFilesWithOneStubAndFilesWithSeveral() {
    // When
    List<StubMapping> stubs = load(source());

    // Then
    assertThat(stubs).hasSize(4);
  }

  @Test
  void changingAStubFromASharedFileSplitsThatFileInPlace() throws IOException {
    // Given
    SplittingMappingsSource source = source();
    StubMapping second = byUrl(load(source), "/orders/2");
    StubMapping changed = withStatus(second, 202);

    // When
    source.save(changed);

    // Then
    assertThat(files()).doesNotContain("acme/orders/all.json").hasSize(4).allMatch(f -> f.equals("ping.json") || f.startsWith("acme/orders/"));
    List<StubMapping> reloaded = load(source());
    assertThat(reloaded).hasSize(4);
    assertThat(byUrl(reloaded, "/orders/2").getResponse().getStatus()).isEqualTo(202);
    assertThat(byUrl(reloaded, "/orders/1").getResponse().getStatus()).isEqualTo(200);
  }

  @Test
  void removingAStubFromASharedFileKeepsTheOthers() throws IOException {
    // Given
    SplittingMappingsSource source = source();
    StubMapping first = byUrl(load(source), "/orders/1");

    // When
    source.remove(first.getId());

    // Then
    List<StubMapping> reloaded = load(source());
    assertThat(reloaded).extracting(s -> s.getRequest().getUrlPath()).containsExactlyInAnyOrder("/orders/2", "/orders/3", "/ping");
    assertThat(files()).hasSize(3);
  }

  @Test
  void changingAStubWithItsOwnFileRewritesThatFile() throws IOException {
    // Given
    SplittingMappingsSource source = source();
    StubMapping ping = byUrl(load(source), "/ping");
    StubMapping changed = withStatus(ping, 200);

    // When
    source.save(changed);

    // Then
    assertThat(files()).containsExactlyInAnyOrder("acme/orders/all.json", "ping.json");
    assertThat(byUrl(load(source()), "/ping").getResponse().getStatus()).isEqualTo(200);
  }

  @Test
  void removingEverythingDeletesSharedAndSingleFiles() throws IOException {
    // Given
    SplittingMappingsSource source = source();
    load(source);

    // When
    source.removeAll();

    // Then
    assertThat(files()).isEmpty();
  }

  @Test
  void keepsTheOrderOfStubsAcrossSplitsAndRestarts() {
    // Given
    SplittingMappingsSource source = source();
    List<StubMapping> loaded = load(source);
    List<String> before = urlsInMatchOrder(loaded);
    StubMapping second = byUrl(loaded, "/orders/2");

    // When
    source.save(withStatus(second, 202));
    source.save(StubMapping.buildFrom("{\"id\":\"" + UUID.randomUUID() + "\",\"request\":{\"urlPath\":\"/orders/new\"},\"response\":{\"status\":201}}"));

    // Then
    List<String> after = urlsInMatchOrder(load(source()));
    assertThat(after.subList(1, after.size())).isEqualTo(before);
    assertThat(after.get(0)).isEqualTo("/orders/new");
  }

  private static List<String> urlsInMatchOrder(List<StubMapping> stubs) {
    return stubs.stream().map(s -> s.getRequest().getUrlPath()).toList();
  }

  private static StubMapping withStatus(StubMapping stub, int status) {
    return StubMapping.buildFrom(StubMapping.buildJsonStringFor(stub).replaceFirst("\"status\"\\s*:\\s*\\d+", "\"status\" : " + status));
  }
}
