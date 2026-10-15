package io.mockhub.user;

import com.fasterxml.jackson.core.type.TypeReference;
import io.mockhub.store.JsonFile;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class FileUserRepository implements UserRepository {
  private final JsonFile<List<User>> file;
  private final List<User> users;

  public FileUserRepository(Path rootDir) {
    this.file = new JsonFile<>(JsonFile.inDataDir(rootDir, "users.json"), new TypeReference<>() {});
    this.users = new ArrayList<>(file.read(List::of));
  }

  @Override
  public synchronized List<User> findAll() {
    return List.copyOf(users);
  }

  @Override
  public synchronized Optional<User> findById(String id) {
    return users.stream().filter(u -> u.id().equals(id)).findFirst();
  }

  @Override
  public synchronized void save(User user) {
    users.removeIf(u -> u.id().equals(user.id()));
    users.add(user);
    file.write(users);
  }

  @Override
  public synchronized void deleteById(String id) {
    users.removeIf(u -> u.id().equals(id));
    file.write(users);
  }
}
