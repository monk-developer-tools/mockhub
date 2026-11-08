package io.mockhub.bench;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.LongAdder;

final class LoadRun {
  record Result(String scenario, String title, int connections, double seconds, long requests, long errors,
      double requestsPerSecond, double megabytesPerSecond, Latency latency, List<Double> timeline, String firstError) {}

  private record Worker(LatencyRecorder recorder, long errors, long bytes, String firstError) {}

  private final String host;
  private final int port;

  LoadRun(String host, int port) {
    this.host = host;
    this.port = port;
  }

  Result run(Scenario scenario, Duration warmup, Duration measure, Duration interval) throws InterruptedException {
    long start = System.nanoTime();
    long measureFrom = start + warmup.toNanos();
    long end = measureFrom + measure.toNanos();
    LongAdder served = new LongAdder();
    ExecutorService pool = Executors.newFixedThreadPool(scenario.connections());
    List<Future<Worker>> futures = new ArrayList<>();
    SplittableRandom seeds = new SplittableRandom(42);
    for (int i = 0; i < scenario.connections(); i++) {
      SplittableRandom random = seeds.split();
      futures.add(pool.submit(() -> work(scenario, random, measureFrom, end, served)));
    }
    List<Double> timeline = timeline(measureFrom, end, interval, served);
    pool.shutdown();
    List<LatencyRecorder> recorders = new ArrayList<>();
    long errors = 0;
    long bytes = 0;
    String firstError = null;
    for (Future<Worker> f : futures) {
      Worker w;
      try {
        w = f.get();
      } catch (ExecutionException e) {
        throw new IllegalStateException("A load worker failed", e.getCause());
      }
      recorders.add(w.recorder());
      errors += w.errors();
      bytes += w.bytes();
      if (firstError == null) firstError = w.firstError();
    }
    Latency latency = LatencyRecorder.summarise(recorders);
    double seconds = measure.toNanos() / 1e9;
    return new Result(scenario.name(), scenario.title(), scenario.connections(), seconds, latency.count(), errors,
        round(latency.count() / seconds), round(bytes / seconds / (1024 * 1024)), latency, timeline, firstError);
  }

  private List<Double> timeline(long measureFrom, long end, Duration interval, LongAdder served) throws InterruptedException {
    sleepUntil(measureFrom);
    List<Double> points = new ArrayList<>();
    long step = Math.min(interval.toNanos(), end - measureFrom);
    if (step <= 0) return points;
    for (long tick = measureFrom + step; tick <= end + step / 2; tick += step) {
      long at = Math.min(tick, end);
      sleepUntil(at);
      long window = at - (tick - step);
      if (window > 0) points.add(round(served.sumThenReset() / (window / 1e9)));
    }
    return points;
  }

  private Worker work(Scenario scenario, SplittableRandom random, long measureFrom, long end, LongAdder served) {
    LatencyRecorder recorder = new LatencyRecorder();
    long errors = 0;
    long bytes = 0;
    String firstError = null;
    RawHttpConnection connection = null;
    long now = System.nanoTime();
    while (now < end) {
      byte[] request = scenario.source().next(random);
      try {
        if (connection == null) connection = new RawHttpConnection(host, port);
        long sent = System.nanoTime();
        RawHttpConnection.Response response = connection.exchange(request);
        now = System.nanoTime();
        boolean measured = sent >= measureFrom && now <= end;
        if (response.status() == scenario.expectedStatus()) {
          if (measured) {
            recorder.record(now - sent);
            bytes += response.bodyBytes();
            served.increment();
          }
        } else if (measured) {
          errors++;
          if (firstError == null) firstError = "HTTP " + response.status();
        }
        if (response.close()) {
          connection.close();
          connection = null;
        }
      } catch (IOException e) {
        now = System.nanoTime();
        if (now >= measureFrom) {
          errors++;
          if (firstError == null) firstError = e.getClass().getSimpleName() + ": " + e.getMessage();
        }
        if (connection != null) connection.close();
        connection = null;
        pause();
      }
    }
    if (connection != null) connection.close();
    return new Worker(recorder, errors, bytes, firstError);
  }

  private static void pause() {
    try {
      Thread.sleep(20);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private static void sleepUntil(long nanoTime) throws InterruptedException {
    long left = nanoTime - System.nanoTime();
    if (left > 0) Thread.sleep(left / 1_000_000, (int) (left % 1_000_000));
  }

  private static double round(double value) {
    return Math.round(value * 10.0) / 10.0;
  }
}
