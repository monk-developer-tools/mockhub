package io.mockhub.state;

import com.github.tomakehurst.wiremock.store.Store;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.wiremock.extensions.state.internal.model.Context;

public final class ProjectStateStore implements Store<String, Object> {
  private static final String CONTEXT = "context:";

  private final StateRepository repository;
  private final StateScope scope;
  private final Clock clock;
  private final Map<String, Object> memory = new ConcurrentHashMap<>();

  public ProjectStateStore(StateRepository repository, StateScope scope, Clock clock) {
    this.repository = repository;
    this.scope = scope;
    this.clock = clock;
  }

  @Override
  public Stream<String> getAllKeys() {
    return Stream.concat(memory.keySet().stream(), repository.names().map(name -> CONTEXT + name)).distinct();
  }

  @Override
  public Optional<Object> get(String key) {
    Object held = memory.get(key);
    if (held != null || !key.startsWith(CONTEXT)) return Optional.ofNullable(held);
    return repository.find(name(key), scope.current()).map(found -> found.record().toContext());
  }

  @Override
  public void put(String key, Object value) {
    Optional<String> project = scope.current();
    if (!key.startsWith(CONTEXT) || !(value instanceof Context context) || project.isEmpty()) {
      memory.put(key, value);
      return;
    }
    memory.remove(key);
    repository.save(project.get(), StateRecord.of(context, clock.instant()));
  }

  @Override
  public void remove(String key) {
    memory.remove(key);
    if (key.startsWith(CONTEXT)) repository.delete(name(key));
  }

  @Override
  public void clear() {
    memory.clear();
    repository.clearAll();
  }

  private static String name(String key) {
    return key.substring(CONTEXT.length());
  }
}
