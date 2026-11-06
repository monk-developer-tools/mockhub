package io.mockhub.publictools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.mockhub.api.Jsons;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class BinOptionsTest {

  private static BinOptions options(String json) throws Exception {
    return BinOptions.from(Jsons.MAPPER.readTree(json));
  }

  @Test
  void readsEverySettingAndWritesItBack() throws Exception {
    // Given
    String json = "{\"cors\": false, \"latencyMs\": 250, \"rateLimit\": {\"requests\": 5, \"per\": \"Second\"}, \"requiredHeader\": {\"name\": \"X-Api-Key\", \"value\": \"k1\"}}";

    // When
    BinOptions read = options(json);

    // Then
    assertThat(read.cors()).isFalse();
    assertThat(read.latencyMs()).isEqualTo(250);
    assertThat(read.rateLimit()).contains(new BinOptions.RateLimit(5, BinOptions.Per.SECOND));
    assertThat(read.requiredHeader()).contains(new BinOptions.RequiredHeader("X-Api-Key", "k1"));
    assertThat(BinOptions.from(read.toJson())).isEqualTo(read);
    assertThat(options("{}")).isEqualTo(BinOptions.DEFAULT);
    assertThat(BinOptions.from(BinOptions.DEFAULT.toJson())).isEqualTo(BinOptions.DEFAULT);
  }

  @Test
  void refusesSettingsThatCantWork() {
    // Given
    String[] bad = {
        "{\"latencyMs\": 20000}",
        "{\"rateLimit\": {\"requests\": 0}}",
        "{\"rateLimit\": {\"requests\": 3, \"per\": \"week\"}}",
        "{\"requiredHeader\": {\"name\": \"Bad Header\", \"value\": \"x\"}}",
        "{\"requiredHeader\": {\"name\": \"X-Key\", \"value\": \"\"}}"};

    // When / Then
    assertThatThrownBy(() -> options(bad[0])).hasMessageContaining("15 seconds");
    assertThatThrownBy(() -> options(bad[1])).hasMessageContaining("1 to 10000");
    assertThatThrownBy(() -> options(bad[2])).hasMessageContaining("second, minute or hour");
    assertThatThrownBy(() -> options(bad[3])).hasMessageContaining("X-Api-Key");
    assertThatThrownBy(() -> options(bad[4])).hasMessageContaining("1 to 500");
  }

  @Test
  void theGateCountsEachBinInItsOwnWindow() {
    // Given
    AtomicLong now = new AtomicLong(10_000);
    BinGate gate = new BinGate(now::get);
    BinOptions.RateLimit twoAMinute = new BinOptions.RateLimit(2, BinOptions.Per.MINUTE);

    // When
    boolean first = gate.take("bin-a", twoAMinute).isEmpty();
    boolean second = gate.take("bin-a", twoAMinute).isEmpty();
    long wait = gate.take("bin-a", twoAMinute).orElseThrow();
    boolean otherBin = gate.take("bin-b", twoAMinute).isEmpty();
    now.addAndGet(60_000);
    boolean nextWindow = gate.take("bin-a", twoAMinute).isEmpty();
    boolean newLimit = gate.take("bin-a", new BinOptions.RateLimit(1, BinOptions.Per.HOUR)).isEmpty();

    // Then
    assertThat(first).isTrue();
    assertThat(second).isTrue();
    assertThat(wait).isEqualTo(60);
    assertThat(otherBin).isTrue();
    assertThat(nextWindow).isTrue();
    assertThat(newLimit).isTrue();
  }
}
