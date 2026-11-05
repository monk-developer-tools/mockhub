package io.mockhub.tunnel;

import io.mockhub.api.Jsons;
import io.mockhub.playground.Header;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class TunnelClient {
  public record Settings(String server, String token, String name, String target, int workers) {}

  private static final Set<String> RESTRICTED = Set.of("host", "connection", "content-length", "expect", "upgrade", "transfer-encoding",
      "keep-alive", "te", "trailer", "proxy-connection");
  private static final Duration POLL_TIMEOUT = TunnelHub.POLL_WAIT.plusSeconds(30);
  private static final Duration TARGET_TIMEOUT = Duration.ofSeconds(28);

  private final Settings settings;
  private final HttpClient http;
  private final PrintStream out;
  private final AtomicBoolean running = new AtomicBoolean(true);
  private final AtomicReference<String> fatal = new AtomicReference<>();

  public TunnelClient(Settings settings, HttpClient http, PrintStream out) {
    this.settings = settings;
    this.http = http;
    this.out = out;
  }

  public static void main(String[] args) {
    Settings settings;
    try {
      settings = parse(args, System.getenv());
    } catch (IllegalArgumentException e) {
      System.err.println(e.getMessage());
      System.err.println(usage());
      System.exit(2);
      return;
    }
    HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NEVER).build();
    TunnelClient client = new TunnelClient(settings, http, System.out);
    Runtime.getRuntime().addShutdownHook(new Thread(client::stop, "mockhub-tunnel-stop"));
    String error = client.run();
    if (error != null) {
      System.err.println(error);
      System.exit(1);
    }
  }

  static Settings parse(String[] args, Map<String, String> env) {
    String server = env.getOrDefault("MOCKHUB_URL", "");
    String token = env.getOrDefault("MOCKHUB_TOKEN", "");
    String name = "";
    String target = "";
    int workers = 4;
    for (int i = 0; i < args.length; i++) {
      String arg = args[i];
      if (arg.equals("--help") || arg.equals("-h")) throw new IllegalArgumentException("MockHub tunnel");
      if (i + 1 >= args.length) throw new IllegalArgumentException("Missing a value for " + arg);
      String value = args[++i];
      switch (arg) {
        case "--server" -> server = value;
        case "--token" -> token = value;
        case "--name" -> name = value;
        case "--to" -> target = value;
        case "--workers" -> workers = Integer.parseInt(value);
        default -> throw new IllegalArgumentException("Unknown option " + arg);
      }
    }
    if (server.isBlank()) throw new IllegalArgumentException("Give the MockHub address with --server or MOCKHUB_URL");
    if (token.isBlank()) throw new IllegalArgumentException("Give an access token with --token or MOCKHUB_TOKEN");
    if (name.isBlank()) throw new IllegalArgumentException("Give the tunnel a name with --name, like --name laptop");
    if (!target.startsWith("http://") && !target.startsWith("https://")) {
      throw new IllegalArgumentException("Give the local service with --to, like --to http://localhost:3000");
    }
    if (workers < 1 || workers > TunnelHub.MAX_IN_FLIGHT) throw new IllegalArgumentException("--workers must be 1 to " + TunnelHub.MAX_IN_FLIGHT);
    return new Settings(trim(server), token.trim(), name.trim(), trim(target), workers);
  }

  static String usage() {
    return """
        Usage: java -jar mockhub.jar tunnel --server https://mockhub.example.com --token mh_... --name laptop --to http://localhost:3000

          --server   MockHub address (or MOCKHUB_URL)
          --token    a personal access token (or MOCKHUB_TOKEN)
          --name     tunnel name; stubs and callouts reach it as tunnel://<name>/path
          --to       the local service that answers, e.g. http://localhost:3000
          --workers  requests handled at the same time (default 4)""";
  }

  public String run() {
    out.printf("Tunnel %s: tunnel://%s -> %s through %s%n", settings.name(), settings.name(), settings.target(), settings.server());
    CountDownLatch done = new CountDownLatch(settings.workers());
    for (int i = 0; i < settings.workers(); i++) {
      Thread worker = new Thread(() -> {
        try {
          work();
        } finally {
          done.countDown();
        }
      }, "mockhub-tunnel-" + i);
      worker.setDaemon(true);
      worker.start();
    }
    try {
      done.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    return fatal.get();
  }

  public void stop() {
    running.set(false);
  }

  private void work() {
    while (running.get()) {
      try {
        HttpResponse<byte[]> polled = http.send(post(tunnelUrl("/next"), new byte[0]).timeout(POLL_TIMEOUT).build(),
            HttpResponse.BodyHandlers.ofByteArray());
        if (polled.statusCode() == 204) continue;
        if (polled.statusCode() == 200) {
          handle(TunnelMessages.readRequest(Jsons.MAPPER.readTree(polled.body())));
          continue;
        }
        String message = errorOf(polled);
        if (polled.statusCode() >= 500) {
          pause();
          continue;
        }
        fatal.compareAndSet(null, "MockHub refused the tunnel (" + polled.statusCode() + "): " + message);
        stop();
      } catch (IOException e) {
        if (running.get()) out.println("Can't reach " + settings.server() + " (" + e.getMessage() + "), retrying");
        pause();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        stop();
      }
    }
  }

  private void handle(TunnelMessages.Request request) throws IOException, InterruptedException {
    long started = System.nanoTime();
    TunnelMessages.Response answer;
    try {
      HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(settings.target() + request.path())).timeout(TARGET_TIMEOUT)
          .method(request.method(), HttpRequest.BodyPublishers.ofByteArray(request.body()));
      for (Header h : request.headers()) {
        if (!RESTRICTED.contains(h.name().toLowerCase(Locale.ROOT))) b.header(h.name(), h.value());
      }
      HttpResponse<byte[]> local = http.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
      List<Header> headers = new ArrayList<>();
      local.headers().map().forEach((name, values) -> {
        if (!name.startsWith(":") && !RESTRICTED.contains(name.toLowerCase(Locale.ROOT))) values.forEach(v -> headers.add(new Header(name, v)));
      });
      answer = new TunnelMessages.Response(local.statusCode(), headers, local.body());
    } catch (IOException | IllegalArgumentException e) {
      String message = "MockHub tunnel " + settings.name() + " couldn't reach " + settings.target() + ": " + e.getMessage();
      answer = new TunnelMessages.Response(502, List.of(new Header("Content-Type", "text/plain; charset=utf-8")), message.getBytes(StandardCharsets.UTF_8));
    }
    byte[] body = Jsons.write(TunnelMessages.write(answer)).getBytes(StandardCharsets.UTF_8);
    http.send(post(tunnelUrl("/responses/" + request.id()), body).timeout(Duration.ofSeconds(30)).build(), HttpResponse.BodyHandlers.discarding());
    out.printf("%s %s -> %d (%d ms)%n", request.method(), request.path(), answer.status(), Duration.ofNanos(System.nanoTime() - started).toMillis());
  }

  private HttpRequest.Builder post(String url, byte[] body) {
    return HttpRequest.newBuilder(URI.create(url))
        .header("Authorization", "Bearer " + settings.token())
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofByteArray(body));
  }

  private String tunnelUrl(String suffix) {
    return settings.server() + "/__admin" + TunnelServlet.PATH + "/" + settings.name() + suffix;
  }

  private static String errorOf(HttpResponse<byte[]> response) {
    try {
      return Jsons.MAPPER.readTree(response.body()).path("error").asText(new String(response.body(), StandardCharsets.UTF_8));
    } catch (IOException e) {
      return new String(response.body(), StandardCharsets.UTF_8);
    }
  }

  private void pause() {
    try {
      Thread.sleep(2000);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      stop();
    }
  }

  private static String trim(String url) {
    String t = url.trim();
    return t.endsWith("/") ? t.substring(0, t.length() - 1) : t;
  }
}
