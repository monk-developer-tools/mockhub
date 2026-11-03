package io.mockhub.history;

import io.mockhub.callout.CalloutEntry;
import io.mockhub.callout.CalloutLog;
import java.util.List;

public final class HistoryCalloutLog implements CalloutLog {
  private final HistoryStore store;

  public HistoryCalloutLog(HistoryStore store) {
    this.store = store;
  }

  @Override
  public void add(CalloutEntry entry) {
    store.record(CalloutLog.capped(entry));
  }

  @Override
  public List<CalloutEntry> list(String projectId, int limit) {
    return store.callouts(projectId, limit, null);
  }

  @Override
  public void clear(String projectId) {
    store.clearCallouts(projectId);
  }
}
