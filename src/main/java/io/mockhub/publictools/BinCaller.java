package io.mockhub.publictools;

import io.mockhub.playground.Header;
import io.mockhub.playground.HttpSender;
import io.mockhub.playground.PlaygroundException;
import io.mockhub.playground.TargetGuard;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongSupplier;

final class BinCaller {
  static final Duration TIMEOUT = Duration.ofSeconds(15);

  private final TemplatePlayground templates;
  private final HttpSender sender;
  private final TargetGuard guard;
  private final LongSupplier clock;

  BinCaller(TemplatePlayground templates, HttpSender sender, TargetGuard guard, LongSupplier clock) {
    this.templates = templates;
    this.sender = sender;
    this.guard = guard;
    this.clock = clock;
  }

  record Prepared(String method, URI uri, List<Header> headers, String body) {
    String shownUrl() {
      return uri.getScheme() + "://" + uri.getRawAuthority() + Optional.ofNullable(uri.getRawPath()).filter(p -> !p.isEmpty()).orElse("/");
    }
  }

  static final class Unprepared extends Exception {
    Unprepared(String message) {
      super(message);
    }
  }

  record Sent(Bins.Callout shown, int status, Map<String, String> headers, byte[] body) {
    boolean ok() {
      return shown.state().equals("sent");
    }
  }

  Prepared prepare(BinCallout spec, TemplatePlayground.Sample sample, BinState state, BinMatcher.Call call) throws Unprepared {
    String method = spec.method().equals(BinCallout.SAME) ? call.method() : spec.method();
    URI uri = target(render(spec.url(), sample, state, "URL").strip());
    Map<String, String> headers = new LinkedHashMap<>();
    String body;
    if (spec.original()) {
      body = Optional.ofNullable(call.request().body()).orElse("");
      call.request().headers().entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase("Content-Type")).findFirst()
          .ifPresent(e -> headers.put("Content-Type", e.getValue()));
    } else {
      body = render(spec.body(), sample, state, "body");
    }
    for (Map.Entry<String, String> e : spec.headers().entrySet()) {
      headers.keySet().removeIf(k -> k.equalsIgnoreCase(e.getKey()));
      headers.put(e.getKey(), render(e.getValue(), sample, state, e.getKey() + " header").strip());
    }
    if (!body.isEmpty() && headers.keySet().stream().noneMatch(k -> k.equalsIgnoreCase("Content-Type"))) {
      headers.put("Content-Type", BinResponder.guessType(body));
    }
    authHeader(spec.auth()).ifPresent(h -> {
      headers.keySet().removeIf(k -> k.equalsIgnoreCase(h.name()));
      headers.put(h.name(), h.value());
    });
    List<Header> out = new ArrayList<>();
    headers.forEach((k, v) -> out.add(new Header(k, v)));
    out.add(new Header("User-Agent", "MockHub-Bin-Callout"));
    return new Prepared(method, uri, List.copyOf(out), body);
  }

  Sent send(Prepared p, boolean async, long waitMs) {
    String mode = async ? "async" : "sync";
    long started = clock.getAsLong();
    try {
      guard.check(p.uri());
    } catch (PlaygroundException | IllegalArgumentException e) {
      String why = e.getMessage() != null && e.getMessage().contains("private or local") ? p.uri().getHost() + " is a private or local address; callouts go only to public ones" : e.getMessage();
      return new Sent(new Bins.Callout(mode, "failed", p.method(), p.shownUrl(), 0, 0, waitMs, why, ""), 0, Map.of(), new byte[0]);
    }
    try {
      HttpSender.Raw raw = sender.send(p.uri(), p.method(), p.headers(), p.body(), TIMEOUT, BinResponder.MAX_UPSTREAM_BODY);
      long took = clock.getAsLong() - started;
      Map<String, String> headers = new LinkedHashMap<>();
      raw.headers().forEach(h -> headers.merge(h.name(), h.value(), (a, b) -> a + ", " + b));
      String shown = Bins.cut(Bins.readable(raw.body()), Bins.MAX_CALLOUT_BODY);
      return new Sent(new Bins.Callout(mode, "sent", p.method(), p.shownUrl(), raw.status(), took, waitMs, "", shown), raw.status(), headers, raw.body());
    } catch (Exception e) {
      long took = clock.getAsLong() - started;
      String why = took >= TIMEOUT.toMillis() ? "it didn't answer within 15 seconds" : String.valueOf(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
      return new Sent(new Bins.Callout(mode, "failed", p.method(), p.shownUrl(), 0, took, waitMs, p.uri().getHost() + " failed: " + why, ""), 0, Map.of(), new byte[0]);
    }
  }

  private String render(String text, TemplatePlayground.Sample sample, BinState state, String what) throws Unprepared {
    if (!text.contains("{{")) return text;
    TemplatePlayground.Result result = templates.render(text, sample, state);
    if (result.error() != null) throw new Unprepared("The callout " + what + " template failed: " + result.error());
    return result.output();
  }

  private static URI target(String url) throws Unprepared {
    URI uri;
    try {
      uri = URI.create(url);
    } catch (IllegalArgumentException e) {
      throw new Unprepared("The callout URL " + url + " isn't a valid URL");
    }
    String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
    if (!scheme.equals("http") && !scheme.equals("https") || uri.getHost() == null) {
      throw new Unprepared("The callout URL " + url + " needs http:// or https:// and a host");
    }
    return uri;
  }

  static Optional<Header> authHeader(BinCallout.Auth auth) {
    return switch (auth.kind()) {
      case NONE -> Optional.empty();
      case BEARER -> Optional.of(new Header("Authorization", "Bearer " + auth.value()));
      case BASIC -> Optional.of(new Header("Authorization", "Basic " + Base64.getEncoder().encodeToString((auth.name() + ":" + auth.value()).getBytes(StandardCharsets.UTF_8))));
      case HEADER -> Optional.of(new Header(auth.name(), auth.value()));
    };
  }
}
