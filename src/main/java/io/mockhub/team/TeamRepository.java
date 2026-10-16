package io.mockhub.team;

import java.util.List;
import java.util.Optional;

public interface TeamRepository {
  List<Team> findAll();

  Optional<Team> findById(String id);

  void save(Team team);

  void deleteById(String id);
}
