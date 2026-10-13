package io.mockhub.stub;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.Admin;
import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class WireMockStubGateway implements StubGateway {
  private final Admin admin;

  public WireMockStubGateway(Admin admin) {
    this.admin = admin;
  }

  @Override
  public List<StubMapping> findAll() {
    return admin.listAllStubMappings().getMappings();
  }

  @Override
  public Optional<StubMapping> findById(UUID id) {
    return Optional.ofNullable(admin.getStubMapping(id).getItem());
  }

  @Override
  public void add(StubMapping stub) {
    admin.addStubMapping(stub);
  }

  @Override
  public void update(StubMapping stub) {
    admin.editStubMapping(stub);
  }

  @Override
  public void remove(UUID id) {
    admin.removeStubMapping(id);
  }

  @Override
  public List<ServeEvent> serveEvents() {
    return admin.getServeEvents().getServeEvents();
  }

  @Override
  public void removeServeEventsUnder(String basePath) {
    admin.removeServeEventsMatching(
        RequestPatternBuilder.newRequestPattern(RequestMethod.ANY, WireMock.urlMatching(basePath + "([/?].*)?")).build());
  }
}
