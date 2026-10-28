package io.mockhub.controller;

import io.mockhub.api.ApiRouter;
import io.mockhub.api.Ctx;
import io.mockhub.audit.AuditEntry;
import io.mockhub.audit.AuditLog;
import io.mockhub.auth.Principal;
import io.mockhub.auth.Role;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

public final class AuditController implements ApiController {
  private static final int MAX_LIMIT = 1000;

  private final AuditLog log;
  private final int retentionDays;

  public AuditController(AuditLog log, int retentionDays) {
    this.log = log;
    this.retentionDays = retentionDays;
  }

  @Override
  public void register(ApiRouter r) {
    r.get("/audit", ctx -> {
      Principal p = ctx.principal();
      Predicate<AuditEntry> visible = e -> p.isAdmin() || canManage(p, e);
      return page(ctx, visible.and(matching(ctx, "orgId", AuditEntry::orgId)).and(matching(ctx, "projectId", AuditEntry::projectId))
          .and(matching(ctx, "actorId", AuditEntry::actorId)));
    });
    r.get("/projects/{id}/audit", ctx -> {
      String id = ctx.path("id");
      return page(ctx, e -> id.equals(e.projectId()));
    });
  }

  private Map<String, Object> page(Ctx ctx, Predicate<AuditEntry> filter) {
    int limit = Math.max(1, Math.min(MAX_LIMIT, Integer.parseInt(ctx.query("limit", "200"))));
    return Map.of("retentionDays", retentionDays, "entries", log.recent(filter, limit));
  }

  private static Predicate<AuditEntry> matching(Ctx ctx, String param, Function<AuditEntry, String> field) {
    String wanted = ctx.query(param);
    return wanted == null || wanted.isBlank() ? e -> true : e -> wanted.equals(field.apply(e));
  }

  private static boolean canManage(Principal p, AuditEntry e) {
    if (e.orgId() == null) return false;
    if (p.can(e.orgId(), Role.MANAGER)) return true;
    return e.teamId() != null && p.canInTeam(e.orgId(), e.teamId(), Role.MANAGER);
  }
}
