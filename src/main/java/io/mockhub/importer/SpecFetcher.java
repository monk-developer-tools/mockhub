package io.mockhub.importer;

import io.mockhub.api.ApiException;
import io.mockhub.playground.Header;
import io.mockhub.playground.HttpSender;
import io.mockhub.playground.PlaygroundException;
import io.mockhub.playground.TargetGuard;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Set;

public final class SpecFetcher {
  public static final int MAX_BYTES = 5 * 1024 * 1024;
  static final int MAX_REDIRECTS = 3;
  private static final Duration TIMEOUT = Duration.ofSeconds(20);
  private static final Set<Integer> REDIRECTS = Set.of(301, 302, 303, 307, 308);
  private static final List<Header> ACCEPT = List.of(new Header("Accept", "application/json, application/yaml, text/yaml, */*;q=0.5"));

  private final HttpSender http;
  private final TargetGuard guard;

  public SpecFetcher(HttpSender http, TargetGuard guard) {
    this.http = http;
    this.guard = guard;
  }

  public String fetch(String url) {
    return fetch(url, MAX_BYTES);
  }

  public String fetch(String url, int maxBytes) {
    URI uri = parse(url);
    for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
      check(uri);
      HttpSender.Raw response = send(uri, maxBytes);
      if (REDIRECTS.contains(response.status())) {
        uri = uri.resolve(location(response));
        continue;
      }
      if (response.status() != 200) throw new ApiException(502, uri.getHost() + " answered " + response.status() + " for the API description");
      if (response.truncated()) throw ApiException.badRequest("The API description is larger than " + size(maxBytes));
      return new String(response.body(), StandardCharsets.UTF_8);
    }
    throw new ApiException(502, "The API description redirected more than " + MAX_REDIRECTS + " times");
  }

  private static URI parse(String url) {
    if (url == null || url.isBlank()) throw ApiException.badRequest("Give the URL of an OpenAPI or Swagger file");
    try {
      return new URI(url.trim());
    } catch (URISyntaxException e) {
      throw ApiException.badRequest("Not a URL: " + url);
    }
  }

  private void check(URI uri) {
    try {
      guard.check(uri);
    } catch (PlaygroundException e) {
      throw ApiException.badRequest(e.getMessage());
    }
  }

  private static String size(int bytes) {
    return bytes % (1024 * 1024) == 0 ? bytes / (1024 * 1024) + " MB" : bytes / 1024 + " KB";
  }

  private HttpSender.Raw send(URI uri, int maxBytes) {
    try {
      return http.send(uri, "GET", ACCEPT, "", TIMEOUT, maxBytes);
    } catch (Exception e) {
      throw new ApiException(502, "Couldn't download " + uri + ": " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
    }
  }

  private static String location(HttpSender.Raw response) {
    return response.headers().stream().filter(h -> h.name().equalsIgnoreCase("Location")).map(Header::value).findFirst()
        .orElseThrow(() -> new ApiException(502, "A redirect without a Location header"));
  }
}
