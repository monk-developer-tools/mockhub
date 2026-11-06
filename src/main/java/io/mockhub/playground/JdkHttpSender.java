package io.mockhub.playground;

import java.io.ByteArrayOutputStream;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class JdkHttpSender implements HttpSender {
  private static final Set<String> RESTRICTED = Set.of("connection", "content-length", "expect", "host", "upgrade");

  private final HttpClient client;

  public JdkHttpSender() {
    this(false);
  }

  public JdkHttpSender(boolean skipTlsChecks) {
    HttpClient.Builder builder = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(10));
    if (skipTlsChecks) builder.sslContext(trustingEveryone()).sslParameters(withoutHostnameCheck());
    this.client = builder.build();
  }

  private static SSLContext trustingEveryone() {
    try {
      SSLContext context = SSLContext.getInstance("TLS");
      context.init(null, new TrustManager[] {new X509TrustManager() {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {}

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {}

        @Override
        public X509Certificate[] getAcceptedIssuers() {
          return new X509Certificate[0];
        }
      }}, new SecureRandom());
      return context;
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  private static SSLParameters withoutHostnameCheck() {
    SSLParameters parameters = new SSLParameters();
    parameters.setEndpointIdentificationAlgorithm("");
    return parameters;
  }

  @Override
  public Raw send(URI uri, String method, List<Header> headers, String body, Duration timeout, int maxBytes) throws Exception {
    HttpRequest.BodyPublisher publisher = body.isEmpty() ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body);
    HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(timeout).method(method, publisher);
    for (Header h : headers) {
      if (!RESTRICTED.contains(h.name().toLowerCase())) builder.header(h.name(), h.value() == null ? "" : h.value());
    }
    HttpResponse<InputStream> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
    List<Header> out = new ArrayList<>();
    response.headers().map().forEach((name, values) -> {
      if (!name.startsWith(":")) values.forEach(v -> out.add(new Header(name, v)));
    });
    try (InputStream in = response.body()) {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      byte[] chunk = new byte[8192];
      boolean truncated = false;
      int read;
      while ((read = in.read(chunk)) != -1) {
        int room = maxBytes - buffer.size();
        if (read > room) {
          buffer.write(chunk, 0, Math.max(0, room));
          truncated = true;
          break;
        }
        buffer.write(chunk, 0, read);
      }
      return new Raw(response.statusCode(), out, buffer.toByteArray(), truncated);
    }
  }
}
