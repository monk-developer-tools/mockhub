package io.mockhub.support;

import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import io.mockhub.stub.StubGateway;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class FakeStubGateway implements StubGateway {

  private final Map<UUID, StubMapping> stubs = new LinkedHashMap<>();
  private final List<ServeEvent> events = new ArrayList<>();
  private final List<String> clearedBasePaths = new ArrayList<>();

  @Override
  public List<StubMapping> findAll() {
    return new ArrayList<>(stubs.values());
  }

  @Override
  public Optional<StubMapping> findById(UUID id) {
    return Optional.ofNullable(stubs.get(id));
  }

  @Override
  public void add(StubMapping stub) {
    if (stub.getId() == null) stub.setId(UUID.randomUUID());
    stubs.put(stub.getId(), stub);
  }

  @Override
  public void update(StubMapping stub) {
    stubs.put(stub.getId(), stub);
  }

  @Override
  public void remove(UUID id) {
    stubs.remove(id);
  }

  @Override
  public List<ServeEvent> serveEvents() {
    return events;
  }

  @Override
  public void removeServeEventsUnder(String basePath) {
    clearedBasePaths.add(basePath);
  }

  @Override
  public void removeServeEvents(List<UUID> ids) {
    events.removeIf(e -> ids.contains(e.getId()));
  }

  public void record(ServeEvent event) {
    events.add(event);
  }

  public List<String> clearedBasePaths() {
    return clearedBasePaths;
  }
}
