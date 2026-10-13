package io.mockhub.integration;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.mockhub.Config;
import io.mockhub.ext.MockHubExtensionFactory;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

final class MockHubServer implements AutoCloseable {

  private final WireMockServer server;

  private MockHubServer(WireMockServer server) {
    this.server = server;
  }

  static MockHubServer start(Path dataDir) {
    return start(dataDir, Map.of());
  }

  static MockHubServer start(Path dataDir, Map<String, String> env) {
    Map<String, String> fullEnv = new HashMap<>(env);
    fullEnv.put("MOCKHUB_DATA_DIR", dataDir.toString());
    Config config = new Config(fullEnv);
    dataDir.resolve("mappings").toFile().mkdirs();
    dataDir.resolve("__files").toFile().mkdirs();
    WireMockServer server = new WireMockServer(options()
        .dynamicPort()
        .withRootDirectory(dataDir.toString())
        .extensions(new MockHubExtensionFactory(config)));
    server.start();
    return new MockHubServer(server);
  }

  String baseUrl() {
    return "http://localhost:" + server.port();
  }

  ApiClient api() {
    return new ApiClient(baseUrl());
  }

  @Override
  public void close() {
    server.stop();
  }
}
