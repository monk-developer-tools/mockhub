package io.mockhub.connection;

import com.fasterxml.jackson.databind.JsonNode;
import io.mockhub.api.Jsons;
import io.mockhub.playground.Header;
import io.mockhub.playground.HttpSender;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class TokenClient {
  public static final long DEFAULT_EXPIRY_SECONDS = 300;
  private static final Duration TIMEOUT = Duration.ofSeconds(15);
  private static final int MAX_BYTES = 256 * 1024;

  public record Token(String value, String type, long expiresInSeconds, String scope) {}

  private final HttpSender sender;
  private final Function<String, URI> resolve;

  public TokenClient(HttpSender sender, Function<String, URI> resolve) {
    this.sender = sender;
    this.resolve = resolve;
  }

  public Token fetch(Connection c, String secret) {
    URI uri;
    try {
      uri = resolve.apply(c.tokenUrl());
    } catch (RuntimeException e) {
      throw new ConnectionException("Can't call the token URL: " + e.getMessage());
    }
    Map<String, String> form = new LinkedHashMap<>();
    form.put("grant_type", "client_credentials");
    if (!c.scope().isEmpty()) form.put("scope", c.scope());
    if (!c.audience().isEmpty()) form.put("audience", c.audience());
    List<Header> headers = new ArrayList<>();
    headers.add(new Header("Content-Type", "application/x-www-form-urlencoded"));
    headers.add(new Header("Accept", "application/json"));
    if (c.basicAuth()) {
      String pair = encode(c.clientId()) + ":" + encode(secret);
      headers.add(new Header("Authorization", "Basic " + Base64.getEncoder().encodeToString(pair.getBytes(StandardCharsets.UTF_8))));
    } else {
      form.put("client_id", c.clientId());
      form.put("client_secret", secret);
    }
    String body = form.entrySet().stream().map(e -> encode(e.getKey()) + "=" + encode(e.getValue())).collect(Collectors.joining("&"));
    HttpSender.Raw raw;
    try {
      raw = sender.send(uri, "POST", headers, body, TIMEOUT, MAX_BYTES);
    } catch (Exception e) {
      throw new ConnectionException("The token request failed: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
    }
    String text = new String(raw.body(), StandardCharsets.UTF_8);
    if (raw.status() >= 400) throw new ConnectionException("The token endpoint answered " + raw.status() + ": " + shorten(text));
    JsonNode json;
    try {
      json = Jsons.MAPPER.readTree(text);
    } catch (Exception e) {
      throw new ConnectionException("The token endpoint didn't answer with JSON: " + shorten(text));
    }
    String token = json == null ? "" : json.path("access_token").asText("");
    if (token.isBlank()) throw new ConnectionException("The token endpoint's answer has no access_token: " + shorten(text));
    long expires = json.path("expires_in").asLong(DEFAULT_EXPIRY_SECONDS);
    return new Token(token, json.path("token_type").asText("Bearer"), expires > 0 ? expires : DEFAULT_EXPIRY_SECONDS, json.path("scope").asText(""));
  }

  private static String encode(String value) {
    return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
  }

  private static String shorten(String text) {
    String flat = text.replaceAll("\\s+", " ").trim();
    return flat.length() <= 200 ? flat : flat.substring(0, 200) + "…";
  }
}
