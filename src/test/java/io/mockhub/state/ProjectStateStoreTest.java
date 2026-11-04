package io.mockhub.state;

import static org.assertj.core.api.Assertions.assertThat;

import io.mockhub.support.InMemoryDataStore;
import io.mockhub.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.wiremock.extensions.state.internal.model.Context;

class ProjectStateStoreTest {

  private final InMemoryDataStore data = new InMemoryDataStore();
  private final StateRepository repository = new StateRepository(data, () -> List.of("shop", "bank"), 3);
  private final StateScope scope = new StateScope();
  private final ProjectStateStore store = new ProjectStateStore(repository, scope, new MutableClock(Instant.parse("2026-11-04T10:00:00Z")));

  @Test
  void aContextWrittenDuringAProjectRequestIsSavedInThatProject() {
    // Given
    scope.enter("shop");

    // When
    store.put("context:cart", context("cart", "status", "open"));
    scope.leave();

    // Then
    assertThat(repository.list("shop")).extracting(StateRecord::name).containsExactly("cart");
    assertThat(repository.list("bank")).isEmpty();
    assertThat(((Context) store.get("context:cart").orElseThrow()).getProperties()).containsEntry("status", "open");
  }

  @Test
  void anotherProjectWritingTheSameContextTakesItOver() {
    // Given
    scope.enter("shop");
    store.put("context:shared", context("shared", "by", "shop"));

    // When
    scope.enter("bank");
    store.put("context:shared", context("shared", "by", "bank"));

    // Then
    assertThat(repository.list("shop")).isEmpty();
    assertThat(repository.list("bank")).extracting(StateRecord::name).containsExactly("shared");
    assertThat(((Context) store.get("context:shared").orElseThrow()).getProperties()).containsEntry("by", "bank");
  }

  @Test
  void transactionsAndWritesOutsideAProjectStayInMemory() {
    // Given
    scope.leave();

    // When
    store.put("transaction:abc", Map.of("contexts", "cart"));
    store.put("context:loose", context("loose", "a", "1"));

    // Then
    assertThat(store.get("transaction:abc")).isPresent();
    assertThat(store.get("context:loose")).isPresent();
    assertThat(repository.list("shop")).isEmpty();
    assertThat(store.getAllKeys()).containsExactlyInAnyOrder("transaction:abc", "context:loose");
  }

  @Test
  void removingAndClearingDeleteSavedContexts() {
    // Given
    scope.enter("shop");
    store.put("context:one", context("one", "a", "1"));
    store.put("context:two", context("two", "a", "2"));

    // When
    store.remove("context:one");

    // Then
    assertThat(store.getAllKeys()).containsExactly("context:two");
    store.clear();
    assertThat(store.getAllKeys()).isEmpty();
    assertThat(store.get("context:two")).isEmpty();
  }

  private static Context context(String name, String key, String value) {
    Context context = new Context(name);
    context.getProperties().put(key, value);
    return context;
  }

  @Test
  void dropsTheContextChangedLongestAgoPastTheLimit() {
    // Given
    MutableClock clock = new MutableClock(Instant.parse("2026-11-04T10:00:00Z"));
    ProjectStateStore capped = new ProjectStateStore(repository, scope, clock);
    scope.enter("shop");
    for (String name : List.of("a", "b", "c")) {
      capped.put("context:" + name, context(name, "v", "1"));
      clock.advance(Duration.ofMillis(1500));
    }
    capped.put("context:a", context("a", "v", "2"));
    clock.advance(Duration.ofSeconds(1));

    // When
    capped.put("context:d", context("d", "v", "1"));

    // Then
    assertThat(repository.list("shop")).extracting(StateRecord::name).containsExactly("a", "c", "d");
  }
}
