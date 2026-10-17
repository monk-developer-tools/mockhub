package io.mockhub.playground;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

class TargetGuardTest {

  private static TargetGuard resolvingTo(String ip, boolean allowPrivate) {
    return new TargetGuard(allowPrivate, host -> new InetAddress[] {InetAddress.getByName(ip)});
  }

  @ParameterizedTest
  @CsvSource({
      "127.0.0.1, true", "10.1.2.3, true", "172.16.0.9, true", "192.168.1.1, true", "169.254.169.254, true",
      "100.64.0.1, true", "0.0.0.0, true", "::1, true", "fd00::1, true", "::ffff:10.0.0.1, true",
      "93.184.216.34, false", "8.8.8.8, false", "2606:4700::1111, false"
  })
  void knowsWhichAddressesAreInternal(String ip, boolean internal) throws UnknownHostException {
    assertThat(TargetGuard.isInternal(InetAddress.getByName(ip))).isEqualTo(internal);
  }

  @Test
  void blocksHostsThatResolveToPrivateAddresses() {
    // Given
    TargetGuard guard = resolvingTo("10.0.0.5", false);

    // When / Then
    assertThatThrownBy(() -> guard.check(URI.create("https://intranet.acme/admin")))
        .isInstanceOf(PlaygroundException.class).hasMessageContaining("MOCKHUB_PLAYGROUND_ALLOW_PRIVATE");
  }

  @Test
  void allowsPublicHostsAndPrivateOnesWhenConfigured() {
    // When / Then
    assertThatCode(() -> resolvingTo("93.184.216.34", false).check(URI.create("https://example.com/x"))).doesNotThrowAnyException();
    assertThatCode(() -> resolvingTo("10.0.0.5", true).check(URI.create("http://intranet.acme/x"))).doesNotThrowAnyException();
  }

  @Test
  void rejectsOtherSchemesAndUnknownHosts() {
    // Given
    TargetGuard unknown = new TargetGuard(false, host -> {
      throw new UnknownHostException(host);
    });

    // When / Then
    assertThatThrownBy(() -> resolvingTo("8.8.8.8", true).check(URI.create("file:///etc/passwd"))).hasMessageContaining("Only http and https");
    assertThatThrownBy(() -> unknown.check(URI.create("https://nope.invalid/"))).hasMessageContaining("Can't find the host");
  }
}
