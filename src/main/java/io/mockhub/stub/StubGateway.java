package io.mockhub.stub;

import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StubGateway {
  List<StubMapping> findAll();

  Optional<StubMapping> findById(UUID id);

  void add(StubMapping stub);

  void update(StubMapping stub);

  void remove(UUID id);

  List<ServeEvent> serveEvents();

  void removeServeEventsUnder(String basePath);

  void removeServeEvents(List<UUID> ids);
}
