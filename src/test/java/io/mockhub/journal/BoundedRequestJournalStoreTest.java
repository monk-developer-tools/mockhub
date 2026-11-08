package io.mockhub.journal;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import io.mockhub.support.Requests;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class BoundedRequestJournalStoreTest {

  private static ServeEvent event(String path) {
    return event(path, "");
  }

  private static ServeEvent event(String path, String body) {
    return ServeEvent.of(Requests.logged("POST", "http://localhost" + path, Map.of(), body));
  }

  private static List<String> urls(BoundedRequestJournalStore store) {
    return store.getAll().map(e -> e.getRequest().getUrl()).toList();
  }

  @Test
  void keepsTheNewestEntriesFirstAndDropsTheOldestPastItsLimit() {
    // Given
    BoundedRequestJournalStore store = new BoundedRequestJournalStore(3, Long.MAX_VALUE);

    // When
    for (int i = 1; i <= 5; i++) store.add(event("/r" + i));

    // Then
    assertThat(urls(store)).containsExactly("/r5", "/r4", "/r3");
    assertThat(store.size()).isEqualTo(3);
    assertThat(store.getAllKeys().count()).isEqualTo(3);
  }

  @Test
  void removingClearingAndReplacingKeepTheCountRight() {
    // Given
    BoundedRequestJournalStore store = new BoundedRequestJournalStore(10, Long.MAX_VALUE);
    ServeEvent first = event("/a");
    ServeEvent second = event("/b");
    store.add(first);
    store.add(second);

    // When
    store.put(first.getId(), first.withResponseDefinition(second.getResponseDefinition()));
    store.remove(second.getId());
    store.remove(second.getId());
    int afterRemove = store.size();
    store.removeLast();
    store.removeLast();
    int afterEmptying = store.size();
    store.add(event("/c"));
    store.clear();

    // Then
    assertThat(afterRemove).isEqualTo(1);
    assertThat(afterEmptying).isZero();
    assertThat(store.size()).isZero();
    assertThat(store.get(first.getId())).isEmpty();
  }

  @Test
  void dropsTheOldestEntriesOnceTheirBodiesPassTheByteBudget() {
    // Given
    long entry = BoundedRequestJournalStore.ENTRY_OVERHEAD + 10_000;
    BoundedRequestJournalStore store = new BoundedRequestJournalStore(5000, 3 * entry);
    String body = "x".repeat(10_000);

    // When
    for (int i = 1; i <= 5; i++) store.add(event("/big" + i, body));
    store.add(event("/huge", "y".repeat(50_000)));

    // Then
    assertThat(urls(store)).containsExactly("/huge");
    assertThat(store.bytes()).isEqualTo(BoundedRequestJournalStore.ENTRY_OVERHEAD + 50_000);
  }

  @Test
  void manyThreadsAddingAtOnceNeverLeaveMoreThanTheLimit() throws InterruptedException {
    // Given
    BoundedRequestJournalStore store = new BoundedRequestJournalStore(100, Long.MAX_VALUE);
    ExecutorService pool = Executors.newFixedThreadPool(8);
    List<ServeEvent> events = new ArrayList<>();
    for (int i = 0; i < 4000; i++) events.add(event("/p" + i));

    // When
    events.forEach(e -> pool.submit(() -> store.add(e)));
    pool.shutdown();
    pool.awaitTermination(30, TimeUnit.SECONDS);

    // Then
    assertThat(store.size()).isEqualTo(100);
    assertThat(store.getAll().count()).isEqualTo(100);
  }
}
