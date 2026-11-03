package io.mockhub.callout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.http.ImmutableRequest;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.RequestMethod;
import io.mockhub.playground.TargetGuard;
import java.net.InetAddress;
import org.junit.jupiter.api.Test;

class CalloutTargetsTest {

  private static final TargetGuard GUARD = new TargetGuard(false, host -> new InetAddress[] {
      InetAddress.getByAddress(host, host.equals("internal.test") ? new byte[] {10, 0, 0, 5} : new byte[] {93, (byte) 184, (byte) 215, 14})
  }, "MOCKHUB_CALLOUT_ALLOW_PRIVATE");

  private final CalloutTargets targets = new CalloutTargets(GUARD, () -> 8080, "https://mocks.company.com");

  private static Request from(String host) {
    return ImmutableRequest.create().withAbsoluteUrl("http://" + host + "/pay").withMethod(RequestMethod.POST).withHeader("Host", host).build();
  }

  @Test
  void sendsPublicUrlsAsTheyAre() {
    // When
    CalloutTargets.Target target = targets.resolve("https://partner.test/cb?x=1", from("mocks.company.com"));

    // Then
    assertThat(target.local()).isFalse();
    assertThat(target.uri().toString()).isEqualTo("https://partner.test/cb?x=1");
  }

  @Test
  void routesCallsToThisServerLocally() {
    // When
    CalloutTargets.Target viaPublicUrl = targets.resolve("https://mocks.company.com/pay/partner/cb", from("other"));
    CalloutTargets.Target viaHost = targets.resolve("http://localhost:9999/pay/cb", from("localhost:9999"));
    CalloutTargets.Target viaPath = targets.resolve("/pay/cb?x=1", from("other"));

    // Then
    assertThat(viaPublicUrl.local()).isTrue();
    assertThat(viaPublicUrl.uri().toString()).isEqualTo("http://127.0.0.1:8080/pay/partner/cb");
    assertThat(viaHost.uri().toString()).isEqualTo("http://127.0.0.1:8080/pay/cb");
    assertThat(viaPath.uri().toString()).isEqualTo("http://127.0.0.1:8080/pay/cb?x=1");
  }

  @Test
  void blocksPrivateAddressesAndTheAdminApi() {
    // When / Then
    assertThatThrownBy(() -> targets.resolve("http://internal.test/secrets", from("mocks.company.com")))
        .isInstanceOf(CalloutException.class)
        .hasMessageContaining("MOCKHUB_CALLOUT_ALLOW_PRIVATE=true");
    assertThatThrownBy(() -> targets.resolve("/__admin/mappings", from("x"))).hasMessageContaining("admin API");
    assertThatThrownBy(() -> targets.resolve("/pay/../__admin/mappings", from("x"))).hasMessageContaining("admin API");
    assertThatThrownBy(() -> targets.resolve("//__ADMIN/mappings", from("x"))).hasMessageContaining("admin API");
    assertThatThrownBy(() -> targets.resolve(" ", from("x"))).hasMessageContaining("no URL");
  }
}
