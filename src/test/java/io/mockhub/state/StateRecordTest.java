package io.mockhub.state;

import static io.mockhub.support.Fixtures.tree;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.mockhub.api.ApiException;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.wiremock.extensions.state.internal.model.Context;

class StateRecordTest {

  private static final Instant NOW = Instant.parse("2026-11-04T10:00:00Z");

  @Test
  void aContextBecomesJsonAndComesBackWithItsUpdateCount() {
    // Given
    Context context = new Context("cart-42");
    context.getProperties().put("status", "open");
    context.getList().add(Map.of("sku", "book"));
    context.incUpdateCount();
    context.incUpdateCount();

    // When
    StateRecord saved = StateRecord.of(context, NOW);
    Context restored = StateRecord.of(saved.toJson()).toContext();

    // Then
    assertThat(saved.toJson().toString()).isEqualTo(
        "{\"name\":\"cart-42\",\"properties\":{\"status\":\"open\"},\"list\":[{\"sku\":\"book\"}],\"updateCount\":2,\"updatedAt\":\"2026-11-04T10:00:00Z\"}");
    assertThat(restored.getContextName()).isEqualTo("cart-42");
    assertThat(restored.getProperties()).containsEntry("status", "open");
    assertThat(restored.getList()).containsExactly(Map.of("sku", "book"));
    assertThat(restored.getUpdateCount()).isEqualTo(2L);
  }

  @Test
  void anEditTurnsNumbersAndBooleansIntoText() {
    // Given
    String body = "{'properties':{'count':3,'paid':true,'gone':null},'list':[{'qty':2}]}";

    // When
    StateRecord edited = StateRecord.edited("cart", tree(body), 5, NOW);

    // Then
    assertThat(edited.properties()).isEqualTo(Map.of("count", "3", "paid", "true"));
    assertThat(edited.list()).containsExactly(Map.of("qty", "2"));
    assertThat(edited.updateCount()).isEqualTo(5);
  }

  @Test
  void anEditExplainsWhatIsWrong() {
    // Then
    assertThatThrownBy(() -> StateRecord.edited("", tree("{}"), 1, NOW)).isInstanceOf(ApiException.class).hasMessageContaining("?name=");
    assertThatThrownBy(() -> StateRecord.edited("c", tree("{'properties':{'a':{'b':1}}}"), 1, NOW)).hasMessageContaining("properties.a");
    assertThatThrownBy(() -> StateRecord.edited("c", tree("{'list':{}}"), 1, NOW)).hasMessageContaining("list must be an array");
    assertThatThrownBy(() -> StateRecord.edited("c", tree("{'list':[{'a':[1]}]}"), 1, NOW)).hasMessageContaining("list[0].a");
  }
}
