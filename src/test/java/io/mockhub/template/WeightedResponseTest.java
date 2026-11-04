package io.mockhub.template;

import static org.assertj.core.api.Assertions.assertThat;

import io.mockhub.api.Jsons;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class WeightedResponseTest {

  @Test
  void readsResponsesSkippingThoseWithoutWeight() throws Exception {
    // Given
    var parameters = Jsons.MAPPER.readTree("""
        {"responses":[{"weight":3,"status":200,"jsonBody":{"ok":true}},{"weight":0,"status":418},
         {"status":503,"body":"busy","headers":{"Retry-After":"5"}}]}""");

    // When
    List<WeightedResponse> choices = WeightedResponse.from(parameters);

    // Then
    assertThat(choices).hasSize(2);
    assertThat(choices.get(0).body()).isEqualTo("{\"ok\":true}");
    assertThat(choices.get(1).weight()).isEqualTo(1);
    assertThat(choices.get(1).headers()).containsEntry("Retry-After", "5");
  }

  @ParameterizedTest
  @CsvSource({"0.0,0", "0.74,0", "0.75,1", "0.999,1"})
  void picksInProportionToWeight(double random, int expected) {
    // Given
    List<WeightedResponse> choices = List.of(new WeightedResponse(3, 200, "ok", Map.of()), new WeightedResponse(1, 503, "busy", Map.of()));

    // When
    int picked = WeightedResponse.pick(choices, random);

    // Then
    assertThat(picked).isEqualTo(expected);
  }
}
