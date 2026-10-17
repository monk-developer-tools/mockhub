package io.mockhub.chaos;

import static io.mockhub.support.Fixtures.stub;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.http.Fault;
import org.junit.jupiter.api.Test;

class ErrorRateTest {

  @Test
  void readsTheRateFromStubMetadata() {
    // Given
    var mapping = stub("{'request':{'urlPath':'/a'},'metadata':{'mockhub':{'errorRate':{'percent':25,'status':503,'body':'down'}}}}");

    // When
    ErrorRate rate = ErrorRate.of(mapping);

    // Then
    assertThat(rate).isEqualTo(new ErrorRate(25, 503, "down", null));
  }

  @Test
  void ignoresMissingOrZeroRates() {
    // When / Then
    assertThat(ErrorRate.of(stub("{'request':{'urlPath':'/a'}}"))).isNull();
    assertThat(ErrorRate.of(stub("{'request':{'urlPath':'/a'},'metadata':{'mockhub':{'errorRate':{'percent':0}}}}"))).isNull();
  }

  @Test
  void fallsBackToStatus500AndReadsFaults() {
    // Given
    var mapping = stub("{'request':{'urlPath':'/a'},'metadata':{'mockhub':{'errorRate':{'percent':'150','status':42,'fault':'CONNECTION_RESET_BY_PEER'}}}}");

    // When
    ErrorRate rate = ErrorRate.of(mapping);

    // Then
    assertThat(rate.percent()).isEqualTo(100);
    assertThat(rate.status()).isEqualTo(500);
    assertThat(rate.fault()).isEqualTo(Fault.CONNECTION_RESET_BY_PEER);
  }

  @Test
  void hitsWhenTheRandomNumberFallsUnderTheRate() {
    // Given
    ErrorRate rate = new ErrorRate(10, 500, null, null);

    // When / Then
    assertThat(rate.hits(0.05)).isTrue();
    assertThat(rate.hits(0.10)).isFalse();
    assertThat(rate.hits(0.99)).isFalse();
  }
}
