package io.mockhub.journal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RequestLogServiceTest {

  @ParameterizedTest
  @CsvSource({
      "/pay, true",
      "/pay/x, true",
      "/pay?x=1, true",
      "/payments, false",
      "/other/pay, false"
  })
  void decidesWhetherAUrlBelongsToAProject(String url, boolean under) {
    assertThat(RequestLogService.isUnder(url, "/pay")).isEqualTo(under);
  }

  @Test
  void bucketsRecentRequestsIntoActivityCounts() {
    // Given
    long now = 10_000_000L;
    List<Long> timestamps = List.of(now - 1_000, now - 2_000, now - 7 * 60_000, now - 90 * 60_000, now + 5_000);

    // When
    int[] activity = RequestLogService.activity(timestamps, now, 12, 5 * 60_000);

    // Then
    assertThat(activity).hasSize(12);
    assertThat(activity[11]).isEqualTo(2);
    assertThat(activity[10]).isEqualTo(1);
    assertThat(Arrays.stream(activity).sum()).isEqualTo(3);
  }
}
