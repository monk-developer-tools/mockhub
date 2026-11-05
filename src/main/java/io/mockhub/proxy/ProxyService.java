package io.mockhub.proxy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.api.ApiException;
import io.mockhub.api.Jsons;
import io.mockhub.project.Project;
import io.mockhub.project.ProjectListener;
import io.mockhub.runtime.IpAllowList;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Collections;
import java.util.Optional;
import java.util.function.Function;

public final class ProxyService implements ProjectListener {
  public static final String KEYSTORE_PASSWORD = "password";

  private final boolean enabled;
  private final IpAllowList allowed;
  private final Path keystore;
  private final Path settings;
  private final Function<String, Optional<Project>> projects;
  private volatile String projectId;

  public ProxyService(boolean enabled, IpAllowList allowed, Path keystore, Function<String, Optional<Project>> projects) {
    this.enabled = enabled;
    this.allowed = allowed;
    this.keystore = keystore;
    this.settings = keystore.resolveSibling("settings.json");
    this.projects = projects;
    this.projectId = readProjectId();
  }

  public boolean enabled() {
    return enabled;
  }

  public boolean allows(String clientIp) {
    return allowed.allows(clientIp);
  }

  public Optional<Project> project() {
    String id = projectId;
    return id == null ? Optional.empty() : projects.apply(id);
  }

  public synchronized ObjectNode choose(String id) {
    if (!enabled) throw new ApiException(409, "The proxy is off. Start MockHub with MOCKHUB_PROXY=true to use it");
    if (id != null && !id.isBlank() && projects.apply(id).isEmpty()) throw ApiException.notFound("Project " + id);
    projectId = id == null || id.isBlank() ? null : id;
    write();
    return view();
  }

  public ObjectNode view() {
    ObjectNode out = Jsons.object();
    out.put("enabled", enabled);
    allowed.entries().forEach(out.putArray("allowedNetworks")::add);
    Optional<Project> chosen = project();
    if (chosen.isPresent()) {
      out.putObject("project").put("id", chosen.get().id()).put("name", chosen.get().name()).put("basePath", chosen.get().basePath());
    } else {
      out.putNull("project");
    }
    out.put("caReady", caCertificate().isPresent());
    return out;
  }

  public Optional<X509Certificate> caCertificate() {
    if (!enabled || !Files.isRegularFile(keystore)) return Optional.empty();
    try (InputStream in = Files.newInputStream(keystore)) {
      KeyStore store = KeyStore.getInstance("JKS");
      store.load(in, KEYSTORE_PASSWORD.toCharArray());
      for (String alias : Collections.list(store.aliases())) {
        Certificate cert = store.getCertificate(alias);
        if (cert instanceof X509Certificate x509 && x509.getBasicConstraints() >= 0) return Optional.of(x509);
      }
      return Optional.empty();
    } catch (Exception e) {
      return Optional.empty();
    }
  }

  public String caPem() {
    X509Certificate cert = caCertificate().orElseThrow(() -> ApiException.notFound("Proxy CA certificate"));
    try {
      String body = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(cert.getEncoded());
      return "-----BEGIN CERTIFICATE-----\n" + body + "\n-----END CERTIFICATE-----\n";
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public synchronized void onProjectDeleted(Project project) {
    if (project.id().equals(projectId)) {
      projectId = null;
      write();
    }
  }

  private String readProjectId() {
    if (!Files.isRegularFile(settings)) return null;
    try {
      JsonNode node = Jsons.MAPPER.readTree(settings.toFile());
      String id = node.path("projectId").asText("");
      return id.isBlank() ? null : id;
    } catch (IOException e) {
      return null;
    }
  }

  private void write() {
    try {
      Files.createDirectories(settings.getParent());
      ObjectNode node = Jsons.object();
      if (projectId == null) node.putNull("projectId");
      else node.put("projectId", projectId);
      Files.writeString(settings, Jsons.write(node));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
