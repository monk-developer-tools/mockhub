package io.mockhub.tls;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.security.SecureRandom;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TlsService {
  private static final Logger LOG = LoggerFactory.getLogger(TlsService.class);
  private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

  private final TlsStore store;
  private final char[] password = randomPassword();
  private ServerIdentity server;
  private TrustedClients clients;
  private TlsTarget target;

  public TlsService(TlsStore store) {
    this.store = store;
    try {
      server = store.server().map(ServerIdentity::parse).orElse(null);
    } catch (RuntimeException e) {
      LOG.warn("Ignoring the saved server certificate: {}", e.getMessage());
    }
    try {
      clients = store.clients().map(TrustedClients::parse).orElse(null);
    } catch (RuntimeException e) {
      LOG.warn("Ignoring the saved client CA certificates: {}", e.getMessage());
    }
  }

  public synchronized void attach(TlsTarget httpsServer) {
    target = httpsServer;
    if (server != null || clients != null) apply();
  }

  public synchronized boolean enabled() {
    return target != null && target.enabled();
  }

  public synchronized ObjectNode view() {
    ObjectNode out = JSON.objectNode();
    out.put("httpsEnabled", enabled());
    if (enabled()) out.put("httpsPort", target.httpsPort());
    else out.putNull("httpsPort");
    if (server == null) out.putNull("server");
    else out.set("server", describe(server.leaf()).put("chainLength", server.chain().size()));
    ArrayNode trusted = out.putArray("clientAuthorities");
    if (clients != null) clients.authorities().forEach(c -> trusted.add(describe(c)));
    return out;
  }

  public synchronized ObjectNode setServer(String pem) {
    ServerIdentity next = ServerIdentity.parse(pem);
    change(next, clients);
    store.saveServer(pem);
    return view();
  }

  public synchronized ObjectNode clearServer() {
    change(null, clients);
    store.deleteServer();
    return view();
  }

  public synchronized ObjectNode setClients(String pem) {
    TrustedClients next = TrustedClients.parse(pem);
    change(server, next);
    store.saveClients(pem);
    return view();
  }

  public synchronized ObjectNode clearClients() {
    change(server, null);
    store.deleteClients();
    return view();
  }

  private void change(ServerIdentity nextServer, TrustedClients nextClients) {
    ServerIdentity previousServer = server;
    TrustedClients previousClients = clients;
    server = nextServer;
    clients = nextClients;
    try {
      apply();
    } catch (RuntimeException e) {
      server = previousServer;
      clients = previousClients;
      apply();
      throw new TlsException("The HTTPS port refused the new settings: " + e.getMessage());
    }
  }

  private void apply() {
    if (target == null || !target.enabled()) return;
    target.apply(server == null ? null : server.keyStore(password), password, clients == null ? null : clients.trustStore());
  }

  private static ObjectNode describe(X509Certificate certificate) {
    ObjectNode out = JSON.objectNode()
        .put("subject", certificate.getSubjectX500Principal().getName())
        .put("issuer", certificate.getIssuerX500Principal().getName())
        .put("notBefore", certificate.getNotBefore().toInstant().toString())
        .put("notAfter", certificate.getNotAfter().toInstant().toString());
    ArrayNode names = out.putArray("names");
    names(certificate).forEach(names::add);
    return out;
  }

  private static List<String> names(X509Certificate certificate) {
    try {
      Collection<List<?>> alternatives = certificate.getSubjectAlternativeNames();
      if (alternatives == null) return List.of();
      return alternatives.stream().filter(n -> n.size() > 1 && n.get(1) instanceof String).map(n -> (String) n.get(1)).toList();
    } catch (CertificateParsingException e) {
      return List.of();
    }
  }

  private static char[] randomPassword() {
    byte[] bytes = new byte[18];
    new SecureRandom().nextBytes(bytes);
    return Base64.getEncoder().encodeToString(bytes).toCharArray();
  }
}
