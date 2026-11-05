package io.mockhub.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.mockhub.api.ApiException;
import io.mockhub.history.HistoryEntry;
import io.mockhub.history.HistoryStore;
import io.mockhub.playground.Header;
import io.mockhub.project.Project;
import io.mockhub.support.MutableClock;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DriftServiceTest {

  @TempDir
  Path data;
  private final MutableClock clock = new MutableClock(Instant.parse("2026-11-05T10:00:00Z"));
  private final Project project = new Project("p1", "Orders", "orders", "", null, null, null, "o1", "t1", null, false, null);
  private final List<List<DriftState.Finding>> notified = new ArrayList<>();
  private HistoryStore history;
  private ContractStore store;
  private DriftService drift;
  private int seq;

  @BeforeEach
  void start() {
    history = new HistoryStore(data.resolve("history"), clock, Duration.ofDays(7), 100L * 1024 * 1024);
    store = new ContractStore(data);
    DriftNotifier notifier = new DriftNotifier(null, null) {
      @Override
      public DriftState.Notice notify(String url, String at, String projectId, String name, String link, List<DriftState.Finding> fresh) {
        notified.add(fresh);
        return new DriftState.Notice(at, fresh.size(), 204, null);
      }

      @Override
      public void checkUrl(String url) {}
    };
    drift = new DriftService(store, history, id -> id.equals("p1") ? Optional.of(project) : Optional.empty(), notifier, p -> null, clock);
  }

  @AfterEach
  void stop() {
    history.close();
  }

  private void record(String method, String url, int status, String body) {
    seq++;
    history.record(new HistoryEntry("e" + seq, clock.instant().toEpochMilli() + seq, "p1", method, url, "http://h" + url, "127.0.0.1", List.of(),
        "", false, status, List.of(new Header("Content-Type", "application/json")), body, false, true, "s1", "stub", 2));
  }

  private void attach() {
    drift.attach("p1", ContractDocument.parse(ContractCheckerTest.ORDERS), "upload", "orders.yaml", null);
  }

  @Test
  void groupsTheSameDriftAndCountsIt() {
    // Given
    attach();
    record("GET", "/orders/refunds/1", 200, "{}");
    record("GET", "/orders/refunds/2", 200, "{}");
    record("GET", "/orders/orders/7", 200, "{\"id\":7,\"amount\":1}");

    // When
    DriftState state = drift.check("p1");

    // Then
    assertThat(state.findings()).hasSize(1);
    DriftState.Finding f = state.findings().get(0);
    assertThat(f.kind()).isEqualTo(DriftKind.UNDOCUMENTED_PATH);
    assertThat(f.path()).isEqualTo("/refunds/{id}");
    assertThat(f.count()).isEqualTo(2);
    assertThat(f.exampleUrl()).isEqualTo("/orders/refunds/2");
    assertThat(state.scanned()).isEqualTo(3);
  }

  @Test
  void eachCheckOnlyReadsNewTrafficAndNotifiesAboutNewFindings() {
    // Given
    attach();
    store.save("p1", store.contract("p1").orElseThrow().withNotifyUrl("http://hooks.example.com"));
    record("GET", "/orders/refunds/1", 200, "{}");
    drift.check("p1");

    // When
    record("GET", "/orders/refunds/2", 200, "{}");
    record("DELETE", "/orders/orders", 204, "");
    DriftState state = drift.check("p1");

    // Then
    assertThat(state.scanned()).isEqualTo(3);
    assertThat(state.findings()).extracting(DriftState.Finding::kind)
        .containsExactlyInAnyOrder(DriftKind.UNDOCUMENTED_PATH, DriftKind.UNDOCUMENTED_METHOD);
    assertThat(notified).hasSize(2);
    assertThat(notified.get(1)).extracting(DriftState.Finding::kind).containsExactly(DriftKind.UNDOCUMENTED_METHOD);
    assertThat(state.lastNotice().status()).isEqualTo(204);
  }

  @Test
  void expectedFindingsStayMarkedAcrossARecheckAndDontNotify() {
    // Given
    attach();
    store.save("p1", store.contract("p1").orElseThrow().withNotifyUrl("http://hooks.example.com"));
    record("GET", "/orders/refunds/1", 200, "{}");
    String key = drift.check("p1").findings().get(0).key();
    notified.clear();

    // When
    drift.setExpected("p1", key, true);
    DriftState state = drift.restart("p1");

    // Then
    assertThat(state.expected()).containsExactly(key);
    assertThat(state.findings()).hasSize(1);
    assertThat(notified).isEmpty();
  }

  @Test
  void replacingTheContractChecksAllTrafficAgain() {
    // Given
    attach();
    record("GET", "/orders/refunds/1", 200, "{}");
    drift.check("p1");
    String fixed = ContractCheckerTest.ORDERS + "  /refunds/{id}:\n    get:\n      responses:\n        '200': { description: ok, content: { application/json: {} } }\n";

    // When
    drift.attach("p1", ContractDocument.parse(fixed), "editor", null, null);
    DriftState state = drift.check("p1");

    // Then
    assertThat(state.findings()).isEmpty();
    assertThat(state.scanned()).isEqualTo(1);
  }

  @Test
  void aProjectWithoutAContractHasNothingToCheck() {
    // Given / When / Then
    assertThatThrownBy(() -> drift.check("p1")).isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status).isEqualTo(404));
  }

  @Test
  void detachingRemovesTheContractAndItsFindings() {
    // Given
    attach();
    record("GET", "/orders/refunds/1", 200, "{}");
    drift.check("p1");

    // When
    drift.detach("p1");

    // Then
    assertThat(drift.contract("p1")).isEmpty();
    assertThat(store.drift("p1").findings()).isEmpty();
    assertThat(store.projectsWithContracts()).isEmpty();
  }
}
