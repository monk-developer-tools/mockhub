package io.mockhub.bench;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.PrintStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class Benchmark {
  private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

  record Options(String base, String user, String password, int connections, Duration warmup, Duration measure,
      Duration soak, Path out, List<String> only) {

    static Options parse(String[] args) {
      Map<String, String> values = new LinkedHashMap<>();
      for (int i = 0; i + 1 < args.length; i += 2) {
        if (!args[i].startsWith("--")) throw new IllegalArgumentException("Expected an option like --base, got " + args[i]);
        values.put(args[i].substring(2), args[i + 1]);
      }
      if (args.length % 2 != 0) throw new IllegalArgumentException("Option " + args[args.length - 1] + " needs a value");
      String only = values.getOrDefault("only", "");
      return new Options(
          values.getOrDefault("base", "http://localhost:8080"),
          values.getOrDefault("user", "admin"),
          values.getOrDefault("password", ""),
          Integer.parseInt(values.getOrDefault("connections", "32")),
          Duration.ofSeconds(Long.parseLong(values.getOrDefault("warmup", "20"))),
          Duration.ofSeconds(Long.parseLong(values.getOrDefault("duration", "60"))),
          Duration.ofSeconds(Long.parseLong(values.getOrDefault("soak", "0"))),
          Path.of(values.getOrDefault("out", "benchmark-results.json")),
          only.isBlank() ? List.of() : List.of(only.split(",")));
    }
  }

  public static void main(String[] args) throws Exception {
    run(Options.parse(args), System.out);
  }

  static List<LoadRun.Result> run(Options options, PrintStream log) throws Exception {
    URI uri = URI.create(options.base());
    String host = uri.getHost();
    int port = uri.getPort() < 0 ? 80 : uri.getPort();
    BenchSetup setup = new BenchSetup(options.base());
    Duration startup = setup.awaitHealthy(Duration.ofMinutes(2));
    setup.signIn(options.user(), options.password());
    setup.createProject(Scenarios.SLUG, Scenarios.mappings());
    String hostHeader = uri.getPort() < 0 ? host : host + ":" + port;
    Scenarios scenarios = new Scenarios(hostHeader, options.connections());
    List<Scenario> plan = new ArrayList<>();
    for (Scenario s : scenarios.all()) if (options.only().isEmpty() || options.only().contains(s.name())) plan.add(s);
    LoadRun load = new LoadRun(host, port);
    List<LoadRun.Result> results = new ArrayList<>();
    log.println("| Scenario | Connections | Requests/s | MB/s | p50 ms | p90 ms | p99 ms | p99.9 ms | max ms | Errors |");
    log.println("|---|---|---|---|---|---|---|---|---|---|");
    for (Scenario s : plan) {
      setup.awaitHealthy(Duration.ofSeconds(15));
      if (s.name().equals("many-stubs")) setup.createProject(Scenarios.MANY_SLUG, Scenarios.manyStubs());
      results.add(report(log, load.run(s, options.warmup(), options.measure(), Duration.ofSeconds(10))));
      if (s.name().equals("many-stubs")) setup.deleteProject(Scenarios.MANY_SLUG);
    }
    if (!options.soak().isZero() && (options.only().isEmpty() || options.only().contains("steady"))) {
      results.add(report(log, load.run(scenarios.soak(), Duration.ZERO, options.soak(), Duration.ofSeconds(10))));
    }
    write(options, startup, results);
    return results;
  }

  private static LoadRun.Result report(PrintStream log, LoadRun.Result r) {
    Latency l = r.latency();
    log.printf(Locale.ROOT, "| %s | %d | %.0f | %.1f | %s | %s | %s | %s | %s | %d%s |%n", r.scenario(), r.connections(), r.requestsPerSecond(),
        r.megabytesPerSecond(), ms(l.p50Micros()), ms(l.p90Micros()), ms(l.p99Micros()), ms(l.p999Micros()), ms(l.maxMicros()), r.errors(),
        r.firstError() == null ? "" : " (" + r.firstError() + ")");
    return r;
  }

  private static String ms(double micros) {
    return String.format(Locale.ROOT, "%.2f", micros / 1000.0);
  }

  private static void write(Options options, Duration startup, List<LoadRun.Result> results) throws Exception {
    ObjectNode root = JSON.createObjectNode();
    root.put("finishedAt", Instant.now().toString());
    root.put("target", options.base());
    root.put("waitedForHealthMillis", startup.toMillis());
    ObjectNode settings = root.putObject("settings");
    settings.put("connections", options.connections());
    settings.put("warmupSeconds", options.warmup().toSeconds());
    settings.put("measureSeconds", options.measure().toSeconds());
    settings.put("soakSeconds", options.soak().toSeconds());
    ObjectNode client = root.putObject("loadGenerator");
    client.put("java", System.getProperty("java.version"));
    client.put("processors", Runtime.getRuntime().availableProcessors());
    root.set("results", JSON.valueToTree(results));
    Path parent = options.out().toAbsolutePath().getParent();
    if (parent != null) Files.createDirectories(parent);
    JSON.writeValue(options.out().toFile(), root);
  }
}
