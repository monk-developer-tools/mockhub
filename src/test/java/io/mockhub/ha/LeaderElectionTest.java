package io.mockhub.ha;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LeaderElectionTest {

  @TempDir
  Path dir;

  private final AtomicLong now = new AtomicLong(1_000_000);
  private final Clock clock = new Clock() {
    @Override
    public ZoneOffset getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return Instant.ofEpochMilli(now.get());
    }
  };
  private final List<String> log = new ArrayList<>();

  @Test
  void theFirstServerLeadsAtOnce() throws Exception {
    // Given
    Path lock = dir.resolve("leader.lock");
    LeaderElection election = new LeaderElection(LeaderLease::tryAcquire, lock, new HandoverRequests(dir.resolve("handover.json")), clock,
        ms -> now.addAndGet(ms), log::add);

    // When
    try (LeaderLease lease = election.acquire(Duration.ofSeconds(5))) {
      // Then
      assertThat(lease).isNotNull();
      assertThat(log).isEmpty();
      assertThat(dir.resolve("handover.json")).doesNotExist();
    }
  }

  @Test
  void aNewServerAsksTheLeaderToHandOverAndTakesTheLockWhenItIsFree() throws Exception {
    // Given
    HandoverRequests handovers = new HandoverRequests(dir.resolve("handover.json"));
    int[] attempts = {0};
    LeaderElection election = new LeaderElection(path -> ++attempts[0] < 4 ? Optional.empty() : LeaderLease.tryAcquire(path),
        dir.resolve("leader.lock"), handovers, clock, ms -> now.addAndGet(ms), log::add);

    // When
    LeaderLease lease = election.acquire(Duration.ofSeconds(5));

    // Then
    assertThat(handovers.requestedAfter(999_999, "someone-else")).isTrue();
    assertThat(handovers.requestedAfter(1_000_000, "someone-else")).isFalse();
    assertThat(handovers.requestedAfter(999_999, election.self())).isFalse();
    assertThat(log).hasSize(2);
    assertThat(log.get(1)).isEqualTo("Took over after 750 ms.");
    lease.close();
  }

  @Test
  void givesUpWhenTheLeaderNeverHandsOver() {
    // Given
    LeaderElection election = new LeaderElection(path -> Optional.empty(), dir.resolve("leader.lock"), new HandoverRequests(dir.resolve("handover.json")),
        clock, ms -> now.addAndGet(ms), log::add);

    // When / Then
    assertThatThrownBy(() -> election.acquire(Duration.ofSeconds(2))).isInstanceOf(LeaderElection.NotLeader.class)
        .hasMessageContaining("did not hand over within 2 seconds");
  }

  @Test
  void theLeaderStepsDownOnlyForARequestMadeAfterItStarted() throws Exception {
    // Given
    HandoverRequests handovers = new HandoverRequests(dir.resolve("handover.json"));
    handovers.request("old-requester", 500);
    LeaderElection leader = new LeaderElection(LeaderLease::tryAcquire, dir.resolve("leader.lock"), handovers, Clock.systemUTC(), Thread::sleep, log::add);
    CountDownLatch steppedDown = new CountDownLatch(1);
    ScheduledExecutorService watcher = leader.watch(1_000, steppedDown::countDown);

    // When
    boolean early = steppedDown.await(1200, TimeUnit.MILLISECONDS);
    handovers.request("new-server", 2_000);
    boolean late = steppedDown.await(3, TimeUnit.SECONDS);

    // Then
    assertThat(early).isFalse();
    assertThat(late).isTrue();
    assertThat(log).contains("A new MockHub asked to take over. Stopping so it can start.");
    watcher.shutdownNow();
  }

  @Test
  void aLockHeldInThisProcessIsNotHandedOutTwice() {
    // Given
    Path lock = dir.resolve("leader.lock");

    // When
    LeaderLease first = LeaderLease.tryAcquire(lock).orElseThrow();
    boolean secondWhileHeld = LeaderLease.tryAcquire(lock).isPresent();
    first.close();
    Optional<LeaderLease> afterRelease = LeaderLease.tryAcquire(lock);

    // Then
    assertThat(secondWhileHeld).isFalse();
    assertThat(afterRelease).isPresent();
    afterRelease.get().close();
  }
}
