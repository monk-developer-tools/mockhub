package io.mockhub.ai;

import static io.mockhub.support.Fixtures.json;
import static io.mockhub.support.Fixtures.tree;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.mockhub.api.Jsons;
import io.mockhub.callout.CalloutEntry;
import io.mockhub.callout.InMemoryCalloutLog;
import io.mockhub.callout.OutboundRequest;
import io.mockhub.history.HistoryEntry;
import io.mockhub.history.HistoryStore;
import io.mockhub.playground.Header;
import io.mockhub.project.Project;
import io.mockhub.project.ProjectService;
import io.mockhub.runtime.DefaultResponseStubs;
import io.mockhub.runtime.RateLimiter;
import io.mockhub.runtime.RuntimeService;
import io.mockhub.snapshot.Snapshot;
import io.mockhub.stub.StubService;
import io.mockhub.support.FakeStubGateway;
import io.mockhub.support.InMemoryProjectRepository;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MockToolsTest {

  private static final Instant NOW = Instant.parse("2026-11-03T10:00:00Z");

  @TempDir
  Path dir;

  private StubService stubs;
  private HistoryStore requestLog;
  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
  private InMemoryCalloutLog callouts;
  private Project orders;
  private String existingId;
  private RuntimeService runtime;

  @BeforeEach
  void setUp() {
    FakeStubGateway gateway = new FakeStubGateway();
    AtomicReference<ProjectService> ref = new AtomicReference<>();
    stubs = new StubService(gateway, ref::get);
    ProjectService projects = new ProjectService(new InMemoryProjectRepository(), List.of(stubs), Clock.systemUTC());
    ref.set(projects);
    requestLog = new HistoryStore(dir, clock, Duration.ofDays(7), Long.MAX_VALUE);
    callouts = new InMemoryCalloutLog(100);
    runtime = new RuntimeService(projects, new DefaultResponseStubs(stubs), new RateLimiter(Clock.systemUTC()));
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
    MockTools tools = new MockTools(stubs, requestLog, clock, callouts, runtime, id -> List.of(), orders.id(), List.of());

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
  void stagesTakingASnapshotFirstAndRestoringOnlyKnownSnapshots() {
    // Given
    Snapshot.Summary v1 = new Snapshot.Summary("s1", "v1", "", "2026-11-03T09:00:00Z", "Sam", false, "Orders", 1, 0, List.of());
    MockTools tools = new MockTools(stubs, requestLog, clock, callouts, runtime, id -> List.of(v1), orders.id(), List.of());
    run(tools, "create_stub", "{'stub':{'request':{'method':'GET','urlPath':'/x'},'response':{'status':200}},'summary':'x'}");

    // When
    JsonNode snapshot = run(tools, "create_snapshot", "{'name':'before x','purpose':'roll back'}");
    JsonNode restore = run(tools, "restore_snapshot", "{'id':'s1','summary':'Go back to v1'}");
    JsonNode unknown = run(tools, "restore_snapshot", "{'id':'nope','summary':'x'}");
    JsonNode listed = run(tools, "list_snapshots", "{}");

    // Then
    assertThat(snapshot.get("staged").asText()).isEqualTo("snapshot");
    assertThat(restore.get("staged").asText()).isEqualTo("restore");
    assertThat(unknown.get("error").asText()).contains("list_snapshots");
    assertThat(listed.get(0).get("name").asText()).isEqualTo("v1");
    assertThat(tools.changes()).extracting(ProposedChange::action).containsExactly("snapshot", "create", "restore");
    assertThat(tools.changes().get(2).stubId()).isEqualTo("s1");
  }

  @Test
  void reportsInvalidStubsAsToolErrors() {
    // Given
    MockTools tools = new MockTools(stubs, requestLog, clock, callouts, runtime, id -> List.of(), orders.id(), List.of());

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
    MockTools tools = new MockTools(stubs, requestLog, clock, callouts, runtime, id -> List.of(), orders.id(), List.of());
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
    MockTools tools = new MockTools(stubs, requestLog, clock, callouts, runtime, id -> List.of(), orders.id(), List.of());
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
    MockTools tools = new MockTools(stubs, requestLog, clock, callouts, runtime, id -> List.of(), orders.id(), List.of(pending));

    // When
    run(tools, "delete_stub", "{'id':'" + existingId + "','summary':'obsolete'}");

    // Then
    assertThat(tools.changes()).extracting(ProposedChange::action).containsExactly("create", "delete");
    assertThat(run(tools, "list_stubs", "{}").findValuesAsText("staged")).containsExactlyInAnyOrder("delete", "create");
  }

  @Test
  void listsFailedCalloutsSoTheAssistantCanDebugWebhooks() {
    // Given
    callouts.add(new CalloutEntry("1", "2026-11-03T10:00:00Z", orders.id(), existingId, "List", "async",
        new CalloutEntry.Trigger("GET", "/orders/items"), new OutboundRequest("POST", "https://partner.test/cb", List.of(), "{}"),
        new CalloutEntry.Result(204, List.of(), "", false), null, 12, null));
    callouts.add(new CalloutEntry("2", "2026-11-03T10:00:01Z", orders.id(), existingId, "List", "async",
        new CalloutEntry.Trigger("GET", "/orders/items"), new OutboundRequest("POST", "http://10.0.0.5/cb", List.of(), "{}"),
        null, "10.0.0.5 is a private or local address", 3, null));
    callouts.add(new CalloutEntry("3", "2026-11-03T10:00:02Z", "other-project", null, null, "async", null, null, null, "elsewhere", 1, null));
    MockTools tools = new MockTools(stubs, requestLog, clock, callouts, runtime, id -> List.of(), orders.id(), List.of());

    // When
    JsonNode all = run(tools, "list_callouts", "{}");
    JsonNode failed = run(tools, "list_callouts", "{'failed_only':true}");

    // Then
    assertThat(all).hasSize(2);
    assertThat(failed).singleElement().satisfies(row -> {
      assertThat(row.get("url").asText()).isEqualTo("http://10.0.0.5/cb");
      assertThat(row.get("error").asText()).contains("private");
      assertThat(row.get("stub").asText()).isEqualTo("List");
    });
  }

  @Test
  void searchesTheRequestHistoryWithFilters() {
    // Given
    requestLog.record(request("a", NOW.minusSeconds(7_200), "GET", "/orders/items", 200, true, ""));
    requestLog.record(request("b", NOW.minusSeconds(60), "POST", "/orders/refunds", 404, false, "{\"reason\":\"damaged\"}"));
    requestLog.record(request("c", NOW.minusSeconds(30), "GET", "/orders/items/9", 200, true, ""));
    MockTools tools = new MockTools(stubs, requestLog, clock, callouts, runtime, id -> List.of(), orders.id(), List.of());

    // When
    JsonNode unmatched = run(tools, "search_requests", "{'unmatched_only':true}");
    JsonNode recent = run(tools, "search_requests", "{'since_minutes':10,'method':'get'}");
    JsonNode byBody = run(tools, "search_requests", "{'text':'damaged','status':'4xx'}");

    // Then
    assertThat(unmatched.get("total").asInt()).isEqualTo(1);
    assertThat(unmatched.at("/requests/0/url").asText()).isEqualTo("/orders/refunds");
    assertThat(recent.get("requests")).singleElement().satisfies(row -> assertThat(row.get("id").asText()).isEqualTo("c"));
    assertThat(byBody.at("/requests/0/body").asText()).contains("damaged");
    assertThat(byBody.at("/requests/0/at").asText()).isEqualTo("2026-11-03T09:59:00Z");
  }

  private HistoryEntry request(String id, Instant at, String method, String url, int status, boolean matched, String body) {
    return new HistoryEntry(id, at.toEpochMilli(), orders.id(), method, url, "http://h" + url, "127.0.0.1",
        List.of(new Header("Content-Type", "application/json")), body, false, status, List.of(), "", false, matched,
        matched ? existingId : null, matched ? "List" : null, 2);
  }

  @Test
  void stagesRuntimeSettingsAndReadsThemBack() {
    // Given
    MockTools tools = new MockTools(stubs, requestLog, clock, callouts, runtime, id -> List.of(), orders.id(), List.of());

    // When
    JsonNode staged = run(tools, "set_runtime", "{'settings':{'rateLimit':{'requests':5,'per':'minute'}},'summary':'Limit to 5 a minute'}");
    JsonNode read = run(tools, "get_runtime", "{}");
    JsonNode invalid = run(tools, "set_runtime", "{'settings':{'rateLimit':{'requests':0,'per':'minute'}},'summary':'x'}");

    // Then
    assertThat(staged.get("staged").asText()).isEqualTo("runtime");
    assertThat(read.at("/rateLimit/requests").asInt()).isEqualTo(5);
    assertThat(runtime.current(orders.id()).rateLimit()).isNull();
    assertThat(tools.changes()).singleElement().satisfies(c -> {
      assertThat(c.action()).isEqualTo(ProposedChange.RUNTIME);
      assertThat(c.before().path("rateLimit").isNull()).isTrue();
    });
    assertThat(invalid.has("error")).isTrue();
  }

  private static final class FakeSetup implements ProjectSetup {
    String fetched;
    String checkedProto;

    @Override
    public Parsed parse(String projectId, String format, String content) {
      if (content.contains("broken")) throw io.mockhub.api.ApiException.badRequest("Can't read the openapi document: line 3");
      List<com.fasterxml.jackson.databind.node.ObjectNode> stubs = List.of(
          json("{'name':'List pets','request':{'method':'GET','urlPath':'/pets'},'response':{'status':200}}"),
          json("{'name':'Add pet','request':{'method':'POST','urlPath':'/pets'},'response':{'status':201}}"));
      return new Parsed(stubs, "Pet Store");
    }

    @Override
    public String fetch(String url) {
      fetched = url;
      return "openapi: 3.0.3";
    }

    @Override
    public JsonNode contract(String content) {
      return json("{'openapi':'3.0.3','info':{'title':'Pet Store','version':'1.0'},'paths':{}}");
    }

    @Override
    public void attachContract(String projectId, JsonNode spec, String fileName, String by) {
      throw new UnsupportedOperationException();
    }

    @Override
    public com.fasterxml.jackson.databind.node.ObjectNode checkCrud(String projectId, JsonNode request) {
      return request.deepCopy();
    }

    @Override
    public int createCrud(String projectId, JsonNode request) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void checkProto(String name, String text) {
      if (!name.endsWith(".proto")) throw io.mockhub.api.ApiException.badRequest("Name the file like library.proto");
      checkedProto = text;
    }

    @Override
    public void addProto(String projectId, String name, String text) {
      throw new UnsupportedOperationException();
    }

    @Override
    public JsonNode drift(String projectId) {
      return json("{'attached':true,'open':1}");
    }
  }

  @Test
  void stagesOneChangeForEveryStubOfAnImportedSpecAndItsContract() {
    // Given
    FakeSetup setup = new FakeSetup();
    MockTools tools = new MockTools(stubs, requestLog, clock, callouts, runtime, id -> List.of(), orders.id(), List.of(), setup);

    // When
    JsonNode result = run(tools, "import_spec", "{'format':'swagger','url':'https://example.com/pets.yaml','keep_contract':true}");

    // Then
    assertThat(setup.fetched).isEqualTo("https://example.com/pets.yaml");
    assertThat(result.get("stubs").asInt()).isEqualTo(2);
    assertThat(tools.changes()).extracting(ProposedChange::action).containsExactly("create", "create", "contract");
    assertThat(tools.changes()).extracting(ProposedChange::summary)
        .containsExactly("From Pet Store: GET /pets", "From Pet Store: POST /pets", "Watch traffic against Pet Store");
    assertThat(tools.changes().get(2).stub().path("fileName").asText()).isEqualTo("https://example.com/pets.yaml");
    assertThat(stubs.stubsOf(orders.id())).hasSize(1);
  }

  @Test
  void explainsWhyAnImportCannotBeStaged() {
    // Given
    MockTools tools = new MockTools(stubs, requestLog, clock, callouts, runtime, id -> List.of(), orders.id(), List.of(), new FakeSetup());

    // When
    JsonNode broken = run(tools, "import_spec", "{'format':'openapi','content':'broken: ['}");
    JsonNode nothing = run(tools, "import_spec", "{'format':'openapi'}");
    JsonNode unknown = run(tools, "import_spec", "{'format':'raml','content':'x'}");
    JsonNode wsdlContract = run(tools, "import_spec", "{'format':'wsdl','content':'<definitions/>','keep_contract':true}");

    // Then
    assertThat(broken.get("error").asText()).contains("line 3");
    assertThat(nothing.get("error").asText()).contains("content, or a url");
    assertThat(unknown.get("error").asText()).contains("openapi");
    assertThat(wsdlContract.get("error").asText()).contains("Only an OpenAPI document");
  }

  @Test
  void stagesAGrpcServiceBeforeItsStubsAndOneCrudResourcePerName() {
    // Given
    FakeSetup setup = new FakeSetup();
    MockTools tools = new MockTools(stubs, requestLog, clock, callouts, runtime, id -> List.of(), orders.id(), List.of(), setup);
    run(tools, "create_stub", "{'stub':{'request':{'method':'POST','urlPath':'/shop.Cart/Get'},'response':{'status':200}},'summary':'Get cart'}");

    // When
    JsonNode grpc = run(tools, "add_grpc_service", "{'name':'cart.proto','proto':'service Cart {}'}");
    JsonNode badName = run(tools, "add_grpc_service", "{'name':'cart.txt','proto':'x'}");
    JsonNode crud = run(tools, "create_crud_resource", "{'resource':'customers','id_type':'number','seed':[{'name':'Ada'}]}");
    JsonNode again = run(tools, "create_crud_resource", "{'resource':'customers'}");

    // Then
    assertThat(grpc.get("staged").asText()).isEqualTo("grpc");
    assertThat(badName.get("error").asText()).contains("library.proto");
    assertThat(crud.get("staged").asText()).isEqualTo("crud");
    assertThat(again.get("error").asText()).contains("already staged");
    assertThat(tools.changes()).extracting(ProposedChange::action).containsExactly("grpc", "create", "crud");
    assertThat(tools.changes().get(2).summary()).isEqualTo("Store customers with 1 record");
    assertThat(tools.changes().get(2).stub().path("idType").asText()).isEqualTo("number");
  }

  @Test
  void offersTheSetupToolsToTheModel() {
    // When
    JsonNode definitions = MockTools.definitions();

    // Then
    List<String> names = new java.util.ArrayList<>();
    definitions.forEach(d -> names.add(d.at("/function/name").asText()));
    assertThat(names).contains("import_spec", "attach_contract", "get_drift", "add_grpc_service", "create_crud_resource");
  }
}
