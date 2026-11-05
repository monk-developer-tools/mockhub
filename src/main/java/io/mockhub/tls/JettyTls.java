package io.mockhub.tls;

import com.github.tomakehurst.wiremock.common.HttpsSettings;
import java.security.KeyStore;
import java.util.function.Consumer;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.util.ssl.SslContextFactory;

public final class JettyTls implements TlsTarget {
  private final ServerConnector connector;
  private final HttpsSettings settings;

  public JettyTls(ServerConnector connector, HttpsSettings settings) {
    this.connector = connector;
    this.settings = settings;
  }

  @Override
  public boolean enabled() {
    return connector != null;
  }

  @Override
  public int httpsPort() {
    return connector == null ? -1 : Math.max(connector.getLocalPort(), settings.port());
  }

  @Override
  public void apply(KeyStore identity, char[] password, KeyStore trusted) {
    SslContextFactory.Server ssl = connector.getConnectionFactory(SslConnectionFactory.class).getSslContextFactory();
    Consumer<SslContextFactory> configure = f -> {
      if (identity == null) {
        f.setKeyStore(null);
        f.setKeyStoreType(settings.keyStoreType());
        f.setKeyStorePassword(settings.keyStorePassword());
        f.setKeyManagerPassword(settings.keyManagerPassword());
      } else {
        f.setKeyStore(identity);
        f.setKeyStoreType("PKCS12");
        f.setKeyStorePassword(new String(password));
        f.setKeyManagerPassword(new String(password));
      }
      f.setTrustStore(trusted);
      ((SslContextFactory.Server) f).setWantClientAuth(trusted != null);
      ((SslContextFactory.Server) f).setNeedClientAuth(settings.needClientAuth());
    };
    try {
      if (ssl.isStarted()) ssl.reload(configure);
      else configure.accept(ssl);
    } catch (Exception e) {
      throw new TlsException(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
    }
  }
}
