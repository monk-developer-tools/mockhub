package io.mockhub.ha;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

public final class LeaderElection {

  @FunctionalInterface
  public interface Sleeper {
    void sleep(long millis) throws InterruptedException;
  }

  public static final class NotLeader extends RuntimeException {
    public NotLeader(String message) {
      super(message);
    }
  }

  private final Function<Path, Optional<LeaderLease>> locks;
  private final Path lockFile;
  private final HandoverRequests handovers;
  private final Clock clock;
  private final Sleeper sleeper;
  private final Consumer<String> log;
  private final String self = UUID.randomUUID().toString();

  public LeaderElection(Function<Path, Optional<LeaderLease>> locks, Path lockFile, HandoverRequests handovers, Clock clock, Sleeper sleeper,
      Consumer<String> log) {
    this.locks = locks;
    this.lockFile = lockFile;
    this.handovers = handovers;
    this.clock = clock;
    this.sleeper = sleeper;
    this.log = log;
  }

  public static LeaderElection inDataDir(Path dataDir, Consumer<String> log) {
    Path dir = dataDir.resolve("mockhub");
    return new LeaderElection(LeaderLease::tryAcquire, dir.resolve("leader.lock"), new HandoverRequests(dir.resolve("handover.json")),
        Clock.systemUTC(), Thread::sleep, log);
  }

  public String self() {
    return self;
  }

  public LeaderLease acquire(Duration maxWait) throws InterruptedException {
    Optional<LeaderLease> lease = locks.apply(lockFile);
    if (lease.isPresent()) return lease.get();
    long started = clock.millis();
    log.accept("Another MockHub holds " + lockFile + ". Asking it to hand over and waiting up to " + maxWait.toSeconds() + " seconds.");
    handovers.request(self, started);
    while (clock.millis() - started < maxWait.toMillis()) {
      sleeper.sleep(250);
      lease = locks.apply(lockFile);
      if (lease.isPresent()) {
        log.accept("Took over after " + (clock.millis() - started) + " ms.");
        return lease.get();
      }
    }
    throw new NotLeader("The other MockHub did not hand over within " + maxWait.toSeconds()
        + " seconds. If it runs a version without MOCKHUB_HA, stop it first.");
  }

  public ScheduledExecutorService watch(long sinceMillis, Runnable stepDown) {
    ScheduledExecutorService watcher = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "mockhub-handover");
      t.setDaemon(true);
      return t;
    });
    watcher.scheduleWithFixedDelay(() -> {
      if (handovers.requestedAfter(sinceMillis, self)) {
        log.accept("A new MockHub asked to take over. Stopping so it can start.");
        watcher.shutdown();
        stepDown.run();
      }
    }, 500, 500, TimeUnit.MILLISECONDS);
    return watcher;
  }
}
