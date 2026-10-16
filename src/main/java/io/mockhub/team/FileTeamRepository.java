package io.mockhub.team;

import com.fasterxml.jackson.core.type.TypeReference;
import io.mockhub.store.JsonFile;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class FileTeamRepository implements TeamRepository {
  private final JsonFile<List<Team>> file;
  private final List<Team> teams;

  public FileTeamRepository(Path rootDir) {
    this.file = new JsonFile<>(JsonFile.inDataDir(rootDir, "teams.json"), new TypeReference<>() {});
    this.teams = new ArrayList<>(file.read(List::of));
  }

  @Override
  public synchronized List<Team> findAll() {
    List<Team> copy = new ArrayList<>(teams);
    copy.sort(Comparator.comparing(t -> t.name().toLowerCase()));
    return copy;
  }

  @Override
  public synchronized Optional<Team> findById(String id) {
    return teams.stream().filter(t -> t.id().equals(id)).findFirst();
  }

  @Override
  public synchronized void save(Team team) {
    teams.removeIf(t -> t.id().equals(team.id()));
    teams.add(team);
    file.write(teams);
  }

  @Override
  public synchronized void deleteById(String id) {
    teams.removeIf(t -> t.id().equals(id));
    file.write(teams);
  }
}
