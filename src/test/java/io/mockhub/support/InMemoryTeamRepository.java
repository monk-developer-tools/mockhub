package io.mockhub.support;

import io.mockhub.team.Team;
import io.mockhub.team.TeamRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class InMemoryTeamRepository implements TeamRepository {
  private final Map<String, Team> teams = new LinkedHashMap<>();

  @Override
  public List<Team> findAll() {
    List<Team> all = new ArrayList<>(teams.values());
    all.sort(Comparator.comparing(Team::name));
    return all;
  }

  @Override
  public Optional<Team> findById(String id) {
    return Optional.ofNullable(teams.get(id));
  }

  @Override
  public void save(Team team) {
    teams.put(team.id(), team);
  }

  @Override
  public void deleteById(String id) {
    teams.remove(id);
  }
}
