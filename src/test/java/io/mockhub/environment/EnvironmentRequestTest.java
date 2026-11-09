package io.mockhub.environment;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.http.ImmutableRequest;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.RequestMethod;
import org.junit.jupiter.api.Test;

class EnvironmentRequestTest {

  private static Request original(String environment) {
    ImmutableRequest.Builder b = ImmutableRequest.create().withAbsoluteUrl("http://localhost/pay/charges").withMethod(RequestMethod.POST);
    if (environment != null) b.withHeader("x-mockhub-environment", java.util.List.of(environment));
    return b.build();
  }

  @Test
  void replacesTheEnvironmentHeaderWhateverItsCase() {
    // When
    Request wrapped = new EnvironmentRequest(original("prod"), "qa");

    // Then
    assertThat(wrapped.header(ProjectEnvironments.HEADER).values()).containsExactly("qa");
    assertThat(wrapped.getHeaders().all()).hasSize(1);
    assertThat(wrapped.getHeader("Origin")).isNull();
    assertThat(wrapped.containsHeader(ProjectEnvironments.HEADER)).isTrue();
  }
}
