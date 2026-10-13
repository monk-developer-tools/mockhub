package io.mockhub.system;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

final class MockHubProcess implements AutoCloseable {

  private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(30);

  private final Process process;
  private final int port;

  private MockHubProcess(Process process, int port) {
    this.process = process;
    this.port = port;
  }

  static MockHubProcess start(Path dataDir, Map<String, String> env) throws IOException, InterruptedException {
    int port = freePort();
    String java = ProcessHandle.current().info().command().orElse("java");
    ProcessBuilder builder = new ProcessBuilder(List.of(java, "-jar", jarPath()))
        .redirectErrorStream(true)
        .redirectOutput(dataDir.resolveSibling(dataDir.getFileName() + "-" + port + ".log").toFile());
    builder.environment().putAll(env);
    builder.environment().put("PORT", String.valueOf(port));
    builder.environment().put("MOCKHUB_DATA_DIR", dataDir.toString());
    MockHubProcess started = new MockHubProcess(builder.start(), port);
    started.awaitHealthy();
    return started;
  }

  String baseUrl() {
    return "http://localhost:" + port;
  }

  @Override
  public void close() throws InterruptedException {
    process.destroy();
    if (!process.waitFor(java.util.concurrent.TimeUnit.SECONDS.toMillis(10), java.util.concurrent.TimeUnit.MILLISECONDS)) {
      process.destroyForcibly().waitFor();
    }
  }

  private void awaitHealthy() throws InterruptedException {
    HttpClient http = HttpClient.newHttpClient();
    Instant deadline = Instant.now().plus(STARTUP_TIMEOUT);
    while (Instant.now().isBefore(deadline)) {
      if (!process.isAlive()) throw new IllegalStateException("MockHub exited with code " + process.exitValue());
      try {
        HttpResponse<Void> r = http.send(HttpRequest.newBuilder(URI.create(baseUrl() + "/__admin/health")).build(),
            HttpResponse.BodyHandlers.discarding());
        if (r.statusCode() == 200) return;
      } catch (IOException notYetListening) {
        Thread.sleep(200);
      }
    }
    process.destroyForcibly();
    throw new IllegalStateException("MockHub did not become healthy within " + STARTUP_TIMEOUT);
  }

  private static String jarPath() {
    String jar = System.getProperty("mockhub.jar");
    if (jar == null) throw new IllegalStateException("System property mockhub.jar is not set; run through Gradle");
    return jar;
  }

  private static int freePort() throws IOException {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    }
  }
}
