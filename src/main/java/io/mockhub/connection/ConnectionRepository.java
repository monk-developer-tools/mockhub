package io.mockhub.connection;

import java.util.List;
import java.util.Optional;

public interface ConnectionRepository {
  List<Connection> findAll(String orgId);

  Optional<Connection> find(String orgId, String key);

  void save(Connection connection);

  boolean delete(String orgId, String key);

  void deleteOrg(String orgId);
}
