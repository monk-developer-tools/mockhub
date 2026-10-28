package io.mockhub.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.mockhub.support.Zips;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WireMockArchiveTest {

  @Test
  void findsMappingsAndBodyFilesWhereverTheFolderSits() {
    // Given
    Map<String, String> entries = new LinkedHashMap<>();
    entries.put("src/main/resources/wiremock/mappings/orders.json", "{\"request\":{}}");
    entries.put("src/main/resources/wiremock/mappings/nested/more.json", "{\"request\":{}}");
    entries.put("src/main/resources/wiremock/mappings/README.md", "notes");
    entries.put("src/main/resources/wiremock/__files/orders/created.json", "{\"id\":1}");
    entries.put("__MACOSX/wiremock/mappings/._orders.json", "junk");
    entries.put("wiremock/mappings/.DS_Store", "junk");

    // When
    WireMockArchive archive = WireMockArchive.read(Zips.of(entries));

    // Then
    assertThat(archive.mappings()).containsOnlyKeys("orders.json", "nested/more.json");
    assertThat(archive.files()).containsOnlyKeys("orders/created.json");
    assertThat(new String(archive.files().get("orders/created.json"), StandardCharsets.UTF_8)).isEqualTo("{\"id\":1}");
  }

  @Test
  void aFolderOnlyCountsWhenItIsAWholePathSegment() {
    // Given
    Map<String, String> entries = Map.of("mymappings/a.json", "{}", "mappings/b.json", "{}");

    // When
    WireMockArchive archive = WireMockArchive.read(Zips.of(entries));

    // Then
    assertThat(archive.mappings()).containsOnlyKeys("b.json");
  }

  @Test
  void explainsWhatToZipWhenThereAreNoMappings() {
    assertThatThrownBy(() -> WireMockArchive.read(Zips.of(Map.of("__files/a.json", "{}"))))
        .hasMessageContaining("Zip the folder that holds the mappings and __files folders");
  }

  @Test
  void rejectsAnEmptyUpload() {
    assertThatThrownBy(() -> WireMockArchive.read(new byte[0])).hasMessageContaining("Send a .zip file");
  }
}
