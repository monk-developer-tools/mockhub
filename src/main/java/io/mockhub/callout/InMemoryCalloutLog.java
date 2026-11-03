package io.mockhub.callout;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

public final class InMemoryCalloutLog implements CalloutLog {
  static final int MAX_STORED_BODY = 64 * 1024;

  private final int capacity;
  private final Deque<CalloutEntry> entries = new ArrayDeque<>();

  public InMemoryCalloutLog(int capacity) {
    this.capacity = Math.max(1, capacity);
  }

  @Override
  public synchronized void add(CalloutEntry entry) {
    entries.addFirst(stored(entry));
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

  private static CalloutEntry stored(CalloutEntry e) {
    OutboundRequest r = e.request();
    OutboundRequest request = r == null ? null : new OutboundRequest(r.method(), r.url(), r.headers(), cap(r.body()));
    CalloutEntry.Result s = e.response();
    CalloutEntry.Result response = s == null ? null
        : new CalloutEntry.Result(s.status(), s.headers(), cap(s.body()), s.truncated() || s.body().length() > MAX_STORED_BODY);
    return new CalloutEntry(e.id(), e.at(), e.projectId(), e.stubId(), e.stubName(), e.mode(), e.trigger(), request, response, e.error(), e.durationMs());
  }

  private static String cap(String body) {
    return body == null || body.length() <= MAX_STORED_BODY ? body : body.substring(0, MAX_STORED_BODY);
  }
}
