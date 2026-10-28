package io.mockhub.audit;

import java.util.List;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LoggingAuditLog implements AuditLog {
  private static final Logger LOG = LoggerFactory.getLogger("mockhub.audit");

  private final AuditLog delegate;

  public LoggingAuditLog(AuditLog delegate) {
    this.delegate = delegate;
  }

  @Override
  public void append(AuditEntry entry) {
    delegate.append(entry);
    try {
      LOG.info("{}: {} {}", entry.actor(), entry.summary(), entry.toJsonLine());
    } catch (Exception e) {
      LOG.info("{}: {}", entry.actor(), entry.summary());
    }
  }

  @Override
  public List<AuditEntry> recent(Predicate<AuditEntry> filter, int limit) {
    return delegate.recent(filter, limit);
  }
}
