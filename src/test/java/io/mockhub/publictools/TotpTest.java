package io.mockhub.publictools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

class TotpTest {

  private static final String SHA1_KEY = "12345678901234567890";
  private static final String SHA256_KEY = "12345678901234567890123456789012";
  private static final String SHA512_KEY = "1234567890123456789012345678901234567890123456789012345678901234";

  private static String base32(String ascii) {
    byte[] bytes = ascii.getBytes(StandardCharsets.US_ASCII);
    StringBuilder out = new StringBuilder();
    int buffer = 0;
    int bits = 0;
    for (byte b : bytes) {
      buffer = (buffer << 8) | (b & 0xFF);
      bits += 8;
      while (bits >= 5) {
        bits -= 5;
        out.append("ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".charAt((buffer >> bits) & 31));
      }
    }
    if (bits > 0) out.append("ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".charAt((buffer << (5 - bits)) & 31));
    return out.toString();
  }

  private static TotpParams params(String key, String algorithm) {
    return TotpParams.of(new TotpParams.Input(base32(key), null, "8", "30", algorithm));
  }

  @ParameterizedTest
  @CsvSource({
      "59, 94287082, 46119246, 90693936",
      "1111111109, 07081804, 68084774, 25091201",
      "1111111111, 14050471, 67062674, 99943326",
      "1234567890, 89005924, 91819424, 93441116",
      "2000000000, 69279037, 90698825, 38618901",
      "20000000000, 65353130, 77737706, 47863826",
  })
  void matchesTheRfc6238TestVectorsForEveryAlgorithm(long time, String sha1, String sha256, String sha512) {
    // Given
    TotpParams one = params(SHA1_KEY, "SHA1");
    TotpParams two = params(SHA256_KEY, "sha-256");
    TotpParams five = params(SHA512_KEY, "SHA512");

    // When
    String[] codes = {Totp.code(one, time), Totp.code(two, time), Totp.code(five, time)};

    // Then
    assertThat(codes).containsExactly(sha1, sha256, sha512);
  }

  @Test
  void readsAnOtpauthLinkWithItsIssuerAccountAndSettings() {
    // Given
    String uri = "otpauth://totp/ACME%20Co:jane%40example.com?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ&issuer=ACME%20Co&algorithm=SHA256&digits=8&period=60";

    // When
    TotpParams fromUri = TotpParams.of(new TotpParams.Input(null, uri, null, null, null));
    TotpParams pastedAsSecret = TotpParams.of(new TotpParams.Input("  " + uri, null, null, null, null));
    TotpParams overridden = TotpParams.of(new TotpParams.Input(null, uri, "6", null, null));

    // Then
    assertThat(fromUri.issuer()).isEqualTo("ACME Co");
    assertThat(fromUri.account()).isEqualTo("jane@example.com");
    assertThat(fromUri.algorithm()).isEqualTo("SHA256");
    assertThat(fromUri.digits()).isEqualTo(8);
    assertThat(fromUri.period()).isEqualTo(60);
    assertThat(new String(fromUri.key(), StandardCharsets.US_ASCII)).isEqualTo(SHA1_KEY);
    assertThat(pastedAsSecret.account()).isEqualTo("jane@example.com");
    assertThat(overridden.digits()).isEqualTo(6);
  }

  @Test
  void acceptsSecretsWithSpacesLowercaseAndPaddingAndUsesTheUsualDefaults() {
    // Given
    TotpParams spaced = TotpParams.of(new TotpParams.Input("gezd gnbv gy3t qojq gezd gnbv gy3t qojq====", null, null, null, null));

    // When
    String code = Totp.code(spaced, 59);

    // Then
    assertThat(spaced.digits()).isEqualTo(6);
    assertThat(spaced.period()).isEqualTo(30);
    assertThat(spaced.algorithm()).isEqualTo("SHA1");
    assertThat(code).isEqualTo("287082");
    assertThat(Base64.getEncoder().encodeToString(spaced.key())).isEqualTo(Base64.getEncoder().encodeToString(SHA1_KEY.getBytes(StandardCharsets.US_ASCII)));
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {
      "'' | '' | Send a Base32 secret or an otpauth:// link",
      "JBSWY3DP1 | '' | must be Base32",
      "'' | otpauth://hotp/x?secret=JBSWY3DP&counter=1 | not counter-based HOTP",
      "'' | otpauth://totp/x?issuer=a | has no secret",
      "'' | https://example.com/?secret=JBSWY3DP | must start with otpauth://",
  })
  void refusesSecretsAndLinksItCannotUse(String secret, String uri, String message) {
    // Given
    TotpParams.Input input = new TotpParams.Input(secret, uri, null, null, null);

    // When / Then
    assertThatThrownBy(() -> TotpParams.of(input)).hasMessageContaining(message);
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {
      "5 | 30 | SHA1 | digits must be 6, 7 or 8",
      "6 | 5 | SHA1 | period must be between 10 and 300",
      "6 | 30 | MD5 | algorithm must be SHA1, SHA256 or SHA512",
      "six | 30 | SHA1 | digits must be a whole number",
  })
  void refusesSettingsOutsideWhatAuthenticatorAppsUse(String digits, String period, String algorithm, String message) {
    // Given
    TotpParams.Input input = new TotpParams.Input("JBSWY3DPEHPK3PXP", null, digits, period, algorithm);

    // When / Then
    assertThatThrownBy(() -> TotpParams.of(input)).hasMessageContaining(message);
  }
}
