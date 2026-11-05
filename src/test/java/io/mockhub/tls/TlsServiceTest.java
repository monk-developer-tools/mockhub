package io.mockhub.tls;

import static io.mockhub.tls.TlsFixtures.CA;
import static io.mockhub.tls.TlsFixtures.SERVER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.connection.SecretBox;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TlsServiceTest {
  @TempDir
  Path data;

  private final SecretBox secrets = new SecretBox(new byte[32]);

  static final class RecordingTarget implements TlsTarget {
    final List<KeyStore[]> applied = new ArrayList<>();
    boolean fail;

    @Override
    public boolean enabled() {
      return true;
    }

    @Override
    public int httpsPort() {
      return 8443;
    }

    @Override
    public void apply(KeyStore identity, char[] password, KeyStore trusted) {
      if (fail) {
        fail = false;
        throw new TlsException("boom");
      }
      applied.add(new KeyStore[] {identity, trusted});
    }
  }

  @Test
  void appliesAndDescribesAServerCertificateAndClientCas() {
    // Given
    TlsService tls = new TlsService(new TlsStore(data, secrets));
    RecordingTarget target = new RecordingTarget();
    tls.attach(target);

    // When
    tls.setServer(SERVER);
    ObjectNode view = tls.setClients(CA);

    // Then
    assertThat(target.applied).hasSize(2);
    assertThat(target.applied.get(1)[0]).isNotNull();
    assertThat(target.applied.get(1)[1]).isNotNull();
    assertThat(view.get("httpsPort").asInt()).isEqualTo(8443);
    assertThat(view.at("/server/subject").asText()).isEqualTo("CN=localhost");
    assertThat(view.at("/server/names").toString()).contains("localhost", "127.0.0.1");
    assertThat(view.at("/clientAuthorities/0/subject").asText()).isEqualTo("CN=MockHub Test CA");
  }

  @Test
  void keepsTheKeySealedOnDiskAndLoadsItAgain() throws Exception {
    // Given
    TlsService first = new TlsService(new TlsStore(data, secrets));
    first.attach(new RecordingTarget());
    first.setServer(SERVER);

    // When
    TlsService reloaded = new TlsService(new TlsStore(data, secrets));
    RecordingTarget target = new RecordingTarget();
    reloaded.attach(target);

    // Then
    assertThat(Files.readString(data.resolve("mockhub/tls/server.pem.sealed"))).doesNotContain("PRIVATE KEY");
    assertThat(target.applied).hasSize(1);
    assertThat(reloaded.view().at("/server/subject").asText()).isEqualTo("CN=localhost");
  }

  @Test
  void keepsThePreviousSettingsWhenTheServerRefusesNewOnes() {
    // Given
    TlsService tls = new TlsService(new TlsStore(data, secrets));
    RecordingTarget target = new RecordingTarget();
    tls.attach(target);
    target.fail = true;

    // When / Then
    assertThatThrownBy(() -> tls.setServer(SERVER)).isInstanceOf(TlsException.class).hasMessageContaining("refused");
    assertThat(tls.view().get("server").isNull()).isTrue();
    assertThat(Files.exists(data.resolve("mockhub/tls/server.pem.sealed"))).isFalse();
  }

  @Test
  void removesTheCertificateAndGoesBackToTheDefault() {
    // Given
    TlsService tls = new TlsService(new TlsStore(data, secrets));
    RecordingTarget target = new RecordingTarget();
    tls.attach(target);
    tls.setServer(SERVER);

    // When
    ObjectNode view = tls.clearServer();

    // Then
    assertThat(view.get("server").isNull()).isTrue();
    assertThat(target.applied.get(target.applied.size() - 1)[0]).isNull();
  }
}
