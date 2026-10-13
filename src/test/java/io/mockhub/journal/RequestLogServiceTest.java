package io.mockhub.journal;

import static org.assertj.core.api.Assertions.assertThat;

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
}
