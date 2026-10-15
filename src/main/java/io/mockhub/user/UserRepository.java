package io.mockhub.user;

import java.util.List;
import java.util.Optional;

public interface UserRepository {
  List<User> findAll();

  Optional<User> findById(String id);

  void save(User user);

  void deleteById(String id);
}
