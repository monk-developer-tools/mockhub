package io.mockhub.git;

import com.fasterxml.jackson.core.type.TypeReference;
import io.mockhub.store.JsonFile;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

public final class GitLinkStore {
  private static final TypeReference<TreeMap<String, GitLink>> TYPE = new TypeReference<>() {};

  private final JsonFile<TreeMap<String, GitLink>> file;
  private TreeMap<String, GitLink> links;

  public GitLinkStore(Path rootDir) {
    this.file = new JsonFile<>(JsonFile.inDataDir(rootDir, "git-links.json"), TYPE);
  }

  public synchronized Optional<GitLink> find(String projectId) {
    return Optional.ofNullable(all().get(projectId));
  }

  public synchronized void save(String projectId, GitLink link) {
    all().put(projectId, link);
    file.write(links);
  }

  public synchronized boolean delete(String projectId) {
    boolean removed = all().remove(projectId) != null;
    if (removed) file.write(links);
    return removed;
  }

  private Map<String, GitLink> all() {
    if (links == null) links = file.read(TreeMap::new);
    return links;
  }
}
