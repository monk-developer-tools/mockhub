package io.mockhub.publictools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
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
  private InstantMocks mocks;

  @BeforeEach
  void setUp() {
    AtomicReference<ProjectService> ref = new AtomicReference<>();
    stubs = new StubService(new FakeStubGateway(), ref::get);
    projects = new ProjectService(new InMemoryProjectRepository(), List.of(stubs), clock);
    ref.set(projects);
    mocks = new InstantMocks(data, projects, stubs, () -> "team-instant", clock);
  }

  private static JsonNode stub(String path) {
    return Fixtures.tree("{'request':{'method':'GET','urlPath':'" + path + "'},'response':{'status':200}}");
  }

  @Test
  void createsAPrivateProjectThatOnlyTheKeyOpens() {
    // When
    InstantMocks.Created created = mocks.create(List.of(stub("/a")));

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
    InstantMocks.Created created = mocks.create(List.of());

    // When
    InstantMocks reloaded = new InstantMocks(data, projects, stubs, () -> "team-instant", clock);

    // Then
    assertThat(reloaded.owned(created.slug(), created.token())).isPresent();
  }

  @Test
  void capsStubsPerMock() {
    // Given
    List<JsonNode> twenty = new ArrayList<>();
    for (int i = 0; i < InstantMocks.MAX_STUBS; i++) twenty.add(stub("/s" + i));
    InstantMocks.Created created = mocks.create(twenty);
    InstantMocks.Entry entry = mocks.owned(created.slug(), created.token()).orElseThrow();

    // Then
    assertThatThrownBy(() -> mocks.addStub(entry, stub("/one-more"))).hasMessageContaining("up to 20 stubs");
  }

  @Test
  void countsRequestsPerDay() {
    // Given
    InstantMocks.Created created = mocks.create(List.of());
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
    InstantMocks.Created old = mocks.create(List.of());
    clock.advance(Duration.ofDays(5));
    InstantMocks.Created recent = mocks.create(List.of());
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
}
