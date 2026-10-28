package io.mockhub.audit;

import static org.assertj.core.api.Assertions.assertThat;

import io.mockhub.support.MutableClock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileAuditLogTest {
  private static final Duration RETENTION = Duration.ofDays(90);

  @TempDir
  Path dataDir;

  private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));

  private static AuditEntry entry(String id, Clock clock) {
    return new AuditEntry(id, Instant.now(clock).toString(), "u1", "Sam", "deleted", "stub", "GET /orders", "", "org1", "team1", "p1", "Shop");
  }

  private Path file() {
    return dataDir.resolve("mockhub").resolve("audit.jsonl");
  }

  @Test
  void returnsNewestFirstAndKeepsEntriesAcrossRestarts() {
    // Given
    FileAuditLog log = new FileAuditLog(dataDir, RETENTION, clock);
    log.append(entry("a", clock));
    clock.advance(Duration.ofMinutes(1));
    log.append(entry("b", clock));

    // When
    List<AuditEntry> reloaded = new FileAuditLog(dataDir, RETENTION, clock).recent(e -> true, 10);

    // Then
    assertThat(reloaded).extracting(AuditEntry::id).containsExactly("b", "a");
  }

  @Test
  void filtersAndLimits() {
    // Given
    FileAuditLog log = new FileAuditLog(dataDir, RETENTION, clock);
    for (int i = 0; i < 5; i++) log.append(entry("e" + i, clock));

    // When
    List<AuditEntry> page = log.recent(e -> !e.id().equals("e4"), 2);

    // Then
    assertThat(page).extracting(AuditEntry::id).containsExactly("e3", "e2");
  }

  @Test
  void forgetsEntriesOlderThanTheRetentionPeriod() {
    // Given
    FileAuditLog log = new FileAuditLog(dataDir, RETENTION, clock);
    log.append(entry("old", clock));
    clock.advance(Duration.ofDays(60));
    log.append(entry("recent", clock));

    // When
    clock.advance(Duration.ofDays(31));
    List<AuditEntry> visible = log.recent(e -> true, 10);

    // Then
    assertThat(visible).extracting(AuditEntry::id).containsExactly("recent");
  }

  @Test
  void removesExpiredLinesFromDiskOnStartup() throws Exception {
    // Given
    FileAuditLog log = new FileAuditLog(dataDir, RETENTION, clock);
    log.append(entry("old", clock));
    clock.advance(Duration.ofDays(89));
    log.append(entry("recent", clock));
    clock.advance(Duration.ofDays(2));

    // When
    new FileAuditLog(dataDir, RETENTION, clock);

    // Then
    List<String> lines = Files.readAllLines(file());
    assertThat(lines).hasSize(1);
    assertThat(lines.get(0)).contains("\"recent\"").doesNotContain("summary");
  }

  @Test
  void compactsTheFileWhileRunningOnceEnoughEntriesExpire() throws Exception {
    // Given
    FileAuditLog log = new FileAuditLog(dataDir, RETENTION, clock);
    for (int i = 0; i < 600; i++) log.append(entry("old" + i, clock));
    clock.advance(Duration.ofDays(91));

    // When
    log.append(entry("fresh", clock));

    // Then
    assertThat(Files.readAllLines(file())).hasSize(1);
    assertThat(log.recent(e -> true, 10)).extracting(AuditEntry::id).containsExactly("fresh");
  }

  @Test
  void skipsDamagedLines() throws Exception {
    // Given
    Files.createDirectories(file().getParent());
    Files.writeString(file(), "not json\n");
    FileAuditLog log = new FileAuditLog(dataDir, RETENTION, clock);

    // When
    log.append(entry("ok", clock));

    // Then
    assertThat(log.recent(e -> true, 10)).extracting(AuditEntry::id).containsExactly("ok");
  }
}
