package io.mockhub.token;

import java.util.List;
import java.util.Optional;

public interface AccessTokenRepository {
  List<AccessToken> findAll();

  Optional<AccessToken> findByHash(String hash);

  void save(AccessToken token);

  void saveAll(List<AccessToken> tokens);

  void deleteById(String id);
}
