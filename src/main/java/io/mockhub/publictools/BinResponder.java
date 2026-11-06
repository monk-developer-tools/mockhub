package io.mockhub.publictools;

import io.mockhub.api.Jsons;
import io.mockhub.playground.Header;
import io.mockhub.playground.HttpSender;
import io.mockhub.playground.PlaygroundException;
import io.mockhub.playground.TargetGuard;
import io.mockhub.tunnel.TunnelException;
import io.mockhub.tunnel.TunnelGateway;
import io.mockhub.tunnel.TunnelMessages;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntUnaryOperator;
import java.util.function.LongSupplier;

final class BinResponder {
  static final Duration UPSTREAM_TIMEOUT = Duration.ofSeconds(15);
  static final int MAX_UPSTREAM_BODY = 1024 * 1024;
  private static final Set<String> HOP = Set.of("connection", "keep-alive", "proxy-authenticate", "proxy-authorization", "te", "trailer",
      "transfer-encoding", "upgrade", "host", "content-length", "x-real-ip", "forwarded");

  private final TemplatePlayground templates;
  private final HttpSender secure;
  private final HttpSender insecure;
  private final TargetGuard guard;
  private final TunnelGateway tunnels;
  private final IntUnaryOperator dice;
  private final LongSupplier clock;

  BinResponder(TemplatePlayground templates, HttpSender secure, HttpSender insecure, TargetGuard guard, TunnelGateway tunnels,
      IntUnaryOperator dice, LongSupplier clock) {
    this.templates = templates;
    this.secure = secure;
    this.insecure = insecure;
    this.guard = guard;
    this.tunnels = tunnels;
    this.dice = dice;
    this.clock = clock;
  }

  record Answer(int status, Map<String, String> headers, byte[] body, int delayMs, String by, String rule) {}

  Answer answer(String binId, Bins.Settings settings, BinMatcher.Call call) {
    if (settings.rulesEnabled()) {
      Optional<BinRule> rule = BinMatcher.first(settings.rules(), call);
      if (rule.isPresent()) return fromRule(rule.get(), call);
    }
    BinRouting routing = settings.routing();
    return switch (routing.mode()) {
      case FORWARD -> forward(routing, call);
      case TUNNEL -> tunnel(binId, routing, call);
      case MOCK -> fallback(settings.reply());
    };
  }

  private Answer fromRule(BinRule rule, BinMatcher.Call call) {
    BinRule.Answer chosen = pick(rule.answers());
    Map<String, String> headers = new LinkedHashMap<>(chosen.headers());
    String body = chosen.body();
    if (chosen.template()) {
      TemplatePlayground.Sample sample = new TemplatePlayground.Sample(call.method(), call.path() + query(call), call.request().headers(),
          Bins.cut(Optional.ofNullable(call.request().body()).orElse(""), TemplatePlayground.MAX_BODY));
      TemplatePlayground.Result result = templates.render(body.isEmpty() ? " " : body, sample);
      if (result.error() != null) {
        return new Answer(500, Map.of("Content-Type", "application/json"), errorJson("The rule's template failed: " + result.error()), 0, "error", rule.label());
      }
      body = result.output();
      for (Map.Entry<String, String> e : chosen.headers().entrySet()) {
        if (!e.getValue().contains("{{")) continue;
        TemplatePlayground.Result h = templates.render(e.getValue(), sample);
        headers.put(e.getKey(), h.error() == null ? h.output().strip() : e.getValue());
      }
    }
    if (headers.keySet().stream().noneMatch(k -> k.equalsIgnoreCase("Content-Type"))) headers.put("Content-Type", guessType(body));
    return new Answer(chosen.status(), headers, body.getBytes(StandardCharsets.UTF_8), chosen.delayMs(), "rule", rule.label());
  }

  private BinRule.Answer pick(List<BinRule.Answer> answers) {
    if (answers.size() == 1) return answers.get(0);
    int total = answers.stream().mapToInt(BinRule.Answer::weight).sum();
    int roll = dice.applyAsInt(total);
    for (BinRule.Answer a : answers) {
      roll -= a.weight();
      if (roll < 0) return a;
    }
    return answers.get(answers.size() - 1);
  }

  private Answer forward(BinRouting routing, BinMatcher.Call call) {
    URI uri;
    try {
      uri = routing.forwardUri(call.path(), call.request().rawQuery());
      guard.check(uri);
    } catch (PlaygroundException | IllegalArgumentException e) {
      String why = e.getMessage() != null && e.getMessage().contains("private or local") ? routing.target() + " is a private or local address; bins forward only to public ones" : e.getMessage();
      return failed(502, "forward", "Can't forward: " + why);
    }
    long started = clock.getAsLong();
    try {
      HttpSender.Raw raw = (routing.insecure() ? insecure : secure).send(uri, call.method(), outgoing(call, routing), Optional.ofNullable(call.request().body()).orElse(""),
          UPSTREAM_TIMEOUT, MAX_UPSTREAM_BODY);
      Map<String, String> headers = incoming(raw.headers(), routing);
      return new Answer(raw.status(), headers, raw.body(), 0, "forward", uri.getHost());
    } catch (Exception e) {
      long took = clock.getAsLong() - started;
      String why = took >= UPSTREAM_TIMEOUT.toMillis() ? "it didn't answer within 15 seconds" : String.valueOf(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
      return failed(502, "forward", "Forwarding to " + uri.getHost() + " failed: " + why);
    }
  }

  private Answer tunnel(String binId, BinRouting routing, BinMatcher.Call call) {
    URI uri = URI.create("tunnel://" + tunnelName(binId) + routing.pathPrefix() + call.path() + query(call));
    try {
      byte[] body = Optional.ofNullable(call.request().body()).orElse("").getBytes(StandardCharsets.UTF_8);
      TunnelMessages.Response r = tunnels.send(projectKey(binId), uri, call.method(), outgoing(call, routing), body, UPSTREAM_TIMEOUT);
      return new Answer(r.status(), incoming(r.headers(), routing), r.body(), 0, "tunnel", tunnelName(binId));
    } catch (TunnelException e) {
      return failed(e.status() >= 500 ? e.status() : 502, "tunnel", e.getMessage());
    }
  }

  private static Answer fallback(Bins.Reply reply) {
    return new Answer(reply.status(), Map.of("Content-Type", reply.contentType()), reply.body().getBytes(StandardCharsets.UTF_8), 0, "default", "");
  }

  private static Answer failed(int status, String by, String message) {
    return new Answer(status, Map.of("Content-Type", "application/json"), errorJson(message), 0, by + "-error", "");
  }

  private static byte[] errorJson(String message) {
    return Jsons.write(Jsons.object().put("error", message)).getBytes(StandardCharsets.UTF_8);
  }

  private static List<Header> outgoing(BinMatcher.Call call, BinRouting routing) {
    List<Header> out = new ArrayList<>();
    call.request().headers().forEach((name, value) -> {
      String lower = name.toLowerCase(Locale.ROOT);
      if (!HOP.contains(lower) && !lower.startsWith("x-forwarded-") && !routing.requestHeaders().keySet().stream().anyMatch(k -> k.equalsIgnoreCase(name))) {
        out.add(new Header(name, value));
      }
    });
    routing.requestHeaders().forEach((k, v) -> out.add(new Header(k, v)));
    return out;
  }

  private static Map<String, String> incoming(List<Header> headers, BinRouting routing) {
    Map<String, String> out = new LinkedHashMap<>();
    for (Header h : headers) {
      if (HOP.contains(h.name().toLowerCase(Locale.ROOT))) continue;
      out.merge(h.name(), h.value(), (a, b) -> a + ", " + b);
    }
    routing.responseHeaders().forEach((k, v) -> {
      out.keySet().removeIf(existing -> existing.equalsIgnoreCase(k));
      out.put(k, v);
    });
    return out;
  }

  static String tunnelName(String binId) {
    return "bin-" + binId;
  }

  static String projectKey(String binId) {
    return "bin:" + binId;
  }

  private static String query(BinMatcher.Call call) {
    String q = call.request().rawQuery();
    return q == null || q.isEmpty() ? "" : "?" + q;
  }

  static String guessType(String body) {
    String t = body.strip();
    if (t.startsWith("{") || t.startsWith("[")) return "application/json";
    if (t.startsWith("<")) return t.startsWith("<!") || t.toLowerCase(Locale.ROOT).startsWith("<html") ? "text/html" : "application/xml";
    return "text/plain";
  }
}
