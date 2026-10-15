package io.mockhub.support;

import io.mockhub.user.User;
import io.mockhub.user.UserRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class InMemoryUserRepository implements UserRepository {
  private final Map<String, User> users = new LinkedHashMap<>();

  @Override
  public List<User> findAll() {
    return new ArrayList<>(users.values());
  }

  @Override
  public Optional<User> findById(String id) {
    return Optional.ofNullable(users.get(id));
  }

  @Override
  public void save(User user) {
    users.put(user.id(), user);
  }

  @Override
  public void deleteById(String id) {
    users.remove(id);
  }
}
