package io.mockhub.environment;

import com.fasterxml.jackson.core.type.TypeReference;
import io.mockhub.project.Project;
import io.mockhub.project.ProjectListener;
import io.mockhub.store.JsonFile;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

public final class EnvironmentStore implements ProjectListener {
  private static final TypeReference<TreeMap<String, ProjectEnvironments>> TYPE = new TypeReference<>() {};

  private final JsonFile<TreeMap<String, ProjectEnvironments>> file;
  private volatile Map<String, ProjectEnvironments> cache;

  public EnvironmentStore(Path rootDir) {
    this.file = new JsonFile<>(JsonFile.inDataDir(rootDir, "environments.json"), TYPE);
  }

  public ProjectEnvironments get(String projectId) {
    return all().getOrDefault(projectId, ProjectEnvironments.NONE);
  }

  public synchronized void save(String projectId, ProjectEnvironments environments) {
    TreeMap<String, ProjectEnvironments> next = new TreeMap<>(all());
    if (environments.isEmpty()) next.remove(projectId);
    else next.put(projectId, environments);
    file.write(next);
    cache = Map.copyOf(next);
  }

  @Override
  public void onProjectDeleted(Project project) {
    if (all().containsKey(project.id())) save(project.id(), ProjectEnvironments.NONE);
  }

  private Map<String, ProjectEnvironments> all() {
    Map<String, ProjectEnvironments> current = cache;
    if (current == null) {
      synchronized (this) {
        if (cache == null) cache = Map.copyOf(file.read(TreeMap::new));
        current = cache;
      }
    }
    return current;
  }
}
