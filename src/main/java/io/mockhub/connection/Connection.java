package io.mockhub.connection;

public record Connection(String orgId, String key, String name, ConnectionType type, String tokenUrl, String clientId,
    String scope, String audience, boolean basicAuth, String headerName, String headerPrefix, String secret,
    String createdAt, String updatedAt) {

  public static final String DEFAULT_HEADER = "Authorization";
  public static final String DEFAULT_PREFIX = "Bearer ";

  public Connection {
    type = type == null ? ConnectionType.OAUTH2 : type;
    tokenUrl = blank(tokenUrl);
    clientId = blank(clientId);
    scope = blank(scope);
    audience = blank(audience);
    headerName = headerName == null || headerName.isBlank() ? DEFAULT_HEADER : headerName.trim();
    headerPrefix = headerPrefix == null ? (type == ConnectionType.OAUTH2 ? DEFAULT_PREFIX : "") : headerPrefix;
    secret = secret == null ? "" : secret;
  }

  public boolean hasSecret() {
    return !secret.isEmpty();
  }

  public Connection withSecret(String sealed) {
    return new Connection(orgId, key, name, type, tokenUrl, clientId, scope, audience, basicAuth, headerName, headerPrefix, sealed, createdAt, updatedAt);
  }

  public Connection inOrg(String otherOrg) {
    return new Connection(otherOrg, key, name, type, tokenUrl, clientId, scope, audience, basicAuth, headerName, headerPrefix, secret, createdAt, updatedAt);
  }

  private static String blank(String value) {
    return value == null ? "" : value.trim();
  }
}
