package io.mockhub.callout;

import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import io.mockhub.playground.HttpSender;
import io.mockhub.stub.StubMetadata;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public final class CalloutService {
  public static final String ASYNC = "async";
  public static final String SYNC = "sync";
  static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
  static final Duration TIMEOUT = Duration.ofSeconds(30);

  public record Outcome(CalloutEntry entry, byte[] body) {}

  private final CalloutRenderer renderer;
  private final CalloutTargets targets;
  private final HttpSender sender;
  private final CalloutLog log;
  private final Clock clock;

  public CalloutService(CalloutRenderer renderer, CalloutTargets targets, HttpSender sender, CalloutLog log, Clock clock) {
    this.renderer = renderer;
    this.targets = targets;
    this.sender = sender;
    this.log = log;
    this.clock = clock;
  }

  public Outcome call(CalloutSpec spec, ServeEvent event, String mode) {
    StubMapping stub = event.getStubMapping();
    String at = Instant.now(clock).toString();
    CalloutEntry.Trigger trigger = new CalloutEntry.Trigger(event.getRequest().getMethod().getName(), event.getRequest().getUrl());
    OutboundRequest request = renderer.unrendered(spec);
    long started = System.nanoTime();
    CalloutEntry.Result result = null;
    String error = null;
    byte[] body = new byte[0];
    try {
      request = renderer.render(spec, event, SYNC.equals(mode) ? null : event.getResponse());
      CalloutTargets.Target target = targets.resolve(request.url(), event.getRequest());
      HttpSender.Raw raw = sender.send(target.uri(), request.method(), request.headers(), request.body(), TIMEOUT, MAX_RESPONSE_BYTES);
      body = raw.body();
      result = new CalloutEntry.Result(raw.status(), raw.headers(), text(raw.body()), raw.truncated());
    } catch (CalloutException e) {
      error = e.getMessage();
    } catch (Exception e) {
      error = describe(e);
    }
    long ms = Duration.ofNanos(System.nanoTime() - started).toMillis();
    CalloutEntry entry = new CalloutEntry(UUID.randomUUID().toString(), at,
        stub == null ? null : StubMetadata.projectId(stub),
        stub == null || stub.getId() == null ? null : stub.getId().toString(),
        stub == null ? null : stub.getName(),
        mode, trigger, request, result, error, ms, event.getId() == null ? null : event.getId().toString());
    log.add(entry);
    return new Outcome(entry, body);
  }

  static String text(byte[] body) {
    String s = new String(body, StandardCharsets.UTF_8);
    return s.indexOf('\u0000') >= 0 ? "[binary content, " + body.length + " bytes]" : s;
  }

  private static String describe(Exception e) {
    String type = e.getClass().getSimpleName();
    if (type.contains("Timeout")) return "No response within " + TIMEOUT.toSeconds() + " seconds";
    if (type.contains("ConnectException")) return "Couldn't connect: the server refused the connection or isn't reachable";
    if (type.contains("SSL")) return "TLS handshake failed: " + e.getMessage();
    String message = e.getMessage();
    return message == null || message.isBlank() ? "The callout failed (" + type + ")" : message;
  }
}
