package io.mockhub.audit;

import java.util.List;
import java.util.function.Predicate;

public interface AuditLog {
  void append(AuditEntry entry);

  List<AuditEntry> recent(Predicate<AuditEntry> filter, int limit);
}
