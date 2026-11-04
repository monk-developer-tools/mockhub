package io.mockhub.data;

import static io.mockhub.support.Fixtures.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileDataStoreTest {

  @TempDir
  Path dir;

  @Test
  void keepsRecordsOnDiskPerProject() {
    // Given
    FileDataStore store = new FileDataStore(dir);
    store.put("p1", "customers", "1", json("{'id':1,'name':'Ada'}"));
    store.put("p1", "orders", "a", json("{'id':'a'}"));
    store.put("p2", "customers", "9", json("{'id':9}"));

    // When
    FileDataStore reopened = new FileDataStore(dir);

    // Then
    assertThat(reopened.resources("p1")).isEqualTo(Map.of("customers", 1, "orders", 1));
    assertThat(reopened.get("p1", "customers", "1").orElseThrow().get("name").asText()).isEqualTo("Ada");
    assertThat(reopened.list("p2", "customers")).hasSize(1);
    assertThat(dir.resolve("mockhub/data/p1.json")).exists();
  }

  @Test
  void deletesClearsAndRestores() {
    // Given
    FileDataStore store = new FileDataStore(dir);
    store.put("p1", "customers", "1", json("{'id':1}"));
    store.put("p1", "customers", "2", json("{'id':2}"));

    // When
    boolean deleted = store.delete("p1", "customers", "1");
    boolean missing = store.delete("p1", "customers", "1");
    Map<String, Map<String, com.fasterxml.jackson.databind.node.ObjectNode>> snapshot = store.snapshot("p1");
    store.clear("p1", "customers");
    store.restore("p3", snapshot);
    store.clearProject("p1");

    // Then
    assertThat(deleted).isTrue();
    assertThat(missing).isFalse();
    assertThat(new FileDataStore(dir).list("p3", "customers")).hasSize(1);
    assertThat(dir.resolve("mockhub/data/p1.json")).doesNotExist();
  }

  @Test
  void returnsCopiesSoCallersCannotChangeStoredRecords() {
    // Given
    FileDataStore store = new FileDataStore(dir);
    store.put("p1", "customers", "1", json("{'id':1,'name':'Ada'}"));

    // When
    store.get("p1", "customers", "1").orElseThrow().put("name", "changed");

    // Then
    assertThat(store.get("p1", "customers", "1").orElseThrow().get("name").asText()).isEqualTo("Ada");
    assertThatThrownBy(() -> store.list("../etc", "x")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void keepsOnlyTheNewestRecordsOfAResource() {
    // Given
    FileDataStore store = new FileDataStore(dir);
    for (int i = 1; i <= 5; i++) store.put("p1", "logs", String.valueOf(i), json("{'id':" + i + "}"));
    store.put("p1", "logs", "1", json("{'id':1,'edited':true}"));

    // When
    int removed = store.keepNewest("p1", "logs", 3);

    // Then
    assertThat(removed).isEqualTo(2);
    assertThat(new FileDataStore(dir).list("p1", "logs")).extracting(r -> r.get("id").asInt()).containsExactly(3, 4, 5);
    assertThat(store.keepNewest("p1", "logs", 3)).isZero();
    assertThat(store.keepNewest("p1", "missing", 3)).isZero();
  }
}
