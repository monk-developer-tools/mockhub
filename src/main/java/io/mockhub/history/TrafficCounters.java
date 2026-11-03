package io.mockhub.history;

import com.fasterxml.jackson.core.type.TypeReference;
import io.mockhub.api.ApiException;
import io.mockhub.api.Jsons;
import io.mockhub.project.Project;
import io.mockhub.project.ProjectListener;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

public final class TrafficCounters implements ProjectListener {
  static final int SECONDS = 3_600;
  static final int MINUTES = 1_440;
  static final int DAYS = 30;
  private static final Map<String, int[]> WINDOWS = Map.of(
      "5m", new int[] {300, 1}, "15m", new int[] {900, 5}, "1h", new int[] {3_600, 10},
      "6h", new int[] {21_600, 60}, "24h", new int[] {86_400, 300});

  public record Point(long at, int requests, int unmatched) {}

  public record View(String window, int bucketSeconds, List<Point> points, long total, long unmatched, double averageRps,
                     int peakRps, Long firstAt, Long lastAt) {}

  public record Day(String date, long requests, long unmatched) {}

  private final Path root;
  private final Clock clock;
  private final Map<String, Series> series = new ConcurrentHashMap<>();

  public TrafficCounters(Path root, Clock clock) {
    this.root = root;
    this.clock = clock;
  }

  public void record(String projectId, long atMillis, boolean matched) {
    series.computeIfAbsent(projectId, this::load).record(atMillis / 1000, matched);
  }

  public View view(String projectId, String window) {
    int[] spec = WINDOWS.get(window == null ? "15m" : window);
    if (spec == null) throw ApiException.badRequest("The window must be 5m, 15m, 1h, 6h or 24h");
    long now = Instant.now(clock).getEpochSecond();
    return series.computeIfAbsent(projectId, this::load).view(window == null ? "15m" : window, now, spec[0], spec[1]);
  }

  public List<Day> daily(String projectId) {
    return series.computeIfAbsent(projectId, this::load).days(LocalDate.ofInstant(Instant.now(clock), ZoneOffset.UTC));
  }

  public void save() {
    series.forEach((id, s) -> s.save(file(id)));
  }

  @Override
  public void onProjectDeleted(Project project) {
    series.remove(project.id());
    NdjsonSegments.deleteQuietly(file(project.id()));
  }

  private Path file(String projectId) {
    return root.resolve(projectId).resolve("daily.json");
  }

  private Series load(String projectId) {
    Series s = new Series();
    Path f = file(projectId);
    if (Files.exists(f)) {
      try {
        Map<String, long[]> days = Jsons.MAPPER.readValue(f.toFile(), new TypeReference<TreeMap<String, long[]>>() {});
        s.days.putAll(days);
      } catch (IOException ignored) {
      }
    }
    return s;
  }

  private static final class Series {
    private final int[] secRequests = new int[SECONDS];
    private final int[] secUnmatched = new int[SECONDS];
    private final long[] secStamp = new long[SECONDS];
    private final int[] minRequests = new int[MINUTES];
    private final int[] minUnmatched = new int[MINUTES];
    private final int[] minPeak = new int[MINUTES];
    private final long[] minStamp = new long[MINUTES];
    private final TreeMap<String, long[]> days = new TreeMap<>();
    private boolean dirty;

    synchronized void record(long second, boolean matched) {
      int s = (int) Math.floorMod(second, SECONDS);
      if (secStamp[s] != second) {
        secStamp[s] = second;
        secRequests[s] = 0;
        secUnmatched[s] = 0;
      }
      secRequests[s]++;
      if (!matched) secUnmatched[s]++;
      long minute = Math.floorDiv(second, 60);
      int m = (int) Math.floorMod(minute, MINUTES);
      if (minStamp[m] != minute) {
        minStamp[m] = minute;
        minRequests[m] = 0;
        minUnmatched[m] = 0;
        minPeak[m] = 0;
      }
      minRequests[m]++;
      if (!matched) minUnmatched[m]++;
      minPeak[m] = Math.max(minPeak[m], secRequests[s]);
      long[] day = days.computeIfAbsent(LocalDate.ofEpochDay(Math.floorDiv(second, 86_400)).toString(), k -> new long[2]);
      day[0]++;
      if (!matched) day[1]++;
      dirty = true;
    }

    synchronized View view(String window, long now, int span, int bucket) {
      boolean fine = span <= SECONDS;
      int count = span / bucket;
      long start = (Math.floorDiv(now, bucket) + 1) * bucket - span;
      List<Point> points = new ArrayList<>(count);
      long total = 0;
      long unmatched = 0;
      int peak = 0;
      Long first = null;
      Long last = null;
      for (int b = 0; b < count; b++) {
        long bucketStart = start + (long) b * bucket;
        int requests = 0;
        int missed = 0;
        if (fine) {
          for (long sec = bucketStart; sec < bucketStart + bucket; sec++) {
            int i = (int) Math.floorMod(sec, SECONDS);
            if (secStamp[i] != sec) continue;
            requests += secRequests[i];
            missed += secUnmatched[i];
            peak = Math.max(peak, secRequests[i]);
          }
        } else {
          for (long minute = Math.floorDiv(bucketStart, 60); minute < Math.floorDiv(bucketStart + bucket, 60); minute++) {
            int i = (int) Math.floorMod(minute, MINUTES);
            if (minStamp[i] != minute) continue;
            requests += minRequests[i];
            missed += minUnmatched[i];
            peak = Math.max(peak, minPeak[i]);
          }
        }
        if (requests > 0) {
          if (first == null) first = bucketStart * 1000;
          last = (bucketStart + bucket) * 1000;
        }
        total += requests;
        unmatched += missed;
        points.add(new Point(bucketStart * 1000, requests, missed));
      }
      double active = first == null ? 0 : Math.max(1, (last - first) / 1000.0);
      double average = first == null ? 0 : Math.round(total / active * 10) / 10.0;
      return new View(window, bucket, points, total, unmatched, average, peak, first, last);
    }

    synchronized List<Day> days(LocalDate today) {
      List<Day> out = new ArrayList<>();
      for (int i = DAYS - 1; i >= 0; i--) {
        String date = today.minusDays(i).toString();
        long[] d = days.getOrDefault(date, new long[2]);
        out.add(new Day(date, d[0], d[1]));
      }
      return out;
    }

    void save(Path file) {
      TreeMap<String, long[]> copy;
      synchronized (this) {
        if (!dirty) return;
        dirty = false;
        String oldest = LocalDate.now(ZoneOffset.UTC).minusDays(DAYS).toString();
        days.headMap(oldest).clear();
        copy = new TreeMap<>(days);
      }
      try {
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling("daily.json.tmp");
        Files.writeString(tmp, Jsons.write(copy));
        Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
      } catch (IOException ignored) {
      }
    }
  }
}
