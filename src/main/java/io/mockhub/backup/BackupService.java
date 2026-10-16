package io.mockhub.backup;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import io.mockhub.Main;
import io.mockhub.api.ApiException;
import io.mockhub.api.Jsons;
import io.mockhub.org.Organization;
import io.mockhub.org.OrganizationService;
import io.mockhub.project.Project;
import io.mockhub.project.ProjectService;
import io.mockhub.stub.StubGateway;
import io.mockhub.team.Team;
import io.mockhub.team.TeamService;
import io.mockhub.stub.StubMetadata;
import io.mockhub.stub.StubPaths;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class BackupService {
  public static final String FORMAT = "mockhub-backup";
  public static final int VERSION = 2;

  private final OrganizationService orgs;
  private final TeamService teams;
  private final ProjectService projects;
  private final StubGateway gateway;
  private final Clock clock;

  public BackupService(OrganizationService orgs, TeamService teams, ProjectService projects, StubGateway gateway, Clock clock) {
    this.orgs = orgs;
    this.teams = teams;
    this.projects = projects;
    this.gateway = gateway;
    this.clock = clock;
  }

  public record Renamed(String project, String from, String to) {}

  public record Result(int organizations, int projects, int stubs, List<Renamed> renamed) {}

  private record Contents(List<Organization> organizations, List<Team> teams, List<Project> projects, List<ObjectNode> mappings) {
    Optional<Team> team(String id) {
      return teams.stream().filter(t -> t.id().equals(id)).findFirst();
    }

    List<ObjectNode> mappingsOf(String projectId) {
      return mappings.stream().filter(m -> projectId.equals(projectIdOf(m))).toList();
    }
  }

  public ObjectNode export(BackupSelection selection) {
    if (selection.isEmpty()) throw ApiException.badRequest("Choose at least one organisation or project to back up");
    List<Project> chosen = selection.pick(projects.findAll());
    Set<String> orgIds = new HashSet<>(selection.organizationIds());
    chosen.forEach(p -> orgIds.add(p.orgId()));
    List<Organization> orgList = orgs.findAll().stream().filter(o -> selection.everything() || orgIds.contains(o.id())).toList();
    if (!selection.everything() && orgList.isEmpty() && chosen.isEmpty()) {
      throw ApiException.notFound("The selected organisations and projects");
    }
    Set<String> projectIds = chosen.stream().map(Project::id).collect(Collectors.toSet());
    Set<String> teamIds = chosen.stream().map(Project::teamId).filter(Objects::nonNull).collect(Collectors.toSet());
    Set<String> wholeOrgs = new HashSet<>(selection.organizationIds());
    List<Team> teamList = teams.findAll().stream()
        .filter(t -> selection.everything() || teamIds.contains(t.id()) || wholeOrgs.contains(t.orgId()))
        .toList();

    ObjectNode out = Jsons.object();
    out.put("format", FORMAT).put("version", VERSION).put("mockhubVersion", Main.VERSION)
        .put("createdAt", Instant.now(clock).toString()).put("scope", selection.everything() ? "full" : "selection");
    out.set("organizations", Jsons.MAPPER.valueToTree(orgList));
    out.set("teams", Jsons.MAPPER.valueToTree(teamList));
    out.set("projects", Jsons.MAPPER.valueToTree(chosen));
    ArrayNode mappings = out.putArray("mappings");
    gateway.findAll().stream()
        .filter(s -> selection.everything() || projectIds.contains(StubMetadata.projectId(s)))
        .forEach(s -> mappings.add(Jsons.fromWireMock(s)));
    return out;
  }

  public String fileName(BackupSelection selection) {
    String date = LocalDate.ofInstant(Instant.now(clock), ZoneOffset.UTC).toString();
    if (selection.everything()) return "mockhub-backup-" + date + ".json";
    if (selection.projectIds().isEmpty() && selection.organizationIds().size() == 1) {
      String id = selection.organizationIds().iterator().next();
      return "mockhub-" + orgs.findAll().stream().filter(o -> o.id().equals(id)).map(Organization::slug).findFirst().orElse("selection") + "-" + date + ".json";
    }
    if (selection.organizationIds().isEmpty() && selection.projectIds().size() == 1) {
      return "mockhub-" + projects.find(selection.projectIds().iterator().next()).map(Project::slug).orElse("selection") + "-" + date + ".json";
    }
    return "mockhub-selection-" + date + ".json";
  }

  public ObjectNode preview(JsonNode file) {
    Contents c = read(file);
    Map<String, Organization> known = orgs.findAll().stream().collect(Collectors.toMap(Organization::id, o -> o));
    Map<String, Organization> inFile = new LinkedHashMap<>();
    c.organizations().forEach(o -> inFile.put(o.id(), o));
    c.projects().forEach(p -> inFile.computeIfAbsent(p.orgId(), id -> known.getOrDefault(id,
        new Organization(id, "Unknown organisation", "", "", "", ""))));

    ObjectNode out = Jsons.object();
    out.put("createdAt", file.path("createdAt").asText(""));
    out.put("mockhubVersion", file.path("mockhubVersion").asText(""));
    out.put("scope", file.path("scope").asText("full"));
    ArrayNode orgNodes = out.putArray("organizations");
    for (Organization o : inFile.values()) {
      ObjectNode node = orgNodes.addObject().put("id", o.id()).put("name", o.name()).put("slug", o.slug()).put("exists", known.containsKey(o.id()));
      ArrayNode list = node.putArray("projects");
      List<Project> inOrg = c.projects().stream().filter(p -> Objects.equals(p.orgId(), o.id()))
          .sorted(Comparator.comparing((Project p) -> p.name().toLowerCase())).toList();
      for (Project p : inOrg) {
        ObjectNode pn = list.addObject().put("id", p.id()).put("name", p.name()).put("slug", p.slug())
            .put("stubCount", c.mappingsOf(p.id()).size())
            .put("teamName", c.team(p.teamId()).map(Team::name).orElse(""));
        boolean exists = projects.find(p.id()).isPresent();
        Optional<Project> holder = projectWithSlug(p.slug());
        boolean slugTaken = holder.isPresent() && !holder.get().id().equals(p.id());
        pn.put("status", exists ? "replace" : slugTaken ? "slug-taken" : "new");
        if (slugTaken) pn.put("newSlug", projects.availableSlug(p.slug()));
      }
    }
    Set<String> fileProjectIds = c.projects().stream().map(Project::id).collect(Collectors.toSet());
    out.put("unassignedStubs", (int) c.mappings().stream().filter(m -> !fileProjectIds.contains(projectIdOf(m))).count());
    return out;
  }

  public Result restore(JsonNode file, RestoreOptions options) {
    Contents c = read(file);
    BackupSelection selection = options.selection();
    if (selection.isEmpty()) throw ApiException.badRequest("Choose at least one project to restore");
    String target = options.targetOrgId();
    if (target != null && !orgs.exists(target)) throw ApiException.notFound("Organisation " + target);
    String targetTeam = target == null ? null
        : options.targetTeamId() == null || options.targetTeamId().isBlank() ? teams.ensureDefault(target).id()
        : teams.getInOrg(target, options.targetTeamId()).id();

    List<Project> chosen = selection.pick(c.projects());
    int orgCount = 0;
    if (target == null) {
      Set<String> needed = chosen.stream().map(Project::orgId).collect(Collectors.toSet());
      for (Organization o : c.organizations()) {
        if (selection.everything() || selection.organizationIds().contains(o.id()) || needed.contains(o.id())) {
          orgs.restore(o);
          orgCount++;
        }
      }
    }
    String fallbackOrg = target != null ? target : orgs.ensureDefault().id();
    Set<String> restoredTeams = new HashSet<>();
    if (target == null) {
      Set<String> neededTeams = chosen.stream().map(Project::teamId).filter(Objects::nonNull).collect(Collectors.toSet());
      for (Team t : c.teams()) {
        if (!neededTeams.contains(t.id()) && !(selection.everything() || selection.organizationIds().contains(t.orgId()))) continue;
        if (!orgs.exists(t.orgId())) continue;
        teams.restore(t);
        restoredTeams.add(t.id());
      }
    }

    List<Renamed> renamed = new ArrayList<>();
    int stubCount = 0;
    for (Project p : chosen) {
      String id = options.asCopy() ? UUID.randomUUID().toString().substring(0, 8) : p.id();
      String orgId = target != null ? target : orgs.exists(p.orgId()) ? p.orgId() : fallbackOrg;
      String teamId = targetTeam != null ? targetTeam
          : restoredTeams.contains(p.teamId()) || teams.find(p.teamId()).filter(t -> t.orgId().equals(orgId)).isPresent() ? p.teamId()
          : teams.ensureDefault(orgId).id();
      Optional<Project> holder = projectWithSlug(p.slug());
      String slug = holder.isEmpty() || holder.get().id().equals(id) ? p.slug() : projects.availableSlug(p.slug());
      if (!slug.equals(p.slug())) renamed.add(new Renamed(p.name(), p.basePath(), "/" + slug));
      if (!options.asCopy()) removeStubsOf(id);
      String now = Instant.now(clock).toString();
      Project saved = new Project(id, p.name(), slug, p.description(), p.color(),
          options.asCopy() ? now : p.createdAt(), now, orgId, teamId);
      projects.restore(List.of(saved));
      for (ObjectNode m : c.mappingsOf(p.id())) {
        upsert(rebase(m, p, saved, options.asCopy()));
        stubCount++;
      }
    }
    if (selection.everything() && !options.asCopy()) {
      Set<String> fileProjectIds = c.projects().stream().map(Project::id).collect(Collectors.toSet());
      for (ObjectNode m : c.mappings()) {
        if (fileProjectIds.contains(projectIdOf(m))) continue;
        upsert(m);
        stubCount++;
      }
    }
    projects.assignOrphans(orgs.ensureDefault().id());
    projects.assignTeams(org -> teams.ensureDefault(org).id());
    return new Result(orgCount, chosen.size(), stubCount, renamed);
  }

  private Contents read(JsonNode file) {
    if (file == null || !file.isObject() || !file.path("projects").isArray()) {
      throw ApiException.badRequest("This file isn't a MockHub backup");
    }
    if (file.has("format") && !FORMAT.equals(file.path("format").asText())) throw ApiException.badRequest("This file isn't a MockHub backup");
    List<Organization> orgList = Jsons.MAPPER.convertValue(file.path("organizations").isArray() ? file.get("organizations") : Jsons.MAPPER.createArrayNode(),
        new TypeReference<List<Organization>>() {});
    List<Project> projectList = Jsons.MAPPER.convertValue(file.get("projects"), new TypeReference<List<Project>>() {});
    List<Team> teamList = Jsons.MAPPER.convertValue(file.path("teams").isArray() ? file.get("teams") : Jsons.MAPPER.createArrayNode(),
        new TypeReference<List<Team>>() {});
    List<ObjectNode> mappings = new ArrayList<>();
    file.path("mappings").forEach(m -> {
      if (m.isObject()) mappings.add((ObjectNode) m);
    });
    return new Contents(orgList, teamList, projectList.stream().filter(p -> p != null && p.id() != null && p.slug() != null).toList(), mappings);
  }

  private Optional<Project> projectWithSlug(String slug) {
    return projects.findAll().stream().filter(p -> p.slug().equals(slug)).findFirst();
  }

  private void removeStubsOf(String projectId) {
    gateway.findAll().stream().filter(s -> projectId.equals(StubMetadata.projectId(s))).forEach(s -> gateway.remove(s.getId()));
  }

  private static ObjectNode rebase(ObjectNode mapping, Project from, Project to, boolean newId) {
    ObjectNode m = mapping.deepCopy();
    if (!from.slug().equals(to.slug())) {
      StubPaths.toRelative(m, from.basePath());
      StubPaths.toAbsolute(m, to.basePath());
    }
    StubMetadata.node(m).put(StubMetadata.PROJECT_ID, to.id());
    if (newId) {
      String id = UUID.randomUUID().toString();
      m.put("id", id).put("uuid", id);
    }
    return m;
  }

  private void upsert(ObjectNode mapping) {
    StubMapping stub = Jsons.toWireMock(mapping, StubMapping.class);
    if (gateway.findById(stub.getId()).isPresent()) gateway.update(stub);
    else gateway.add(stub);
  }

  private static String projectIdOf(JsonNode mapping) {
    JsonNode id = mapping.path("metadata").path(StubMetadata.KEY).path(StubMetadata.PROJECT_ID);
    return id.isTextual() ? id.asText() : null;
  }
}
