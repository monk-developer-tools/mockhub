package io.mockhub.integration;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
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
    return start(dataDir, env, false);
  }

  static MockHubServer startWithHttps(Path dataDir) {
    return start(dataDir, Map.of(), true);
  }

  private static MockHubServer start(Path dataDir, Map<String, String> env, boolean https) {
    Map<String, String> fullEnv = new HashMap<>(env);
    fullEnv.put("MOCKHUB_DATA_DIR", dataDir.toString());
    Config config = new Config(fullEnv);
    dataDir.resolve("mappings").toFile().mkdirs();
    dataDir.resolve("__files").toFile().mkdirs();
    MockHubExtensionFactory factory = new MockHubExtensionFactory(config);
    WireMockConfiguration options = options()
        .dynamicPort()
        .withRootDirectory(dataDir.toString())
        .extensions(factory);
    if (https) options.dynamicHttpsPort();
    if (config.proxyEnabled) {
      config.proxyCaKeystore().getParent().toFile().mkdirs();
      options.enableBrowserProxying(true).caKeystorePath(config.proxyCaKeystore().toString());
    }
    WireMockServer server = new WireMockServer(options);
    server.start();
    factory.serverStarted(server.port());
    return new MockHubServer(server);
  }

  String baseUrl() {
    return "http://localhost:" + server.port();
  }

  int httpsPort() {
    return server.httpsPort();
  }

  int port() {
    return server.port();
  }

  ApiClient api() {
    return new ApiClient(baseUrl());
  }

  @Override
  public void close() {
    server.stop();
  }
}
