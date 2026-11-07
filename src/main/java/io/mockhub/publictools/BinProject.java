package io.mockhub.publictools;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.api.Jsons;
import io.mockhub.auth.Principal;
import io.mockhub.controller.TeamChoice;
import io.mockhub.data.CrudService;
import io.mockhub.data.DataStore;
import io.mockhub.project.Project;
import io.mockhub.project.ProjectService;
import io.mockhub.runtime.DefaultResponseStubs;
import io.mockhub.stub.StubService;
import io.mockhub.team.Team;
import java.util.List;

public final class BinProject {
  private final ProjectService projects;
  private final StubService stubs;
  private final CrudService crud;
  private final TeamChoice teams;
  private final DataStore records;
  private final DefaultResponseStubs defaultResponses;

  public BinProject(ProjectService projects, StubService stubs, CrudService crud, TeamChoice teams, DataStore records,
      DefaultResponseStubs defaultResponses) {
    this.projects = projects;
    this.stubs = stubs;
    this.crud = crud;
    this.teams = teams;
    this.records = records;
    this.defaultResponses = defaultResponses;
  }

  public static BinProject around(Bins bins, ProjectService projects, StubService stubs, CrudService crud, TeamChoice teams,
      DefaultResponseStubs defaultResponses) {
    return new BinProject(projects, stubs, crud, teams, bins.records(), defaultResponses);
  }

  public record Saved(String projectId, String name, String slug, String ui, List<String> notes) {}

  public Saved save(String binId, Bins.Settings settings, Principal who, String askedName) {
    BinStubs.Plan plan = BinStubs.of(settings);
    Team team = teams.writable(who, "", "");
    String name = askedName == null ? "" : askedName.trim();
    ObjectNode body = Jsons.object()
        .put("name", name.isEmpty() ? "Bin " + binId.substring(0, 6) : name)
        .put("description", "Rules saved from the public webhook bin " + binId)
        .put("orgId", team.orgId())
        .put("teamId", team.id());
    body.putObject("runtime").set("defaultResponse", plan.defaultResponse());
    Project project = projects.create(body);
    try {
      plan.stubs().forEach(stub -> stubs.create(project.id(), stub));
      defaultResponses.sync(project);
      seedRecords(binId, settings, project.id());
    } catch (RuntimeException e) {
      projects.delete(project.id());
      throw e;
    }
    return new Saved(project.id(), project.name(), project.slug(), "/__admin/mockhub/ui/projects/" + project.id(), plan.notes());
  }

  private void seedRecords(String binId, Bins.Settings settings, String projectId) {
    for (BinRule rule : settings.rules()) {
      if (rule.crud().isEmpty()) continue;
      ArrayNode list = Jsons.MAPPER.createArrayNode();
      records.list(binId, rule.crud().get().resource().name()).forEach(list::add);
      crud.seed(projectId, rule.crud().get().resource(), list);
    }
  }
}
