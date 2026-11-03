package io.mockhub.callout;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

public final class InMemoryCalloutLog implements CalloutLog {
  private final int capacity;
  private final Deque<CalloutEntry> entries = new ArrayDeque<>();

  public InMemoryCalloutLog(int capacity) {
    this.capacity = Math.max(1, capacity);
  }

  @Override
  public synchronized void add(CalloutEntry entry) {
    entries.addFirst(CalloutLog.capped(entry));
    while (entries.size() > capacity) entries.removeLast();
  }

  @Override
  public synchronized List<CalloutEntry> list(String projectId, int limit) {
    List<CalloutEntry> out = new ArrayList<>();
    for (CalloutEntry e : entries) {
      if (out.size() >= Math.max(1, limit)) break;
      if (Objects.equals(projectId, e.projectId())) out.add(e);
    }
    return out;
  }

  @Override
  public synchronized void clear(String projectId) {
    entries.removeIf(e -> Objects.equals(projectId, e.projectId()));
  }
}
