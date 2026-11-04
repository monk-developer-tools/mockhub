package io.mockhub.connection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.api.ApiException;
import io.mockhub.api.Jsons;
import io.mockhub.org.Organization;
import io.mockhub.org.OrganizationListener;
import io.mockhub.playground.Header;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class ConnectionService implements OrganizationListener {
  public static final int MAX_CONNECTIONS = 50;
  private static final Pattern KEY = Pattern.compile("[a-z0-9][a-z0-9_-]{0,63}");
  private static final Pattern HEADER_NAME = Pattern.compile("[!#$%&'*+.^_`|~0-9A-Za-z-]{1,100}");
  private static final long REFRESH_MARGIN_SECONDS = 30;

  public record TestResult(String headerName, String preview, String tokenType, long expiresInSeconds, String scope) {}

  private record Cached(String header, Instant expiresAt) {}

  private final ConnectionRepository repository;
  private final SecretBox secrets;
  private final TokenClient tokens;
  private final Clock clock;
  private final Map<String, Cached> cache = new ConcurrentHashMap<>();

  public ConnectionService(ConnectionRepository repository, SecretBox secrets, TokenClient tokens, Clock clock) {
    this.repository = repository;
    this.secrets = secrets;
    this.tokens = tokens;
    this.clock = clock;
  }

  public List<Connection> list(String orgId) {
    return repository.findAll(orgId);
  }

  public Connection get(String orgId, String key) {
    return repository.find(orgId, key).orElseThrow(() -> ApiException.notFound("Connection " + key));
  }

  public synchronized Connection create(String orgId, JsonNode body) {
    if (body == null || !body.isObject()) throw ApiException.badRequest("Send the connection's key, name, type and settings");
    String key = body.path("key").asText("").trim();
    if (!KEY.matcher(key).matches()) throw ApiException.badRequest("The key must be lowercase letters, digits, dashes or underscores, e.g. payments-api");
    if (repository.find(orgId, key).isPresent()) throw new ApiException(409, "A connection called " + key + " already exists");
    if (repository.findAll(orgId).size() >= MAX_CONNECTIONS) throw ApiException.badRequest("An organisation can have up to " + MAX_CONNECTIONS + " connections");
    String now = clock.instant().toString();
    Connection draft = settings(orgId, key, body, null, now, now);
    String secret = body.path("secret").asText("");
    if (secret.isEmpty()) throw ApiException.badRequest(draft.type() == ConnectionType.OAUTH2 ? "Enter the client secret" : "Enter the header value");
    Connection saved = draft.withSecret(secrets.seal(secret));
    repository.save(saved);
    return saved;
  }

  public synchronized Connection update(String orgId, String key, JsonNode body) {
    if (body == null || !body.isObject()) throw ApiException.badRequest("Send the connection's settings");
    Connection current = get(orgId, key);
    Connection draft = settings(orgId, key, body, current, current.createdAt(), clock.instant().toString());
    String secret = body.path("secret").asText("");
    Connection saved = draft.withSecret(secret.isEmpty() ? current.secret() : secrets.seal(secret));
    repository.save(saved);
    cache.remove(cacheKey(orgId, key));
    return saved;
  }

  public synchronized boolean delete(String orgId, String key) {
    cache.remove(cacheKey(orgId, key));
    return repository.delete(orgId, key);
  }

  public TestResult test(String orgId, String key) {
    Connection c = get(orgId, key);
    cache.remove(cacheKey(orgId, key));
    String secret = secretOf(c);
    if (c.type() == ConnectionType.HEADER) return new TestResult(c.headerName(), c.headerPrefix() + mask(secret), "", 0, "");
    TokenClient.Token token = tokens.fetch(c, secret);
    remember(c, token);
    return new TestResult(c.headerName(), c.headerPrefix() + mask(token.value()), token.type(), token.expiresInSeconds(), token.scope());
  }

  public Header header(String orgId, String key) {
    Connection c = repository.find(orgId, key).orElseThrow(() -> new ConnectionException("There is no connection called " + key + " in this organisation"));
    String secret = secretOf(c);
    if (c.type() == ConnectionType.HEADER) return new Header(c.headerName(), c.headerPrefix() + secret);
    Cached cached = cache.get(cacheKey(orgId, key));
    if (cached != null && clock.instant().isBefore(cached.expiresAt())) return new Header(c.headerName(), cached.header());
    synchronized (this) {
      Cached again = cache.get(cacheKey(orgId, key));
      if (again != null && clock.instant().isBefore(again.expiresAt())) return new Header(c.headerName(), again.header());
      return new Header(c.headerName(), remember(c, tokens.fetch(c, secret)));
    }
  }

  public List<Connection> snapshot(String orgId) {
    return repository.findAll(orgId).stream().map(c -> c.withSecret("")).toList();
  }

  public synchronized int restore(String orgId, List<Connection> connections, boolean replaceExisting) {
    int restored = 0;
    for (Connection c : connections) {
      if (c == null || c.key() == null || !KEY.matcher(c.key()).matches()) continue;
      var existing = repository.find(orgId, c.key());
      if (existing.isPresent() && !replaceExisting) continue;
      String keep = existing.map(Connection::secret).orElse("");
      repository.save(c.inOrg(orgId).withSecret(keep));
      cache.remove(cacheKey(orgId, c.key()));
      restored++;
    }
    return restored;
  }

  @Override
  public void onOrganizationDeleted(Organization organization) {
    cache.keySet().removeIf(k -> k.startsWith(organization.id() + "/"));
    repository.deleteOrg(organization.id());
  }

  public static ObjectNode view(Connection c) {
    ObjectNode out = Jsons.object().put("key", c.key()).put("name", c.name()).put("type", c.type().id());
    if (c.type() == ConnectionType.OAUTH2) {
      out.put("tokenUrl", c.tokenUrl()).put("clientId", c.clientId()).put("scope", c.scope()).put("audience", c.audience())
          .put("clientAuth", c.basicAuth() ? "basic" : "body");
    }
    return out.put("headerName", c.headerName()).put("headerPrefix", c.headerPrefix()).put("hasSecret", c.hasSecret())
        .put("createdAt", c.createdAt()).put("updatedAt", c.updatedAt());
  }

  private String remember(Connection c, TokenClient.Token token) {
    String header = c.headerPrefix() + token.value();
    long seconds = Math.max(1, token.expiresInSeconds() - REFRESH_MARGIN_SECONDS);
    cache.put(cacheKey(c.orgId(), c.key()), new Cached(header, clock.instant().plusSeconds(seconds)));
    return header;
  }

  private String secretOf(Connection c) {
    if (!c.hasSecret()) throw new ConnectionException("The connection " + c.key() + " has no secret yet; enter it in its settings");
    try {
      return secrets.open(c.secret());
    } catch (SecretBox.SecretException e) {
      throw new ConnectionException("The connection " + c.key() + ": " + e.getMessage());
    }
  }

  private static Connection settings(String orgId, String key, JsonNode body, Connection current, String createdAt, String updatedAt) {
    ConnectionType type = body.has("type") ? ConnectionType.parse(body.path("type").asText()) : current == null ? ConnectionType.OAUTH2 : current.type();
    String name = text(body, "name", current == null ? key : current.name());
    if (name.length() > 100) throw ApiException.badRequest("Keep the name under 100 characters");
    String headerName = text(body, "headerName", current == null ? Connection.DEFAULT_HEADER : current.headerName());
    if (!HEADER_NAME.matcher(headerName).matches()) throw ApiException.badRequest("The header name can't contain spaces or special characters, e.g. Authorization");
    String prefix = body.has("headerPrefix") ? body.path("headerPrefix").asText("") : current != null ? current.headerPrefix()
        : type == ConnectionType.OAUTH2 ? Connection.DEFAULT_PREFIX : "";
    if (prefix.length() > 50 || prefix.contains("\n") || prefix.contains("\r")) throw ApiException.badRequest("Keep the header prefix short, e.g. \"Bearer \"");
    String tokenUrl = text(body, "tokenUrl", current == null ? "" : current.tokenUrl());
    String clientId = text(body, "clientId", current == null ? "" : current.clientId());
    if (type == ConnectionType.OAUTH2) {
      if (tokenUrl.isEmpty()) throw ApiException.badRequest("Enter the token URL, e.g. https://login.example.com/oauth2/token");
      if (!tokenUrl.startsWith("/")) {
        try {
          URI uri = URI.create(tokenUrl);
          if (uri.getScheme() == null || !(uri.getScheme().equals("https") || uri.getScheme().equals("http")) || uri.getHost() == null) throw new IllegalArgumentException();
        } catch (IllegalArgumentException e) {
          throw ApiException.badRequest("The token URL must start with https:// or http://, or be a path on this server");
        }
      }
      if (clientId.isEmpty()) throw ApiException.badRequest("Enter the client id");
    }
    boolean basic = body.has("clientAuth") ? !"body".equalsIgnoreCase(body.path("clientAuth").asText()) : current == null || current.basicAuth();
    return new Connection(orgId, key, name, type, tokenUrl, clientId, text(body, "scope", current == null ? "" : current.scope()),
        text(body, "audience", current == null ? "" : current.audience()), basic, headerName, prefix, "", createdAt, updatedAt);
  }

  private static String text(JsonNode body, String field, String fallback) {
    return body.has(field) ? body.path(field).asText("").trim() : fallback;
  }

  static String mask(String value) {
    if (value.length() <= 12) return "••••";
    return value.substring(0, 4) + "…" + value.substring(value.length() - 4);
  }

  private static String cacheKey(String orgId, String key) {
    return orgId + "/" + key;
  }
}
