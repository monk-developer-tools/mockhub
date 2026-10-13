package io.mockhub.export;

import static io.mockhub.support.Fixtures.project;
import static io.mockhub.support.Fixtures.stub;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;

class BrunoExporterTest {

  private final BrunoExporter exporter = new BrunoExporter();

  @Test
  void zipsACollectionWithEnvironmentFoldersAndRequests() throws Exception {
    // Given
    var stubs = List.of(
        stub("{'name':'Get user','request':{'method':'GET','urlPathTemplate':'/pay/users/{id}'},'response':{'status':200}}"),
        stub("{'name':'Get user','request':{'method':'DELETE','urlPathTemplate':'/pay/users/{id}'},'response':{'status':204}}"),
        stub("{'name':'Create','request':{'method':'POST','urlPath':'/pay/users','bodyPatterns':[{'equalToJson':{'n':1}}]},'response':{'status':201}}"));

    // When
    Map<String, String> files = unzip(exporter.export(project("pay"), stubs, "http://localhost:8080/pay"));

    // Then
    assertThat(files).containsKeys("pay/bruno.json", "pay/collection.bru", "pay/environments/MockHub.bru",
        "pay/users/folder.bru", "pay/users/Get user.bru", "pay/users/Get user 2.bru", "pay/users/Create.bru");
    assertThat(files.get("pay/environments/MockHub.bru")).contains("baseUrl: http://localhost:8080/pay");
    assertThat(files.get("pay/users/Get user.bru"))
        .contains("get {\n  url: {{baseUrl}}/users/:id\n  body: none")
        .contains("params:path {\n  id: 1\n}")
        .contains("res.status: eq 200");
    assertThat(files.get("pay/users/Create.bru"))
        .contains("body: json")
        .contains("body:json {\n  {\n    \"n\" : 1\n  }\n}")
        .contains("seq: 3");
  }

  @Test
  void sanitisesFileNames() {
    assertThat(BrunoExporter.safeName("GET /users/{id}?x")).isEqualTo("GET -users-id-x");
    assertThat(BrunoExporter.safeName("  ")).isEqualTo("request");
  }

  private static Map<String, String> unzip(byte[] zip) throws Exception {
    Map<String, String> files = new LinkedHashMap<>();
    try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
      for (ZipEntry e; (e = in.getNextEntry()) != null; ) {
        files.put(e.getName(), new String(in.readAllBytes(), StandardCharsets.UTF_8));
      }
    }
    return files;
  }
}
