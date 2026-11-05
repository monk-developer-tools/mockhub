package io.mockhub.tls;

import static io.mockhub.tls.TlsFixtures.CA;
import static io.mockhub.tls.TlsFixtures.CLIENT;
import static io.mockhub.tls.TlsFixtures.SERVER;
import static io.mockhub.tls.TlsFixtures.SERVER_PKCS1;
import static io.mockhub.tls.TlsFixtures.certificatesOnly;
import static io.mockhub.tls.TlsFixtures.keyOnly;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.KeyStore;
import org.junit.jupiter.api.Test;

class ServerIdentityTest {

  @Test
  void readsACertificateWithItsPkcs8Key() throws Exception {
    // When
    ServerIdentity identity = ServerIdentity.parse(SERVER);
    KeyStore store = identity.keyStore("pw".toCharArray());

    // Then
    assertThat(identity.leaf().getSubjectX500Principal().getName()).isEqualTo("CN=localhost");
    assertThat(store.isKeyEntry(ServerIdentity.ALIAS)).isTrue();
  }

  @Test
  void readsAnRsaKeyInPkcs1Form() {
    // When
    ServerIdentity identity = ServerIdentity.parse(SERVER_PKCS1);

    // Then
    assertThat(identity.key().getAlgorithm()).isEqualTo("RSA");
  }

  @Test
  void refusesAKeyThatBelongsToAnotherCertificate() {
    // Given
    String mixed = certificatesOnly(SERVER) + keyOnly(CLIENT);

    // When / Then
    assertThatThrownBy(() -> ServerIdentity.parse(mixed)).isInstanceOf(TlsException.class).hasMessageContaining("doesn't belong");
  }

  @Test
  void explainsWhatIsMissing() {
    // When / Then
    assertThatThrownBy(() -> ServerIdentity.parse(certificatesOnly(SERVER))).hasMessageContaining("Add the private key");
    assertThatThrownBy(() -> ServerIdentity.parse(keyOnly(SERVER))).hasMessageContaining("Add the server certificate");
    assertThatThrownBy(() -> ServerIdentity.parse("hello")).hasMessageContaining("No PEM blocks");
    assertThatThrownBy(() -> ServerIdentity.parse("-----BEGIN ENCRYPTED PRIVATE KEY-----\nAAAA\n-----END ENCRYPTED PRIVATE KEY-----"))
        .hasMessageContaining("encrypted");
  }

  @Test
  void trustsEveryCaInThePemText() throws Exception {
    // When
    TrustedClients clients = TrustedClients.parse(CA + certificatesOnly(SERVER));

    // Then
    assertThat(clients.authorities()).hasSize(2);
    assertThat(clients.trustStore().size()).isEqualTo(2);
    assertThatThrownBy(() -> TrustedClients.parse(keyOnly(SERVER))).hasMessageContaining("at least one CA certificate");
  }
}
