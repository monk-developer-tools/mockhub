package io.mockhub.sso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.support.FakeHttpTransport;
import io.mockhub.support.MutableClock;
import io.mockhub.support.TestIdp;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class JwtVerifierTest {

  private static final String AUTHORITY = "https://login.example";
  private static final String JWKS = AUTHORITY + "/keys";

  private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
  private final TestIdp idp = new TestIdp();
  private final FakeHttpTransport http = new FakeHttpTransport().onGet(JWKS, idp.jwks());
  private final JwtVerifier verifier = new JwtVerifier(http, clock);
  private final JwtVerifier.Expectations expected =
      new JwtVerifier.Expectations(iss -> iss.equals(AUTHORITY + "/" + TestIdp.TENANT + "/v2.0"), TestIdp.CLIENT, "n-1");

  private ObjectNode claims() {
    return idp.claims(AUTHORITY, "n-1", clock.millis() / 1000);
  }

  @Test
  void acceptsAValidTokenAndCachesTheKeys() {
    // When
    verifier.verify(idp.sign(claims()), JWKS, expected);
    var result = verifier.verify(idp.sign(claims()), JWKS, expected);

    // Then
    assertThat(result.path("oid").asText()).isEqualTo("user-oid-1");
    assertThat(http.getCount).isEqualTo(1);
  }

  @Test
  void rejectsTokensSignedByAnotherKey() {
    // Given
    String forged = new TestIdp().sign(claims());

    // Then
    assertThatThrownBy(() -> verifier.verify(forged, JWKS, expected)).hasMessageContaining("signature");
  }

  @Test
  void rejectsWrongIssuerAudienceNonceAndExpiry() {
    // Then
    assertThatThrownBy(() -> verifier.verify(idp.sign(claims().put("iss", "https://evil/v2.0")), JWKS, expected)).hasMessageContaining("issuer");
    assertThatThrownBy(() -> verifier.verify(idp.sign(claims().put("aud", "other")), JWKS, expected)).hasMessageContaining("another application");
    assertThatThrownBy(() -> verifier.verify(idp.sign(claims().put("nonce", "n-2")), JWKS, expected)).hasMessageContaining("browser session");
    assertThatThrownBy(() -> verifier.verify(idp.sign(claims().put("exp", clock.millis() / 1000 - 600)), JWKS, expected)).hasMessageContaining("expired");
  }

  @Test
  void rejectsUnknownKeysMalformedTokensAndOtherAlgorithms() {
    // Then
    assertThatThrownBy(() -> verifier.verify(idp.sign(claims(), "other-kid"), JWKS, expected)).hasMessageContaining("No signing key");
    assertThatThrownBy(() -> verifier.verify("abc", JWKS, expected)).hasMessageContaining("malformed");
    String none = "eyJhbGciOiJub25lIn0." + idp.sign(claims()).split("\\.")[1] + ".";
    assertThatThrownBy(() -> verifier.verify(none + "x", JWKS, expected)).hasMessageContaining("algorithm");
  }
}
