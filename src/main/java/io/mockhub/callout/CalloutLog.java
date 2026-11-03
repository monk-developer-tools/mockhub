package io.mockhub.callout;

import java.util.List;

public interface CalloutLog {
  void add(CalloutEntry entry);

  List<CalloutEntry> list(String projectId, int limit);

  void clear(String projectId);
}
