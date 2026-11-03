package io.mockhub.callout;

import java.util.List;

public interface CalloutLog {
  int MAX_STORED_BODY = 64 * 1024;

  void add(CalloutEntry entry);

  List<CalloutEntry> list(String projectId, int limit);

  void clear(String projectId);

  static CalloutEntry capped(CalloutEntry e) {
    OutboundRequest r = e.request();
    OutboundRequest request = r == null ? null : new OutboundRequest(r.method(), r.url(), r.headers(), cap(r.body()));
    CalloutEntry.Result s = e.response();
    CalloutEntry.Result response = s == null ? null
        : new CalloutEntry.Result(s.status(), s.headers(), cap(s.body()), s.truncated() || s.body().length() > MAX_STORED_BODY);
    return new CalloutEntry(e.id(), e.at(), e.projectId(), e.stubId(), e.stubName(), e.mode(), e.trigger(), request, response,
        e.error(), e.durationMs(), e.requestId());
  }

  private static String cap(String body) {
    return body == null || body.length() <= MAX_STORED_BODY ? body : body.substring(0, MAX_STORED_BODY);
  }
}
