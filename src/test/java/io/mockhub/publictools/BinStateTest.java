package io.mockhub.publictools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.mockhub.template.ValueHelpers;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class BinStateTest {
  private final ExecutorService workers = Executors.newFixedThreadPool(2);
  private final TemplatePlayground templates = new TemplatePlayground(List.of(new ValueHelpers()), workers, 2000);
  private final TemplatePlayground.Sample order = new TemplatePlayground.Sample("POST", "/orders", Map.of("Content-Type", "application/json"), "{\"id\":\"o7\"}");

  @AfterEach
  void stop() {
    workers.shutdownNow();
  }

  @Test
  void countersSavedValuesAndListsCarryFromOneRequestToTheNext() {
    // Given
    BinState state = new BinState();
    String template = "{{counter 'orders'}}{{saveValue 'last' (jsonPath request.body '$.id')}}{{addToList 'seen' (jsonPath request.body '$.id')}}";

    // When
    String first = templates.render(template, order, state).output();
    String second = templates.render(template, order, state).output();
    String read = templates.render("{{counter 'orders' add=0}} {{savedValue 'last'}} {{savedValue 'none' default='-'}} {{{savedList 'seen'}}} {{len (savedList 'seen')}}", order, state).output();
    String reset = templates.render("{{counter 'orders' reset=true}}", order, state).output();

    // Then
    assertThat(first).isEqualTo("1");
    assertThat(second).isEqualTo("2");
    assertThat(read).isEqualTo("2 o7 - [\"o7\",\"o7\"] 2");
    assertThat(reset).isEqualTo("0");
    assertThat(state.takeChanged()).isTrue();
    assertThat(state.takeChanged()).isFalse();
  }

  @Test
  void aListKeepsItsNewestHundredAndTheStateSurvivesJson() {
    // Given
    BinState state = new BinState();
    for (int i = 1; i <= 105; i++) state.add("ids", "id-" + i);
    state.save("mode", "slow");
    state.count("hits", 3, false);

    // When
    BinState copy = BinState.from(state.toJson());

    // Then
    assertThat(copy.list("ids")).hasSize(100).startsWith("id-6").endsWith("id-105");
    assertThat(copy.saved("mode")).contains("slow");
    assertThat(copy.toJson()).isEqualTo(state.toJson());
    assertThat(copy.takeChanged()).isFalse();
  }

  @Test
  void theStateStaysSmall() {
    // Given
    BinState state = new BinState();
    for (int i = 0; i < BinState.MAX_NAMES; i++) state.count("c" + i, 1, false);

    // When
    TemplatePlayground.Result tooMany = templates.render("{{saveValue 'one-more' 'x'}}", order, state);

    // Then
    assertThat(tooMany.error()).contains("up to 100 counters");
    assertThatThrownBy(() -> new BinState().save("big", "x".repeat(BinState.MAX_VALUE + 1))).hasMessageContaining("4 KB");
    assertThatThrownBy(() -> new BinState().count(" ", 1, false)).hasMessageContaining("1 to 60 characters");
  }

  @Test
  void thePlaygroundExplainsThatStateBelongsToABin() {
    // Given
    String template = "{{counter 'orders'}}";

    // When
    TemplatePlayground.Result result = templates.render(template, order);

    // Then
    assertThat(result.error()).isEqualTo("Counters, saved values and lists work in a webhook bin's answers.");
  }
}
