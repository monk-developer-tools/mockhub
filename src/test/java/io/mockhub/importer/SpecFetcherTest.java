package io.mockhub.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.mockhub.api.ApiException;
import io.mockhub.playground.Header;
import io.mockhub.playground.HttpSender;
import io.mockhub.playground.TargetGuard;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SpecFetcherTest {

  private final Map<String, HttpSender.Raw> answers = new HashMap<>();
  private final List<String> called = new ArrayList<>();
  private final HttpSender sender = (uri, method, headers, body, timeout, max) -> {
    called.add(uri.toString());
    HttpSender.Raw raw = answers.get(uri.toString());
    if (raw == null) throw new java.io.IOException("connection refused");
    return raw;
  };
  private final TargetGuard publicOnly = new TargetGuard(false, host -> new InetAddress[] {
      InetAddress.getByAddress(host, host.startsWith("internal") ? new byte[] {10, 0, 0, 1} : new byte[] {93, (byte) 184, (byte) 215, 14})});
  private final SpecFetcher fetcher = new SpecFetcher(sender, publicOnly);

  private static HttpSender.Raw ok(String body) {
    return new HttpSender.Raw(200, List.of(), body.getBytes(StandardCharsets.UTF_8), false);
  }

  private static HttpSender.Raw redirect(String location) {
    return new HttpSender.Raw(302, List.of(new Header("Location", location)), new byte[0], false);
  }

  @Test
  void downloadsTheDescriptionAndFollowsRedirects() {
    // Given
    answers.put("https://api.example.com/spec", redirect("/v2/openapi.yaml"));
    answers.put("https://api.example.com/v2/openapi.yaml", ok("openapi: 3.0.0"));

    // When
    String content = fetcher.fetch(" https://api.example.com/spec ");

    // Then
    assertThat(content).isEqualTo("openapi: 3.0.0");
    assertThat(called).containsExactly("https://api.example.com/spec", "https://api.example.com/v2/openapi.yaml");
  }

  @Test
  void checksEveryRedirectAgainstPrivateAddresses() {
    // Given
    answers.put("https://api.example.com/spec", redirect("http://internal.example.com/secret"));

    // When / Then
    assertThatThrownBy(() -> fetcher.fetch("https://api.example.com/spec"))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("private or local address");
    assertThat(called).containsExactly("https://api.example.com/spec");
  }

  @Test
  void stopsAfterThreeRedirects() {
    // Given
    answers.put("https://api.example.com/a", redirect("/b"));
    answers.put("https://api.example.com/b", redirect("/c"));
    answers.put("https://api.example.com/c", redirect("/d"));
    answers.put("https://api.example.com/d", redirect("/e"));

    // When / Then
    assertThatThrownBy(() -> fetcher.fetch("https://api.example.com/a")).hasMessageContaining("redirected more than 3 times");
  }

  @Test
  void explainsFailedDownloads() {
    // Given
    answers.put("https://api.example.com/missing", new HttpSender.Raw(404, List.of(), new byte[0], false));
    answers.put("https://api.example.com/huge", new HttpSender.Raw(200, List.of(), new byte[10], true));

    // When / Then
    assertThatThrownBy(() -> fetcher.fetch("https://api.example.com/missing")).hasMessageContaining("answered 404");
    assertThatThrownBy(() -> fetcher.fetch("https://api.example.com/huge")).hasMessageContaining("larger than 5 MB");
    assertThatThrownBy(() -> fetcher.fetch("https://api.example.com/down")).hasMessageContaining("Couldn't download");
    assertThatThrownBy(() -> fetcher.fetch("ftp://api.example.com/x")).hasMessageContaining("Only http and https");
    assertThatThrownBy(() -> fetcher.fetch("")).hasMessageContaining("Give the URL");
  }
}
