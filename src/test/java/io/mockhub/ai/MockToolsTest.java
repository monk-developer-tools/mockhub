package io.mockhub.ai;

import static io.mockhub.support.Fixtures.json;
import static io.mockhub.support.Fixtures.tree;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.mockhub.api.Jsons;
import io.mockhub.journal.RequestLogService;
import io.mockhub.project.Project;
import io.mockhub.project.ProjectService;
import io.mockhub.stub.StubService;
import io.mockhub.support.FakeStubGateway;
import io.mockhub.support.InMemoryProjectRepository;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MockToolsTest {

  private StubService stubs;
  private RequestLogService requestLog;
  private Project orders;
  private String existingId;

  @BeforeEach
  void setUp() {
    FakeStubGateway gateway = new FakeStubGateway();
    AtomicReference<ProjectService> ref = new AtomicReference<>();
    stubs = new StubService(gateway, ref::get);
    ProjectService projects = new ProjectService(new InMemoryProjectRepository(), List.of(stubs), Clock.systemUTC());
    ref.set(projects);
    requestLog = new RequestLogService(gateway, projects);
    orders = projects.create(tree("{'orgId':'org1','name':'Orders','slug':'orders'}"));
    existingId = stubs.create(orders.id(), json("{'name':'List','request':{'method':'GET','urlPath':'/items'},'response':{'status':200}}")).get("id").asText();
  }

  private JsonNode run(MockTools tools, String name, String args) {
    try {
      return Jsons.MAPPER.readTree(tools.execute(name, json(args)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @Test
  void stagesNewStubsWithoutTouchingTheServer() {
    // Given
    MockTools tools = new MockTools(stubs, requestLog, orders.id(), List.of());

    // When
    JsonNode result = run(tools, "create_stub", "{'stub':{'name':'Get','request':{'method':'GET','urlPathTemplate':'/items/{id}'},'response':{'status':200}},'summary':'Get one item'}");

    // Then
    assertThat(result.get("staged").asText()).isEqualTo("create");
    assertThat(tools.changes()).singleElement().satisfies(c -> {
      assertThat(c.action()).isEqualTo("create");
      assertThat(c.summary()).isEqualTo("Get one item");
    });
    assertThat(stubs.stubsOf(orders.id())).hasSize(1);
    assertThat(run(tools, "list_stubs", "{}")).hasSize(2);
  }

  @Test
  void reportsInvalidStubsAsToolErrors() {
    // Given
    MockTools tools = new MockTools(stubs, requestLog, orders.id(), List.of());

    // Then
    assertThat(run(tools, "create_stub", "{'stub':{'response':{'status':200}},'summary':'x'}").get("error").asText()).contains("request");
    assertThat(run(tools, "create_stub", "{'stub':{'request':{'method':'GET','urlPath':'/x'},'response':{'status':'abc'}},'summary':'x'}").has("error")).isTrue();
    assertThat(run(tools, "get_stub", "{'id':'missing'}").has("error")).isTrue();
    assertThat(run(tools, "nope", "{}").get("error").asText()).contains("Unknown tool");
    assertThat(tools.changes()).isEmpty();
  }

  @Test
  void updatesKeepTheOriginalForReviewAndReplaceEarlierProposals() {
    // Given
    MockTools tools = new MockTools(stubs, requestLog, orders.id(), List.of());
    String updated = "{'id':'" + existingId + "','stub':{'name':'List','request':{'method':'GET','urlPath':'/items'},'response':{'status':503}},'summary':'Make it fail'}";

    // When
    run(tools, "update_stub", updated);
    run(tools, "update_stub", updated.replace("503", "500"));

    // Then
    assertThat(tools.changes()).singleElement().satisfies(c -> {
      assertThat(c.action()).isEqualTo("update");
      assertThat(c.stubId()).isEqualTo(existingId);
      assertThat(c.before().at("/response/status").asInt()).isEqualTo(200);
      assertThat(c.stub().at("/response/status").asInt()).isEqualTo(500);
    });
    assertThat(run(tools, "get_stub", "{'id':'" + existingId + "'}").at("/response/status").asInt()).isEqualTo(500);
  }

  @Test
  void revisesOrDropsStagedStubsByChangeId() {
    // Given
    MockTools tools = new MockTools(stubs, requestLog, orders.id(), List.of());
    String changeId = run(tools, "create_stub", "{'stub':{'request':{'method':'POST','urlPath':'/items'},'response':{'status':201}},'summary':'Create'}").get("changeId").asText();

    // When
    run(tools, "update_stub", "{'id':'" + changeId + "','stub':{'request':{'method':'POST','urlPath':'/items'},'response':{'status':202}},'summary':'Accept'}");

    // Then
    assertThat(tools.changes()).singleElement().satisfies(c -> assertThat(c.stub().at("/response/status").asInt()).isEqualTo(202));
    run(tools, "delete_stub", "{'id':'" + changeId + "','summary':'not needed'}");
    assertThat(tools.changes()).isEmpty();
  }

  @Test
  void stagesDeletionsAndContinuesFromPendingChanges() {
    // Given
    ProposedChange pending = new ProposedChange("c-1", "create", null, json("{'request':{'urlPath':'/a'}}"), null, "earlier");
    MockTools tools = new MockTools(stubs, requestLog, orders.id(), List.of(pending));

    // When
    run(tools, "delete_stub", "{'id':'" + existingId + "','summary':'obsolete'}");

    // Then
    assertThat(tools.changes()).extracting(ProposedChange::action).containsExactly("create", "delete");
    assertThat(run(tools, "list_stubs", "{}").findValuesAsText("staged")).containsExactlyInAnyOrder("delete", "create");
  }
}
