package io.mockhub.stub;

import static io.mockhub.support.Fixtures.json;
import static io.mockhub.support.Fixtures.stub;
import static io.mockhub.support.Fixtures.tree;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import io.mockhub.project.Project;
import io.mockhub.project.ProjectService;
import io.mockhub.support.FakeStubGateway;
import io.mockhub.support.InMemoryProjectRepository;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StubServiceTest {

  private final FakeStubGateway gateway = new FakeStubGateway();
  private ProjectService projects;
  private StubService stubs;
  private Project orders;
  private Project billing;

  @BeforeEach
  void setUp() {
    AtomicReference<ProjectService> ref = new AtomicReference<>();
    stubs = new StubService(gateway, ref::get);
    projects = new ProjectService(new InMemoryProjectRepository(), List.of(stubs), Clock.systemUTC());
    ref.set(projects);
    orders = projects.create(tree("{'name':'Orders','slug':'orders'}"));
    billing = projects.create(tree("{'name':'Billing','slug':'billing'}"));
  }

  @Test
  void storesStubsUnderTheProjectBasePathAndReturnsThemRelative() {
    // When
    ObjectNode created = stubs.create(orders.id(), json("{'request':{'method':'GET','urlPath':'/items'},'response':{'status':200}}"));

    // Then
    StubMapping stored = gateway.findAll().get(0);
    assertThat(stored.getRequest().getUrlPath()).isEqualTo("/orders/items");
    assertThat(StubMetadata.projectId(stored)).isEqualTo(orders.id());
    assertThat(StubMetadata.of(stored)).containsEntry("type", "rest");
    assertThat(created.at("/request/urlPath").asText()).isEqualTo("/items");
  }

  @Test
  void addsADefaultResponseWhenMissing() {
    // When
    ObjectNode created = stubs.create(orders.id(), json("{'request':{'method':'GET','urlPath':'/x'}}"));

    // Then
    assertThat(created.at("/response/status").asInt()).isEqualTo(200);
  }

  @Test
  void listsOnlyTheProjectsStubsOrderedByPriorityThenName() {
    // Given
    stubs.create(orders.id(), json("{'name':'b','priority':5,'request':{'urlPath':'/b'}}"));
    stubs.create(orders.id(), json("{'name':'a','priority':1,'request':{'urlPath':'/a'}}"));
    stubs.create(billing.id(), json("{'name':'other','request':{'urlPath':'/c'}}"));

    // When
    ArrayNode list = stubs.list(orders.id());

    // Then
    assertThat(list).extracting(n -> n.get("name").asText()).containsExactly("a", "b");
  }

  @Test
  void updateKeepsTheIdAndRewritesTheUrl() {
    // Given
    String id = stubs.create(orders.id(), json("{'request':{'urlPath':'/a'}}")).get("id").asText();

    // When
    stubs.update(orders.id(), id, json("{'request':{'urlPath':'/b'},'response':{'status':201}}"));

    // Then
    StubMapping stored = gateway.findById(UUID.fromString(id)).orElseThrow();
    assertThat(stored.getRequest().getUrlPath()).isEqualTo("/orders/b");
    assertThat(stored.getResponse().getStatus()).isEqualTo(201);
  }

  @Test
  void aStubOfAnotherProjectIsNotFound() {
    // Given
    String id = stubs.create(billing.id(), json("{'request':{'urlPath':'/a'}}")).get("id").asText();

    // When / Then
    assertThatThrownBy(() -> stubs.get(orders.id(), id)).extracting("status").isEqualTo(404);
  }

  @Test
  void copyInTheSameProjectMarksTheName() {
    // Given
    String id = stubs.create(orders.id(), json("{'name':'Get item','request':{'urlPath':'/a'}}")).get("id").asText();

    // When
    ObjectNode copy = stubs.copy(orders.id(), id, null);

    // Then
    assertThat(copy.get("name").asText()).isEqualTo("Get item (copy)");
    assertThat(copy.get("id").asText()).isNotEqualTo(id);
    assertThat(gateway.findAll()).hasSize(2);
  }

  @Test
  void moveReRootsTheStubIntoTheTargetProject() {
    // Given
    String id = stubs.create(orders.id(), json("{'name':'x','request':{'urlPath':'/a'}}")).get("id").asText();

    // When
    stubs.move(orders.id(), id, billing.id());

    // Then
    assertThat(gateway.findAll()).singleElement().satisfies(s -> {
      assertThat(s.getRequest().getUrlPath()).isEqualTo("/billing/a");
      assertThat(StubMetadata.projectId(s)).isEqualTo(billing.id());
    });
  }

  @Test
  void changingTheProjectBasePathReRootsItsStubs() {
    // Given
    stubs.create(orders.id(), json("{'request':{'urlPathTemplate':'/items/{id}'}}"));

    // When
    projects.update(orders.id(), tree("{'slug':'orders-v2'}"));

    // Then
    assertThat(gateway.findAll().get(0).getRequest().getUrlPathTemplate()).isEqualTo("/orders-v2/items/{id}");
  }

  @Test
  void deletingAProjectDeletesItsStubsOnly() {
    // Given
    stubs.create(orders.id(), json("{'request':{'urlPath':'/a'}}"));
    stubs.create(billing.id(), json("{'request':{'urlPath':'/b'}}"));

    // When
    projects.delete(orders.id());

    // Then
    assertThat(gateway.findAll()).singleElement().satisfies(s -> assertThat(StubMetadata.projectId(s)).isEqualTo(billing.id()));
  }

  @Test
  void adoptsAnUnassignedStubAndOptionallyPrefixesItsUrl() {
    // Given
    StubMapping raw = stub("{'request':{'method':'GET','url':'/legacy'},'response':{'status':204}}");
    gateway.add(raw);
    assertThat(stubs.unassigned()).hasSize(1);

    // When
    stubs.adopt(orders.id(), raw.getId().toString(), true);

    // Then
    assertThat(stubs.unassigned()).isEmpty();
    assertThat(gateway.findAll().get(0).getRequest().getUrl()).isEqualTo("/orders/legacy");
  }

  @Test
  void createAllAcceptsUrlsAlreadyUnderTheBasePath() {
    // When
    stubs.createAll(orders.id(), List.of(json("{'request':{'url':'/orders/a'}}"), json("{'request':{'url':'/b'}}")));

    // Then
    assertThat(gateway.findAll()).extracting(s -> s.getRequest().getUrl()).containsExactlyInAnyOrder("/orders/a", "/orders/b");
  }

  @Test
  void rejectsMalformedStubIds() {
    assertThatThrownBy(() -> stubs.get(orders.id(), "not-a-uuid")).extracting("status").isEqualTo(400);
  }
}
