package io.mockhub.publictools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.data.CrudQuery;
import io.mockhub.data.CrudResource;
import io.mockhub.data.CrudService;
import io.mockhub.data.FileDataStore;
import io.mockhub.org.Organization;
import io.mockhub.project.ProjectService;
import io.mockhub.stub.StubService;
import io.mockhub.support.FakeStubGateway;
import io.mockhub.support.Fixtures;
import io.mockhub.support.InMemoryProjectRepository;
import io.mockhub.support.MutableClock;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InstantMocksTest {

  @TempDir
  Path data;
  private final MutableClock clock = new MutableClock(Instant.parse("2026-11-04T10:00:00Z"));
  private ProjectService projects;
  private StubService stubs;
  private CrudService crud;
  private InstantMocks mocks;

  @BeforeEach
  void setUp() {
    AtomicReference<ProjectService> ref = new AtomicReference<>();
    stubs = new StubService(new FakeStubGateway(), ref::get);
    projects = new ProjectService(new InMemoryProjectRepository(), List.of(stubs), clock);
    ref.set(projects);
    crud = new CrudService(new FileDataStore(data));
    mocks = new InstantMocks(data, projects, stubs, crud, () -> "team-instant", clock);
  }

  private static JsonNode stub(String path) {
    return Fixtures.tree("{'request':{'method':'GET','urlPath':'" + path + "'},'response':{'status':200}}");
  }

  @Test
  void createsAPrivateProjectThatOnlyTheKeyOpens() {
    // When
    InstantMocks.Created created = mocks.create(InstantSources.stubs(List.of(stub("/a"))));

    // Then
    assertThat(created.slug()).matches("m-[a-z0-9]{10}");
    assertThat(projects.get(created.projectId()).orgId()).isEqualTo(Organization.INSTANT_ID);
    assertThat(mocks.owned(created.slug(), created.token())).isPresent();
    assertThat(mocks.owned(created.slug(), "wrong")).isEmpty();
    assertThat(mocks.stubsOf(mocks.owned(created.slug(), created.token()).orElseThrow())).hasSize(1);
  }

  @Test
  void remembersMocksAcrossRestarts() {
    // Given
    InstantMocks.Created created = mocks.create(InstantSources.stubs(List.of()));

    // When
    InstantMocks reloaded = new InstantMocks(data, projects, stubs, crud, () -> "team-instant", clock);

    // Then
    assertThat(reloaded.owned(created.slug(), created.token())).isPresent();
  }

  @Test
  void capsStubsPerMock() {
    // Given
    List<JsonNode> twenty = new ArrayList<>();
    for (int i = 0; i < InstantMocks.MAX_STUBS; i++) twenty.add(stub("/s" + i));
    InstantMocks.Created created = mocks.create(InstantSources.stubs(twenty));
    InstantMocks.Entry entry = mocks.owned(created.slug(), created.token()).orElseThrow();

    // Then
    assertThatThrownBy(() -> mocks.addStub(entry, stub("/one-more"))).hasMessageContaining("up to 20 stubs");
  }

  @Test
  void countsRequestsPerDay() {
    // Given
    InstantMocks.Created created = mocks.create(InstantSources.stubs(List.of()));
    for (int i = 0; i < InstantMocks.DAILY_REQUESTS; i++) mocks.takeRequest(created.projectId());

    // When
    boolean overToday = mocks.takeRequest(created.projectId());
    clock.advance(Duration.ofDays(1));
    boolean tomorrow = mocks.takeRequest(created.projectId());

    // Then
    assertThat(overToday).isFalse();
    assertThat(tomorrow).isTrue();
  }

  @Test
  void deletesMocksAWeekAfterTheirLastChange() {
    // Given
    InstantMocks.Created old = mocks.create(InstantSources.stubs(List.of()));
    clock.advance(Duration.ofDays(5));
    InstantMocks.Created recent = mocks.create(InstantSources.stubs(List.of()));
    InstantMocks.Entry recentEntry = mocks.owned(recent.slug(), recent.token()).orElseThrow();
    mocks.addStub(recentEntry, stub("/kept"));
    clock.advance(Duration.ofDays(3));

    // When
    int removed = mocks.prune();

    // Then
    assertThat(removed).isEqualTo(1);
    assertThat(projects.find(old.projectId())).isEmpty();
    assertThat(mocks.owned(old.slug(), old.token())).isEmpty();
    assertThat(mocks.owned(recent.slug(), recent.token())).isPresent();
  }

  @Test
  void skipsImportedStubsThatCantRunAndSaysWhy() {
    // Given
    JsonNode proxy = Fixtures.tree("{'name':'Proxy','request':{'urlPath':'/p'},'response':{'proxyBaseUrl':'http://169.254.169.254'}}");
    InstantSeed seed = new InstantSeed(List.of(stub("/ok"), proxy), List.of(), false, 3);

    // When
    InstantMocks.Created created = mocks.create(project -> seed);

    // Then
    assertThat(mocks.stubsOf(mocks.owned(created.slug(), created.token()).orElseThrow())).hasSize(1);
    assertThat(created.skipped()).hasSize(2);
    assertThat(created.skipped().get(0)).startsWith("Proxy: response.proxyBaseUrl");
    assertThat(created.skipped().get(1)).startsWith("3 more operations");
  }

  @Test
  void refusesASeedWithNothingThatRunsAndLeavesNoProjectBehind() {
    // Given
    JsonNode proxy = Fixtures.tree("{'name':'Proxy','request':{'urlPath':'/p'},'response':{'proxyBaseUrl':'http://example.com'}}");
    int before = projects.findAll().size();

    // When / Then
    assertThatThrownBy(() -> mocks.create(project -> new InstantSeed(List.of(proxy), List.of(), false, 0)))
        .hasMessageStartingWith("None of the stubs can run in an instant mock.");
    assertThat(projects.findAll()).hasSize(before);
    assertThat(mocks.size()).isZero();
  }

  @Test
  void startsFromRecordsBehindACrudStub() {
    // Given
    JsonNode records = Fixtures.tree("[{'id':1,'name':'Ada'},{'id':2,'name':'Linus'}]");

    // When
    InstantMocks.Created created = mocks.create(InstantSources.records("users", records));

    // Then
    List<ObjectNode> shown = mocks.stubsOf(mocks.owned(created.slug(), created.token()).orElseThrow());
    assertThat(shown).hasSize(1);
    assertThat(shown.get(0).path("response").path("transformers").get(0).asText()).isEqualTo("mockhub-crud");
    CrudResource users = new CrudResource("users", "id", CrudResource.IdType.NUMBER);
    assertThat(crud.list(created.projectId(), users, CrudQuery.ALL).total()).isEqualTo(2);
  }
}
