package io.mockhub.publictools;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

interface BinScheduler {
  int MAX_PER_BIN = 20;
  int MAX_WAITING = 500;

  boolean later(String binId, long delayMs, Runnable job);

  static BinScheduler pooled(int threads) {
    return new Pooled(threads);
  }

  final class Pooled implements BinScheduler {
    private final ScheduledExecutorService pool;
    private final Map<String, AtomicInteger> perBin = new ConcurrentHashMap<>();
    private final AtomicInteger waiting = new AtomicInteger();

    Pooled(int threads) {
      this.pool = Executors.newScheduledThreadPool(threads, r -> {
        Thread t = new Thread(r, "bin-callouts");
        t.setDaemon(true);
        return t;
      });
    }

    @Override
    public boolean later(String binId, long delayMs, Runnable job) {
      AtomicInteger mine = perBin.computeIfAbsent(binId, k -> new AtomicInteger());
      if (waiting.incrementAndGet() > MAX_WAITING | mine.incrementAndGet() > MAX_PER_BIN) {
        release(binId, mine);
        return false;
      }
      try {
        pool.schedule(() -> {
          try {
            job.run();
          } finally {
            release(binId, mine);
          }
        }, delayMs, TimeUnit.MILLISECONDS);
        return true;
      } catch (RejectedExecutionException e) {
        release(binId, mine);
        return false;
      }
    }

    private void release(String binId, AtomicInteger mine) {
      waiting.decrementAndGet();
      if (mine.decrementAndGet() <= 0) perBin.remove(binId, mine);
    }
  }
}
