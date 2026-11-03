package io.mockhub.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import io.mockhub.project.Project;
import io.mockhub.project.ProjectListener;
import io.mockhub.store.JsonFile;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class StagedChanges implements ProjectListener {
  private final JsonFile<Map<String, List<StagedChange>>> file;
  private final Map<String, List<StagedChange>> byProject;
  private final Clock clock;

  public StagedChanges(Path rootDir, Clock clock) {
    this.file = new JsonFile<>(JsonFile.inDataDir(rootDir, "staged-changes.json"), new TypeReference<>() {});
    this.byProject = new HashMap<>(file.read(Map::of));
    this.clock = clock;
  }

  public synchronized List<StagedChange> list(String projectId) {
    return List.copyOf(byProject.getOrDefault(projectId, List.of()));
  }

  public synchronized List<ProposedChange> changes(String projectId) {
    return list(projectId).stream().map(StagedChange::change).toList();
  }

  public synchronized void replace(String projectId, List<ProposedChange> changes, String by) {
    Map<String, StagedChange> existing = list(projectId).stream()
        .collect(Collectors.toMap(s -> s.change().id(), Function.identity()));
    String now = Instant.now(clock).toString();
    List<StagedChange> next = new ArrayList<>();
    for (ProposedChange change : changes) {
      StagedChange old = existing.get(change.id());
      next.add(new StagedChange(change, old == null ? by : old.by(), old == null ? now : old.at()));
    }
    store(projectId, next);
  }

  public synchronized List<ProposedChange> take(String projectId, Collection<String> ids) {
    return list(projectId).stream().map(StagedChange::change).filter(c -> ids.contains(c.id())).toList();
  }

  public synchronized void remove(String projectId, Collection<String> ids) {
    store(projectId, list(projectId).stream().filter(s -> !ids.contains(s.change().id())).toList());
  }

  public synchronized Optional<StagedChange> find(String projectId, String id) {
    return list(projectId).stream().filter(s -> s.change().id().equals(id)).findFirst();
  }

  @Override
  public synchronized void onProjectDeleted(Project project) {
    if (byProject.remove(project.id()) != null) file.write(byProject);
  }

  private void store(String projectId, List<StagedChange> changes) {
    if (changes.isEmpty()) byProject.remove(projectId);
    else byProject.put(projectId, List.copyOf(changes));
    file.write(byProject);
  }
}
