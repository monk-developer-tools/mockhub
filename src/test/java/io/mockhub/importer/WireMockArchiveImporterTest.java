package io.mockhub.importer;

import static io.mockhub.support.Fixtures.project;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.mockhub.files.BodyFiles;
import io.mockhub.project.Project;
import io.mockhub.support.Zips;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WireMockArchiveImporterTest {

  @TempDir
  Path data;

  @Test
  void savesBodyFilesInTheProjectAndPointsStubsAtThem() {
    // Given
    BodyFiles files = new BodyFiles(data);
    Project shop = project("shop");
    Map<String, String> entries = new LinkedHashMap<>();
    entries.put("wiremock/mappings/orders.json",
        "{\"mappings\":[{\"request\":{\"urlPath\":\"/orders/1\"},\"response\":{\"bodyFileName\":\"orders/order-1.json\"}},"
            + "{\"request\":{\"urlPath\":\"/orders/2\"},\"response\":{\"bodyFileName\":\"order-2.json\"}}]}");
    entries.put("wiremock/__files/orders/order-1.json", "{\"id\":1}");

    // When
    WireMockArchiveImporter.Result result = new WireMockArchiveImporter(files).parse(shop, Zips.of(entries));

    // Then
    assertThat(result.files()).isEqualTo(1);
    assertThat(result.stubs()).extracting(s -> s.at("/response/bodyFileName").asText())
        .containsExactly("projects/p-shop/orders/order-1.json", "order-2.json");
    assertThat(result.missingFiles()).containsExactly("order-2.json");
    assertThat(new String(files.read(shop.id(), "orders/order-1.json"), StandardCharsets.UTF_8)).isEqualTo("{\"id\":1}");
  }

  @Test
  void namesTheMappingFileThatCouldNotBeRead() {
    // Given
    byte[] zip = Zips.of(Map.of("mappings/broken.json", "{nope"));

    // When / Then
    assertThatThrownBy(() -> new WireMockArchiveImporter(new BodyFiles(data)).parse(project("shop"), zip))
        .hasMessageContaining("mappings/broken.json");
  }

  @Test
  void keepsFoldersAndFollowsDotSlashAndTemplatedReferences() {
    // Given
    BodyFiles files = new BodyFiles(data);
    Project shop = project("shop");
    Map<String, String> entries = new LinkedHashMap<>();
    entries.put("mock/mappings/acme/orders.json",
        "{\"mappings\":["
            + "{\"request\":{\"urlPath\":\"/a\"},\"response\":{\"bodyFileName\":\"./acme/orders/list.json\"}},"
            + "{\"request\":{\"urlPathPattern\":\"/orders/.*\"},\"response\":{\"bodyFileName\":\"./acme/orders/{{request.pathSegments.[1]}}.json\",\"transformers\":[\"response-template\"]}}]}");
    entries.put("mock/__files/acme/orders/list.json", "[]");
    entries.put("mock/__files/acme/orders/42.json", "{\"id\":42}");
    entries.put("mock/__files/acme/empty.txt", "");

    // When
    WireMockArchiveImporter.Result result = new WireMockArchiveImporter(files).parse(shop, Zips.of(entries));

    // Then
    assertThat(result.missingFiles()).isEmpty();
    assertThat(result.stubs()).extracting(s -> s.at("/response/bodyFileName").asText())
        .containsExactly("projects/p-shop/acme/orders/list.json", "projects/p-shop/acme/orders/{{request.pathSegments.[1]}}.json");
    assertThat(files.list(shop.id())).extracting(BodyFiles.Info::name).containsExactly("acme/empty.txt", "acme/orders/42.json", "acme/orders/list.json");
    assertThat(files.read(shop.id(), "acme/empty.txt")).isEmpty();
  }

  @Test
  void normalisesTheWaysWireMockMappingsReferToBodyFiles() {
    assertThat(WireMockArchiveImporter.normalise("./orders/list.json")).isEqualTo("orders/list.json");
    assertThat(WireMockArchiveImporter.normalise("/orders/list.json")).isEqualTo("orders/list.json");
    assertThat(WireMockArchiveImporter.normalise("orders/./2024/list.json")).isEqualTo("orders/2024/list.json");
    assertThat(WireMockArchiveImporter.normalise("./orders/{{regexExtract request.path 'id-(\\d+)'}}.json"))
        .isEqualTo("orders/{{regexExtract request.path 'id-(\\d+)'}}.json");
  }
}
