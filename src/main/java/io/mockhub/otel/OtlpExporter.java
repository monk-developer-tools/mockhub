package io.mockhub.otel;

import io.mockhub.api.Jsons;
import io.mockhub.history.HistoryEntry;
import io.mockhub.history.ServedRequestListener;
import io.mockhub.playground.Header;
import io.mockhub.playground.HttpSender;
import io.mockhub.project.Project;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class OtlpExporter implements ServedRequestListener, AutoCloseable {
  static final int MAX_QUEUE = 10_000;
  static final int BATCH = 512;
  private static final Logger LOG = LoggerFactory.getLogger(OtlpExporter.class);
  private static final Duration TIMEOUT = Duration.ofSeconds(10);
  private static final long WARN_EVERY_MS = 60_000;

  private final OtlpConfig config;
  private final HttpSender http;
  private final LongSupplier clock;
  private final String version;
  private final ArrayBlockingQueue<OtlpJson.Request> queue = new ArrayBlockingQueue<>(MAX_QUEUE);
  private final OtlpMetrics metrics;
  private final AtomicLong dropped = new AtomicLong();
  private final List<Header> headers;
  private ScheduledExecutorService scheduler;
  private volatile long lastWarning;

  OtlpExporter(OtlpConfig config, HttpSender http, LongSupplier clock, String version) {
    this.config = config;
    this.http = http;
    this.clock = clock;
    this.version = version;
    this.metrics = new OtlpMetrics(clock.getAsLong());
    List<Header> list = new ArrayList<>();
    config.headers().forEach((k, v) -> list.add(new Header(k, v)));
    list.add(new Header("Content-Type", "application/json"));
    this.headers = List.copyOf(list);
  }

  public static ServedRequestListener start(OtlpConfig config, HttpSender http, String version) {
    if (!config.enabled()) return ServedRequestListener.NONE;
    if (!config.protocol().equals("http/json")) {
      LOG.warn("OTEL_EXPORTER_OTLP_PROTOCOL is {}; MockHub sends OTLP over HTTP with JSON (http/json)", config.protocol());
    }
    OtlpExporter exporter = new OtlpExporter(config, http, System::currentTimeMillis, version);
    exporter.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "mockhub-otlp");
      t.setDaemon(true);
      return t;
    });
    exporter.scheduler.scheduleWithFixedDelay(exporter::flushSafely, config.batchDelayMs(), config.batchDelayMs(), TimeUnit.MILLISECONDS);
    exporter.scheduler.scheduleWithFixedDelay(exporter::exportMetricsSafely, config.metricIntervalMs(), config.metricIntervalMs(), TimeUnit.MILLISECONDS);
    LOG.info("Sending OpenTelemetry traces to {}, metrics to {}, logs to {}", or(config.tracesUrl()), or(config.metricsUrl()), or(config.logsUrl()));
    return exporter;
  }

  @Override
  public void served(Project project, HistoryEntry entry) {
    OtlpJson.Request request = request(project, entry);
    if (!config.metricsUrl().isEmpty()) metrics.record(request);
    if ((!config.tracesUrl().isEmpty() || !config.logsUrl().isEmpty()) && !queue.offer(request)) dropped.incrementAndGet();
  }

  long dropped() {
    return dropped.get() + metrics.dropped();
  }

  void flush() {
    List<OtlpJson.Request> batch = new ArrayList<>(BATCH);
    while (queue.drainTo(batch, BATCH) > 0) {
      if (!config.tracesUrl().isEmpty()) post(config.tracesUrl(), Jsons.write(OtlpJson.traces(config.resource(), batch, version)));
      if (!config.logsUrl().isEmpty()) post(config.logsUrl(), Jsons.write(OtlpJson.logs(config.resource(), batch, version)));
      batch.clear();
    }
  }

  void exportMetrics() {
    if (config.metricsUrl().isEmpty() || metrics.isEmpty()) return;
    post(config.metricsUrl(), Jsons.write(metrics.snapshot(config.resource(), clock.getAsLong(), version)));
  }

  @Override
  public void close() {
    if (scheduler != null) {
      scheduler.shutdown();
      try {
        scheduler.awaitTermination(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
    flushSafely();
    exportMetricsSafely();
  }

  private void flushSafely() {
    try {
      flush();
    } catch (RuntimeException e) {
      warn("OpenTelemetry export failed: " + e.getMessage());
    }
  }

  private void exportMetricsSafely() {
    try {
      exportMetrics();
    } catch (RuntimeException e) {
      warn("OpenTelemetry metrics export failed: " + e.getMessage());
    }
  }

  private void post(String url, String body) {
    try {
      HttpSender.Raw response = http.send(URI.create(url), "POST", headers, body, TIMEOUT, 64 * 1024);
      if (response.status() >= 300) warn("The OpenTelemetry collector at " + url + " answered " + response.status());
    } catch (Exception e) {
      warn("Couldn't reach the OpenTelemetry collector at " + url + ": " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
    }
  }

  private void warn(String message) {
    long now = clock.getAsLong();
    if (now - lastWarning < WARN_EVERY_MS) return;
    lastWarning = now;
    LOG.warn(message);
  }

  private static OtlpJson.Request request(Project project, HistoryEntry e) {
    String traceparent = e.requestHeaders().stream().filter(h -> h.name().equalsIgnoreCase("traceparent")).map(Header::value).findFirst().orElse(null);
    OtlpJson.Trace parent = OtlpJson.parent(traceparent);
    String url = e.url() == null ? "" : e.url();
    int q = url.indexOf('?');
    return new OtlpJson.Request(e.at(), e.durationMs(), e.method(), q < 0 ? url : url.substring(0, q), q < 0 ? "" : url.substring(q + 1), e.status(),
        e.clientIp(), project.id(), project.slug(), e.stubId(), e.stubName(), e.matched(),
        parent == null ? hex(16) : parent.traceId(), hex(8), parent == null ? null : parent.parentSpanId());
  }

  private static String hex(int bytes) {
    StringBuilder out = new StringBuilder();
    ThreadLocalRandom random = ThreadLocalRandom.current();
    for (int i = 0; i < bytes; i++) out.append(String.format("%02x", random.nextInt(256)));
    if (out.chars().allMatch(c -> c == '0')) out.setCharAt(out.length() - 1, '1');
    return out.toString();
  }

  private static String or(String url) {
    return url.isEmpty() ? "(off)" : url;
  }
}
