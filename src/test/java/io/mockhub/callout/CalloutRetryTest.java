package io.mockhub.callout;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CalloutRetryTest {

  @Test
  void readsCountBackoffAndStatusesFromTheStubParameters() {
    // Given
    Map<String, Object> raw = Map.of("count", 3, "backoffMs", 200, "statuses", List.of("503", "429"));

    // When
    CalloutRetry retry = CalloutRetry.from(raw);

    // Then
    assertThat(retry.count()).isEqualTo(3);
    assertThat(retry.backoffMs()).isEqualTo(200);
    assertThat(retry.retriesStatus(503)).isTrue();
    assertThat(retry.retriesStatus(429)).isTrue();
    assertThat(retry.retriesStatus(500)).isFalse();
  }

  @Test
  void defaultsToRetryingTooManyRequestsAndServerErrors() {
    // Given
    CalloutRetry retry = CalloutRetry.from(2);

    // When
    boolean serverError = retry.retriesStatus(502);
    boolean clientError = retry.retriesStatus(404);

    // Then
    assertThat(retry.count()).isEqualTo(2);
    assertThat(retry.backoffMs()).isEqualTo(CalloutRetry.DEFAULT_BACKOFF_MS);
    assertThat(serverError).isTrue();
    assertThat(retry.retriesStatus(429)).isTrue();
    assertThat(clientError).isFalse();
  }

  @Test
  void capsTheCountAndDoublesTheWaitUpToTheLimit() {
    // Given
    CalloutRetry retry = new CalloutRetry(50, 10_000, null);

    // When
    long beforeSecond = retry.backoffBefore(2);
    long beforeThird = retry.backoffBefore(3);
    long beforeSixth = retry.backoffBefore(6);

    // Then
    assertThat(retry.count()).isEqualTo(CalloutRetry.MAX_COUNT);
    assertThat(beforeSecond).isEqualTo(10_000);
    assertThat(beforeThird).isEqualTo(20_000);
    assertThat(beforeSixth).isEqualTo(CalloutRetry.MAX_BACKOFF_MS);
  }

  @Test
  void meansNoRetriesWhenTheStubSaysNothing() {
    // Given
    Object missing = null;

    // When
    CalloutRetry retry = CalloutRetry.from(missing);

    // Then
    assertThat(retry.count()).isZero();
  }
}
