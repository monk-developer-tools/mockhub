package io.mockhub.history;

import io.mockhub.api.Jsons;
import io.mockhub.callout.CalloutEntry;
import io.mockhub.project.Project;
import io.mockhub.project.ProjectListener;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

public final class HistoryStore implements ProjectListener, AutoCloseable {
  static final String REQUESTS = "requests";
  static final String CALLOUTS = "callouts";
  static final int QUEUE_SIZE = 20_000;
  static final int MAX_SCAN = 200_000;
  private static final int BATCH = 2_000;
  private static final System.Logger LOG = System.getLogger(HistoryStore.class.getName());
  private static final com.fasterxml.jackson.databind.ObjectWriter LINE =
      Jsons.MAPPER.writer().without(com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT);

  private final Path root;
  private final Clock clock;
  private final Duration retention;
  private final long maxBytesPerProject;
  private final NdjsonSegments<HistoryEntry> requests;
  private final NdjsonSegments<CalloutEntry> callouts;
  private final BlockingQueue<Object> queue = new LinkedBlockingQueue<>(QUEUE_SIZE);
  private final AtomicLong dropped = new AtomicLong();
  private final Thread writer;
  private volatile boolean running = true;

  private record Pending(String projectId, String kind, long at, String json) {}

  private record Flush(CountDownLatch done) {}

  public HistoryStore(Path root, Clock clock, Duration retention, long maxBytesPerProject) {
    this.root = root;
    this.clock = clock;
    this.retention = retention;
    this.maxBytesPerProject = maxBytesPerProject;
    this.requests = new NdjsonSegments<>(root, REQUESTS, HistoryEntry.class);
    this.callouts = new NdjsonSegments<>(root, CALLOUTS, CalloutEntry.class);
    this.writer = new Thread(this::writeLoop, "mockhub-history");
    this.writer.setDaemon(true);
    this.writer.start();
  }

  public void record(HistoryEntry entry) {
    if (entry.projectId() == null) return;
    enqueue(new Pending(entry.projectId(), REQUESTS, entry.at(), line(entry)));
  }

  public void record(CalloutEntry entry) {
    if (entry.projectId() == null) return;
    enqueue(new Pending(entry.projectId(), CALLOUTS, millis(entry.at()), line(entry)));
  }

  public long dropped() {
    return dropped.get();
  }

  public HistoryPage search(String projectId, HistoryQuery query) {
    return search(projectId, query, false);
  }

  public HistoryPage search(String projectId, HistoryQuery query, boolean full) {
    flush();
    List<HistoryEntry> items = new ArrayList<>();
    Map<String, Integer> methods = new LinkedHashMap<>();
    Map<String, Integer> statuses = new LinkedHashMap<>();
    Map<String, HistoryPage.StubCount> stubs = new HashMap<>();
    int[] counts = new int[3];
    NdjsonSegments.Scan scan = requests.scanNewestFirst(projectId, query.from(), query.to(), MAX_SCAN, e -> {
      if (!query.matches(e)) return true;
      counts[0]++;
      if (counts[0] > query.offset() && items.size() < query.limit()) items.add(full ? e : e.summary());
      methods.merge(e.method(), 1, Integer::sum);
      statuses.merge(e.statusClass(), 1, Integer::sum);
      if (e.matched()) counts[1]++;
      else counts[2]++;
      if (e.stubId() != null) {
        stubs.merge(e.stubId(), new HistoryPage.StubCount(e.stubId(), e.stubName(), 1),
            (a, b) -> new HistoryPage.StubCount(a.id(), a.name(), a.count() + 1));
      }
      return true;
    });
    List<HistoryPage.StubCount> topStubs = stubs.values().stream()
        .sorted(Comparator.comparingInt(HistoryPage.StubCount::count).reversed()).limit(10).toList();
    return new HistoryPage(items, counts[0], scan.truncated(),
        new HistoryPage.Facets(sorted(methods), sorted(statuses), counts[1], counts[2], topStubs));
  }

  public int each(String projectId, long after, java.util.function.Consumer<HistoryEntry> visitor) {
    flush();
    int[] visited = {0};
    requests.scanNewestFirst(projectId, after + 1, Long.MAX_VALUE, MAX_SCAN, e -> {
      if (e.at() > after) {
        visited[0]++;
        visitor.accept(e);
      }
      return true;
    });
    return visited[0];
  }

  public Optional<HistoryEntry> get(String projectId, String id) {
    flush();
    HistoryEntry[] found = new HistoryEntry[1];
    requests.scanNewestFirst(projectId, 0, Long.MAX_VALUE, MAX_SCAN, e -> {
      if (!id.equals(e.id())) return true;
      found[0] = e;
      return false;
    });
    return Optional.ofNullable(found[0]);
  }

  public List<CalloutEntry> callouts(String projectId, int limit, String requestId) {
    flush();
    List<CalloutEntry> out = new ArrayList<>();
    callouts.scanNewestFirst(projectId, 0, Long.MAX_VALUE, MAX_SCAN, e -> {
      if (requestId == null || requestId.equals(e.requestId())) out.add(e);
      return out.size() < Math.max(1, limit);
    });
    return out;
  }

  public void clearRequests(String projectId) {
    flush();
    requests.delete(projectId);
  }

  public void clearCallouts(String projectId) {
    flush();
    callouts.delete(projectId);
  }

  @Override
  public void onProjectDeleted(Project project) {
    clearRequests(project.id());
    clearCallouts(project.id());
  }

  public void prune() {
    flush();
    long cutoff = Instant.now(clock).minus(retention).toEpochMilli();
    for (Path dir : projectDirs()) {
      List<Path> files = new ArrayList<>(segmentFiles(dir));
      files.removeIf(f -> {
        if (NdjsonSegments.hourOf(f) + 3_600_000L > cutoff) return false;
        NdjsonSegments.deleteQuietly(f);
        return true;
      });
      long total = files.stream().mapToLong(HistoryStore::size).sum();
      files.sort(Comparator.comparing((Path f) -> f.getFileName().toString()));
      for (Path f : files) {
        if (total <= maxBytesPerProject) break;
        total -= size(f);
        NdjsonSegments.deleteQuietly(f);
      }
    }
  }

  public void flush() {
    if (Thread.currentThread() == writer) return;
    CountDownLatch done = new CountDownLatch(1);
    try {
      if (!queue.offer(new Flush(done), 5, TimeUnit.SECONDS)) return;
      done.await(10, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  @Override
  public void close() {
    flush();
    running = false;
    writer.interrupt();
  }

  private void enqueue(Pending pending) {
    if (!queue.offer(pending)) dropped.incrementAndGet();
  }

  private void writeLoop() {
    List<Object> batch = new ArrayList<>(BATCH);
    while (running) {
      try {
        batch.add(queue.take());
        queue.drainTo(batch, BATCH - 1);
        write(batch);
      } catch (InterruptedException e) {
        if (!running) return;
      } catch (RuntimeException e) {
        LOG.log(System.Logger.Level.WARNING, "Couldn't write request history: " + e.getMessage());
      } finally {
        for (Object o : batch) if (o instanceof Flush f) f.done().countDown();
        batch.clear();
      }
    }
  }

  private void write(List<Object> batch) {
    Map<String, List<NdjsonSegments.Line>> requestLines = new LinkedHashMap<>();
    Map<String, List<NdjsonSegments.Line>> calloutLines = new LinkedHashMap<>();
    for (Object o : batch) {
      if (!(o instanceof Pending p)) continue;
      (REQUESTS.equals(p.kind()) ? requestLines : calloutLines)
          .computeIfAbsent(p.projectId(), k -> new ArrayList<>()).add(new NdjsonSegments.Line(p.at(), p.json()));
    }
    requestLines.forEach(requests::append);
    calloutLines.forEach(callouts::append);
  }

  private List<Path> projectDirs() {
    if (!Files.isDirectory(root)) return List.of();
    try (Stream<Path> s = Files.list(root)) {
      return s.filter(Files::isDirectory).toList();
    } catch (IOException e) {
      return List.of();
    }
  }

  private static List<Path> segmentFiles(Path dir) {
    try (Stream<Path> s = Files.list(dir)) {
      return s.filter(p -> p.getFileName().toString().endsWith(".ndjson")).toList();
    } catch (IOException e) {
      return List.of();
    }
  }

  private static long size(Path f) {
    try {
      return Files.size(f);
    } catch (IOException e) {
      return 0;
    }
  }

  private static String line(Object value) {
    try {
      return LINE.writeValueAsString(value);
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  private static long millis(String iso) {
    try {
      return Instant.parse(iso).toEpochMilli();
    } catch (RuntimeException e) {
      return System.currentTimeMillis();
    }
  }

  private static Map<String, Integer> sorted(Map<String, Integer> counts) {
    Map<String, Integer> out = new LinkedHashMap<>();
    counts.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
        .forEach(e -> out.put(e.getKey(), e.getValue()));
    return out;
  }
}
