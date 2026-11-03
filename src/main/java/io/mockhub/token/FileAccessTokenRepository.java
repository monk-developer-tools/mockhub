package io.mockhub.token;

import com.fasterxml.jackson.core.type.TypeReference;
import io.mockhub.store.JsonFile;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class FileAccessTokenRepository implements AccessTokenRepository {
  private final JsonFile<List<AccessToken>> file;
  private final List<AccessToken> tokens;

  public FileAccessTokenRepository(Path rootDir) {
    this.file = new JsonFile<>(JsonFile.inDataDir(rootDir, "access-tokens.json"), new TypeReference<>() {});
    this.tokens = new ArrayList<>(file.read(List::of));
  }

  @Override
  public synchronized List<AccessToken> findAll() {
    return List.copyOf(tokens);
  }

  @Override
  public synchronized Optional<AccessToken> findByHash(String hash) {
    return tokens.stream().filter(t -> t.hash().equals(hash)).findFirst();
  }

  @Override
  public synchronized void save(AccessToken token) {
    tokens.removeIf(t -> t.id().equals(token.id()));
    tokens.add(token);
    file.write(tokens);
  }

  @Override
  public synchronized void saveAll(List<AccessToken> updated) {
    for (AccessToken token : updated) {
      tokens.replaceAll(t -> t.id().equals(token.id()) ? token : t);
    }
    file.write(tokens);
  }

  @Override
  public synchronized void deleteById(String id) {
    tokens.removeIf(t -> t.id().equals(id));
    file.write(tokens);
  }
}
