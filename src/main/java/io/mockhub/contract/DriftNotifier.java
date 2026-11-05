package io.mockhub.contract;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.api.Jsons;
import io.mockhub.playground.Header;
import io.mockhub.playground.HttpSender;
import io.mockhub.playground.TargetGuard;
import java.net.URI;
import java.time.Duration;
import java.util.List;

public class DriftNotifier {
  static final int MAX_LISTED = 20;
  private static final Duration TIMEOUT = Duration.ofSeconds(10);

  private final HttpSender sender;
  private final TargetGuard guard;

  public DriftNotifier(HttpSender sender, TargetGuard guard) {
    this.sender = sender;
    this.guard = guard;
  }

  public void checkUrl(String url) {
    URI uri;
    try {
      uri = URI.create(url);
    } catch (IllegalArgumentException e) {
      throw io.mockhub.api.ApiException.badRequest("Enter a full http:// or https:// URL");
    }
    if (!"http".equals(uri.getScheme()) && !"https".equals(uri.getScheme()) || uri.getHost() == null) {
      throw io.mockhub.api.ApiException.badRequest("Enter a full http:// or https:// URL");
    }
    guard.check(uri);
  }

  public DriftState.Notice notify(String url, String at, String projectId, String projectName, String driftUrl, List<DriftState.Finding> fresh) {
    ObjectNode body = Jsons.object();
    body.put("event", "contract.drift");
    body.put("at", at);
    body.putObject("project").put("id", projectId).put("name", projectName);
    body.put("newFindings", fresh.size());
    if (driftUrl != null) body.put("url", driftUrl);
    ArrayNode list = body.putArray("findings");
    fresh.stream().limit(MAX_LISTED).forEach(f -> list.addObject()
        .put("kind", f.kind().name())
        .put("label", f.kind().label())
        .put("method", f.method())
        .put("path", f.path())
        .put("detail", f.detail())
        .put("count", f.count()));
    try {
      URI uri = URI.create(url);
      guard.check(uri);
      HttpSender.Raw answer = sender.send(uri, "POST", List.of(new Header("Content-Type", "application/json"),
          new Header("User-Agent", "MockHub contract drift")), Jsons.write(body), TIMEOUT, 4096);
      return new DriftState.Notice(at, fresh.size(), answer.status(), answer.status() >= 400 ? "The webhook answered " + answer.status() : null);
    } catch (Exception e) {
      return new DriftState.Notice(at, fresh.size(), 0, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
    }
  }
}
