package io.mockhub.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.http.RequestMethod;
import io.mockhub.api.ChangeAudit;
import io.mockhub.api.Jsons;
import io.mockhub.auth.Principal;
import io.mockhub.project.Project;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class AuditRecorder implements ChangeAudit {

  enum Name { PROJECT, STUB, ORG, TEAM, USER, BODY_NAME, BODY_USERNAME, BODY_RESOURCE, PARAM_FORMAT, NONE }

  record Rule(String action, String target, Name name) {}

  private static final Map<String, Rule> RULES = Map.ofEntries(
      Map.entry("POST /projects", new Rule("created", "project", Name.BODY_NAME)),
      Map.entry("PUT /projects/{id}", new Rule("updated", "project", Name.PROJECT)),
      Map.entry("DELETE /projects/{id}", new Rule("deleted", "project", Name.PROJECT)),
      Map.entry("POST /projects/{id}/stubs", new Rule("created", "stub", Name.BODY_NAME)),
      Map.entry("PUT /projects/{id}/stubs/{stubId}", new Rule("updated", "stub", Name.STUB)),
      Map.entry("DELETE /projects/{id}/stubs/{stubId}", new Rule("deleted", "stub", Name.STUB)),
      Map.entry("PUT /projects/{id}/stubs/{stubId}/enabled", new Rule("switched", "stub", Name.STUB)),
      Map.entry("POST /projects/{id}/stubs/{stubId}/clone", new Rule("duplicated", "stub", Name.STUB)),
      Map.entry("POST /projects/{id}/stubs/{stubId}/move", new Rule("moved", "stub", Name.STUB)),
      Map.entry("POST /projects/{id}/adopt/{stubId}", new Rule("adopted", "stub", Name.NONE)),
      Map.entry("POST /projects/{id}/import/{format}", new Rule("imported", "stubs", Name.PARAM_FORMAT)),
      Map.entry("POST /projects/{id}/import/wiremock-zip", new Rule("imported", "stubs", Name.NONE)),
      Map.entry("POST /projects/{id}/ai/apply", new Rule("applied", "assistant changes", Name.NONE)),
      Map.entry("POST /projects/{id}/crud", new Rule("created", "CRUD resource", Name.BODY_RESOURCE)),
      Map.entry("POST /projects/{id}/data/records", new Rule("added", "data record", Name.NONE)),
      Map.entry("PUT /projects/{id}/data/records", new Rule("updated", "data records", Name.NONE)),
      Map.entry("DELETE /projects/{id}/data/records", new Rule("deleted", "data records", Name.NONE)),
      Map.entry("DELETE /projects/{id}/data", new Rule("cleared", "stored data", Name.NONE)),
      Map.entry("POST /projects/{id}/files", new Rule("uploaded", "file", Name.NONE)),
      Map.entry("DELETE /projects/{id}/files", new Rule("deleted", "file", Name.NONE)),
      Map.entry("DELETE /projects/{id}/requests", new Rule("cleared", "request log", Name.NONE)),
      Map.entry("POST /examples/{id}/install", new Rule("installed", "example", Name.NONE)),
      Map.entry("POST /orgs", new Rule("created", "organisation", Name.BODY_NAME)),
      Map.entry("PUT /orgs/{id}", new Rule("updated", "organisation", Name.ORG)),
      Map.entry("DELETE /orgs/{id}", new Rule("deleted", "organisation", Name.ORG)),
      Map.entry("PUT /orgs/{id}/members/{userId}", new Rule("gave organisation access to", "person", Name.USER)),
      Map.entry("DELETE /orgs/{id}/members/{userId}", new Rule("removed organisation access from", "person", Name.USER)),
      Map.entry("POST /orgs/{id}/teams", new Rule("created", "team", Name.BODY_NAME)),
      Map.entry("PUT /orgs/{id}/teams/{teamId}", new Rule("updated", "team", Name.TEAM)),
      Map.entry("DELETE /orgs/{id}/teams/{teamId}", new Rule("deleted", "team", Name.TEAM)),
      Map.entry("PUT /orgs/{id}/teams/{teamId}/members/{userId}", new Rule("gave team access to", "person", Name.USER)),
      Map.entry("DELETE /orgs/{id}/teams/{teamId}/members/{userId}", new Rule("removed team access from", "person", Name.USER)),
      Map.entry("POST /users", new Rule("created", "account", Name.BODY_USERNAME)),
      Map.entry("PUT /users/{id}", new Rule("updated", "account", Name.USER)),
      Map.entry("DELETE /users/{id}", new Rule("deleted", "account", Name.USER)),
      Map.entry("POST /users/{id}/password", new Rule("reset the password of", "account", Name.USER)),
      Map.entry("PUT /sso", new Rule("updated", "single sign-on settings", Name.NONE)),
      Map.entry("PUT /ai/settings", new Rule("updated", "AI assistant settings", Name.NONE)),
      Map.entry("POST /restore", new Rule("restored", "backup", Name.NONE)));

  private final AuditLog log;
  private final AuditNames names;
  private final Clock clock;

  public AuditRecorder(AuditLog log, AuditNames names, Clock clock) {
    this.log = log;
    this.names = names;
    this.clock = clock;
  }

  @Override
  public boolean covers(RequestMethod method, String route) {
    return RULES.containsKey(method.value() + " " + route);
  }

  @Override
  public Pending begin(Change change) {
    Rule rule = RULES.get(change.method().value() + " " + change.route());
    if (rule == null) return Pending.NONE;
    AuditEntry entry = describe(rule, change);
    return result -> {
      AuditEntry e = createdProject(entry, rule, result);
      log.append(new AuditEntry(UUID.randomUUID().toString(), Instant.now(clock).toString(), e.actorId(), e.actor(),
          e.action(), e.target(), e.targetName(), e.details(), e.orgId(), e.teamId(), e.projectId(), e.projectName()));
    };
  }

  private static AuditEntry createdProject(AuditEntry e, Rule rule, Object result) {
    if (!rule.target().equals("project") || !rule.action().equals("created") || result == null) return e;
    JsonNode node = asJson(result);
    String id = text(node, "id");
    if (id.isEmpty()) return e;
    return new AuditEntry(e.id(), e.at(), e.actorId(), e.actor(), e.action(), e.target(), e.targetName(), e.details(),
        or(text(node, "orgId"), e.orgId()), or(text(node, "teamId"), e.teamId()), id, or(text(node, "name"), e.targetName()));
  }

  private static JsonNode asJson(Object result) {
    try {
      return result instanceof String json ? Jsons.MAPPER.readTree(json) : Jsons.MAPPER.valueToTree(result);
    } catch (Exception e) {
      return Jsons.MAPPER.createObjectNode();
    }
  }

  private static String or(String value, String fallback) {
    return value.isEmpty() ? fallback : value;
  }

  AuditEntry describe(Rule rule, Change change) {
    Principal p = change.principal();
    JsonNode body = change.body();
    Optional<Project> project = change.route().startsWith("/projects/") ? names.project(change.param("id")) : Optional.empty();
    String orgId = project.map(Project::orgId).orElse(change.route().startsWith("/orgs/") ? change.param("id") : null);
    String teamId = project.map(Project::teamId).orElse(change.param("teamId"));
    if (change.route().startsWith("/orgs/{id}/teams/{teamId}") && teamId == null) teamId = change.param("teamId");
    String action = rule.action();
    if (rule.target().equals("stub") && action.equals("switched") && body != null && body.has("enabled")) {
      action = body.get("enabled").asBoolean() ? "turned on" : "turned off";
    }
    return new AuditEntry(null, null, p.id(), label(p), action, rule.target(), name(rule.name(), change, project), details(change),
        orgId, teamId, project.map(Project::id).orElse(null), project.map(Project::name).orElse(null));
  }

  private String name(Name source, Change change, Optional<Project> project) {
    JsonNode body = change.body();
    return switch (source) {
      case PROJECT -> project.map(Project::name).orElse(change.param("id"));
      case STUB -> names.stub(change.param("id"), change.param("stubId")).orElse(change.param("stubId"));
      case ORG -> names.org(change.param("id")).orElse(change.param("id"));
      case TEAM -> names.team(change.param("teamId")).orElse(change.param("teamId"));
      case USER -> names.user(change.param("userId") != null ? change.param("userId") : change.param("id")).orElse("");
      case BODY_NAME -> body == null ? "" : stubLabel(body);
      case BODY_USERNAME -> text(body, "username");
      case BODY_RESOURCE -> text(body, "resource");
      case PARAM_FORMAT -> change.param("format");
      case NONE -> "";
    };
  }

  private static String details(Change change) {
    JsonNode body = change.body();
    String route = change.route();
    if (body == null || !body.isObject()) return route.endsWith("/import/wiremock-zip") ? "WireMock folder" : "";
    if (route.contains("/members/")) return text(body, "role").isEmpty() ? "" : "as " + text(body, "role");
    if (route.equals("/projects/{id}") || route.equals("/orgs/{id}") || route.equals("/orgs/{id}/teams/{teamId}") || route.equals("/users/{id}")) {
      List<String> fields = new ArrayList<>();
      body.fieldNames().forEachRemaining(f -> { if (!f.equals("password")) fields.add(f); });
      return fields.isEmpty() ? "" : "changed " + String.join(", ", fields);
    }
    if (route.endsWith("/stubs/{stubId}/move")) return text(body, "projectId").isEmpty() ? "" : "to project " + text(body, "projectId");
    return "";
  }

  static String stubLabel(JsonNode body) {
    String name = text(body, "name");
    if (!name.isEmpty()) return name;
    JsonNode req = body.path("request");
    String url = firstText(req, "url", "urlPath", "urlPathTemplate", "urlPattern", "urlPathPattern");
    return url.isEmpty() ? "" : (req.path("method").asText("ANY") + " " + url);
  }

  private static String firstText(JsonNode node, String... fields) {
    for (String f : fields) if (node.hasNonNull(f)) return node.get(f).asText();
    return "";
  }

  private static String text(JsonNode body, String field) {
    return body == null ? "" : body.path(field).asText("");
  }

  private static String label(Principal p) {
    String display = p.user().displayName();
    return display == null || display.isBlank() ? p.username() : display;
  }
}
