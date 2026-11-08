package io.mockhub.bench;

import java.util.Arrays;
import java.util.List;

final class LatencyRecorder {
  private long[] values = new long[4096];
  private int size;

  void record(long nanos) {
    if (size == values.length) values = Arrays.copyOf(values, size * 2);
    values[size++] = nanos;
  }

  int size() {
    return size;
  }

  static Latency summarise(List<LatencyRecorder> recorders) {
    int total = recorders.stream().mapToInt(LatencyRecorder::size).sum();
    long[] all = new long[total];
    int at = 0;
    for (LatencyRecorder r : recorders) {
      System.arraycopy(r.values, 0, all, at, r.size);
      at += r.size;
    }
    Arrays.sort(all);
    if (total == 0) return new Latency(0, 0, 0, 0, 0, 0, 0);
    double sum = 0;
    for (long v : all) sum += v;
    return new Latency(total, micros(sum / total), micros(percentile(all, 50)), micros(percentile(all, 90)),
        micros(percentile(all, 99)), micros(percentile(all, 99.9)), micros(all[total - 1]));
  }

  static long percentile(long[] sorted, double p) {
    int rank = (int) Math.ceil(p * sorted.length / 100.0 - 1e-9);
    return sorted[Math.max(0, Math.min(sorted.length - 1, rank - 1))];
  }

  private static double micros(double nanos) {
    return Math.round(nanos / 100.0) / 10.0;
  }
}
