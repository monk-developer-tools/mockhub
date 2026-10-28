package io.mockhub.audit;

import io.mockhub.api.Jsons;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;

public final class FileAuditLog implements AuditLog {
  private static final int MIN_STALE_LINES = 500;

  private final Path file;
  private final Duration retention;
  private final Clock clock;
  private final Deque<AuditEntry> entries = new ArrayDeque<>();
  private int linesOnDisk;

  public FileAuditLog(Path dataDir, Duration retention, Clock clock) {
    this.file = dataDir.resolve("mockhub").resolve("audit.jsonl");
    this.retention = retention;
    this.clock = clock;
    load();
  }

  @Override
  public synchronized void append(AuditEntry entry) {
    entries.addLast(entry);
    try {
      Files.createDirectories(file.getParent());
      Files.writeString(file, entry.toJsonLine() + "\n", StandardCharsets.UTF_8,
          StandardOpenOption.CREATE, StandardOpenOption.APPEND);
      linesOnDisk++;
      pruneAndCompactIfStale();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public synchronized List<AuditEntry> recent(Predicate<AuditEntry> filter, int limit) {
    dropExpired();
    List<AuditEntry> out = new ArrayList<>();
    Iterator<AuditEntry> newestFirst = entries.descendingIterator();
    while (newestFirst.hasNext() && out.size() < limit) {
      AuditEntry e = newestFirst.next();
      if (filter.test(e)) out.add(e);
    }
    return out;
  }

  private void load() {
    if (!Files.isRegularFile(file)) return;
    try {
      for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
        if (line.isBlank()) continue;
        linesOnDisk++;
        try {
          entries.addLast(Jsons.MAPPER.readValue(line, AuditEntry.class));
        } catch (IOException ignored) {
          continue;
        }
      }
      dropExpired();
      if (linesOnDisk > entries.size()) compact();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private void pruneAndCompactIfStale() throws IOException {
    dropExpired();
    if (linesOnDisk - entries.size() >= Math.max(MIN_STALE_LINES, entries.size())) compact();
  }

  private void dropExpired() {
    Instant cutoff = Instant.now(clock).minus(retention);
    while (!entries.isEmpty() && expired(entries.peekFirst(), cutoff)) entries.removeFirst();
  }

  private static boolean expired(AuditEntry entry, Instant cutoff) {
    try {
      return Instant.parse(entry.at()).isBefore(cutoff);
    } catch (DateTimeParseException | NullPointerException e) {
      return true;
    }
  }

  private void compact() throws IOException {
    StringBuilder sb = new StringBuilder();
    for (AuditEntry e : entries) sb.append(e.toJsonLine()).append('\n');
    Path tmp = file.resolveSibling("audit.jsonl.tmp");
    Files.writeString(tmp, sb.toString(), StandardCharsets.UTF_8);
    Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    linesOnDisk = entries.size();
  }
}
