package io.mockhub.publictools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import io.mockhub.api.Jsons;
import io.mockhub.auth.Principal;
import io.mockhub.playground.JdkHttpSender;
import io.mockhub.playground.TargetGuard;
import io.mockhub.tunnel.TunnelGateway;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BinTool implements PublicTool {
  public static final int MAX_REPLY_BODY = 4 * 1024;
  private static final Pattern CAPTURE = Pattern.compile("/bins/([a-z0-9]{12})(/.*)?");
  private static final Pattern INSPECT = Pattern.compile("/inspect/([a-z0-9]{12})(/requests|/response|/settings|/rules|/routing|/draft)?");

  private final Bins bins;
  private final PublicLimits limits;
  private final BinResponder responder;
  private final BinDrafter drafter;
  private final Function<String, Optional<Boolean>> tunnelOnline;
  private final Function<String, Optional<Principal>> signedIn;
  private final LongSupplier millis;

  BinTool(Bins bins, PublicLimits limits, BinResponder responder, BinDrafter drafter, Function<String, Optional<Boolean>> tunnelOnline,
      Function<String, Optional<Principal>> signedIn, LongSupplier millis) {
    this.bins = bins;
    this.limits = limits;
    this.responder = responder;
    this.drafter = drafter;
    this.tunnelOnline = tunnelOnline;
    this.signedIn = signedIn;
    this.millis = millis;
  }

  public static BinTool create(Bins bins, PublicLimits limits, TemplatePlayground templates, TargetGuard guard, TunnelGateway tunnels,
      Function<String, Optional<Boolean>> tunnelOnline, Function<String, Optional<Principal>> signedIn, BooleanSupplier aiOn, BinDrafter.Llm llm) {
    BinResponder responder = new BinResponder(templates, new JdkHttpSender(), new JdkHttpSender(true), guard, tunnels,
        bound -> ThreadLocalRandom.current().nextInt(bound), System::currentTimeMillis);
    return new BinTool(bins, limits, responder, new BinDrafter(aiOn, llm), tunnelOnline, signedIn, System::currentTimeMillis);
  }

  @Override
  public Optional<ResponseDefinition> handle(PublicRequest request) {
    if (request.path().equals("/bins") && request.method().equals("POST")) return Optional.of(create(request));
    Matcher capture = CAPTURE.matcher(request.path());
    if (capture.matches()) return Optional.of(capture(capture.group(1), request));
    Matcher inspect = INSPECT.matcher(request.path());
    if (inspect.matches()) return Optional.of(inspect(inspect.group(1), inspect.group(2), request));
    return Optional.empty();
  }

  static boolean usesBinRate(String path) {
    return CAPTURE.matcher(path).matches() || INSPECT.matcher(path).matches();
  }

  private ResponseDefinition create(PublicRequest request) {
    Optional<Long> wait = limits.take(request.clientIp(), PublicLimits.Kind.NEW_BIN);
    if (wait.isPresent()) {
      return PublicResponses.refused(wait.get(), "Your address made " + PublicLimits.Kind.NEW_BIN.limit.requests()
          + " bins in the last hour. Open one of them under My bins, or make a new one in " + PublicResponses.inTime(wait.get()) + ".");
    }
    return bins.create()
        .map(c -> PublicResponses.json(201, Jsons.object()
            .put("id", c.id())
            .put("token", c.token())
            .put("url", PublicRequest.PREFIX + "/bins/" + c.id())
            .put("tunnel", BinResponder.tunnelName(c.id()))
            .put("expiresAfterIdleHours", Bins.IDLE.toHours())))
        .orElseGet(() -> PublicResponses.error(503, "The demo holds as many bins as it can right now. Try again later."));
  }

  private ResponseDefinition capture(String id, PublicRequest request) {
    Optional<Bins.Settings> settings = bins.settingsFor(id);
    if (settings.isEmpty()) return PublicResponses.error(404, "No bin " + id + ". Bins are deleted 24 hours after their last request.");
    String rest = request.path().substring(("/bins/" + id).length());
    BinMatcher.Call call = new BinMatcher.Call(request.method(), rest.isEmpty() ? "/" : rest, request);
    long started = millis.getAsLong();
    BinResponder.Answer answer = responder.answer(id, settings.get(), call);
    long took = millis.getAsLong() - started + answer.delayMs();
    String text = new String(answer.body(), StandardCharsets.UTF_8);
    boolean cut = answer.body().length > Bins.MAX_ANSWER_BODY;
    Map<String, String> shown = new LinkedHashMap<>(answer.headers());
    bins.record(id, request, call.path(), BinMatcher.eventOf(request, call.json()),
        new Bins.Outcome(answer.by(), answer.rule(), answer.status(), took, shown, cut ? Bins.cut(text, Bins.MAX_ANSWER_BODY) : text, cut));
    ResponseDefinitionBuilder out = PublicResponses.base(answer.status()).withHeader("X-MockHub-Bin", id);
    answer.headers().forEach(out::withHeader);
    if (answer.delayMs() > 0) out.withFixedDelay(answer.delayMs());
    return out.withBody(answer.body()).build();
  }

  private ResponseDefinition inspect(String id, String part, PublicRequest request) {
    String token = Optional.ofNullable(request.header("X-Bin-Token")).orElse(request.firstQuery("token"));
    String method = request.method();
    String what = part == null ? "/requests" : part;
    boolean reading = what.equals("/requests") && method.equals("GET");
    if (!reading && bins.viewOnly(id, token)) {
      return PublicResponses.error(403, "This is a view-only link. Only the bin's owner can change it.");
    }
    if (part == null && method.equals("DELETE")) {
      return bins.clear(id, token) ? PublicResponses.json(200, Jsons.object().put("cleared", true)) : notYours(id);
    }
    if (reading) {
      long after = parseLong(request.firstQuery("after"));
      long before = parseLong(request.firstQuery("before"));
      int limit = (int) parseLong(request.firstQuery("limit"));
      return bins.requests(id, token, after, before, limit).map(page -> PublicResponses.json(200, listing(id, page))).orElseGet(() -> notYours(id));
    }
    if (what.equals("/response") && method.equals("GET")) {
      return bins.settings(id, token).map(s -> PublicResponses.json(200, reply(s.reply()))).orElseGet(() -> notYours(id));
    }
    if (what.equals("/response") && method.equals("PUT")) return setReply(id, token, request.body());
    if (what.equals("/settings") && method.equals("GET")) {
      return bins.settings(id, token).map(s -> PublicResponses.json(200, settings(id, token, s))).orElseGet(() -> notYours(id));
    }
    if (what.equals("/rules") && method.equals("PUT")) return setRules(id, token, request.body());
    if (what.equals("/routing") && method.equals("PUT")) return setRouting(id, token, request.body());
    if (what.equals("/draft") && method.equals("POST")) return draft(id, token, request);
    return PublicResponses.error(405, "Read a bin with GET, change it with PUT /response, /rules or /routing, or clear it with DELETE");
  }

  private ResponseDefinition setReply(String id, String token, String body) {
    JsonNode node;
    try {
      node = read(body);
    } catch (IllegalArgumentException e) {
      return PublicResponses.error(400, "Send the answer as JSON: {\"status\": 200, \"contentType\": \"application/json\", \"body\": \"...\"}");
    }
    int status = node.path("status").asInt(200);
    if (status < 100 || status > 599) return PublicResponses.error(400, "Use a status from 100 to 599");
    String text = node.path("body").asText("");
    if (text.length() > MAX_REPLY_BODY) return PublicResponses.error(400, "Keep the answer under 4 KB");
    String type = node.path("contentType").asText("application/json");
    Bins.Reply reply = new Bins.Reply(status, type.isBlank() ? "text/plain" : type, text, node.path("template").asBoolean(false));
    return bins.update(id, token, s -> s.withReply(reply)) ? PublicResponses.json(200, reply(reply)) : notYours(id);
  }

  private ResponseDefinition setRules(String id, String token, String body) {
    try {
      JsonNode node = read(body);
      List<BinRule> rules = BinRule.listFrom(node.path("rules"));
      boolean enabled = node.path("rulesEnabled").asBoolean(true);
      if (!bins.update(id, token, s -> s.withRules(enabled, rules))) return notYours(id);
      return bins.settings(id, token).map(s -> PublicResponses.json(200, settings(id, token, s))).orElseGet(() -> notYours(id));
    } catch (IllegalArgumentException e) {
      return PublicResponses.error(400, e.getMessage());
    }
  }

  private ResponseDefinition setRouting(String id, String token, String body) {
    try {
      BinRouting routing = BinRouting.from(read(body));
      if (!bins.update(id, token, s -> s.withRouting(routing))) return notYours(id);
      return bins.settings(id, token).map(s -> PublicResponses.json(200, settings(id, token, s))).orElseGet(() -> notYours(id));
    } catch (IllegalArgumentException e) {
      return PublicResponses.error(400, e.getMessage());
    }
  }

  private ResponseDefinition draft(String id, String token, PublicRequest request) {
    if (!bins.verify(id, token)) return notYours(id);
    if (!drafter.available()) return PublicResponses.error(409, "This server has no AI provider set up.");
    Optional<Principal> who = signedIn.apply(request.header("Authorization"));
    if (who.isEmpty()) return PublicResponses.error(401, "Sign in to MockHub to draft rules with AI.");
    Optional<Long> wait = limits.take("user:" + who.get().id(), PublicLimits.Kind.AI);
    if (wait.isPresent()) return PublicResponses.tooMany(wait.get(), "AI drafts");
    try {
      BinRule rule = drafter.draft(read(request.body()).path("description").asText(""));
      return PublicResponses.json(200, Jsons.object().set("rule", rule.toJson()));
    } catch (IllegalArgumentException e) {
      return PublicResponses.error(400, e.getMessage());
    } catch (BinDrafter.Unavailable e) {
      return PublicResponses.error(409, e.getMessage());
    } catch (RuntimeException e) {
      return PublicResponses.error(502, "The AI provider didn't answer: " + e.getMessage());
    }
  }

  private ObjectNode settings(String id, String token, Bins.Settings s) {
    ObjectNode out = Jsons.object().put("id", id);
    out.set("reply", reply(s.reply()));
    out.put("rulesEnabled", s.rulesEnabled());
    out.set("rules", BinRule.toJson(s.rules()));
    out.set("routing", s.routing().toJson());
    out.put("ai", drafter.available());
    bins.viewKey(id, token).ifPresent(k -> out.put("viewKey", k));
    String name = BinResponder.tunnelName(id);
    out.putObject("tunnel").put("name", name).put("online", tunnelOnline.apply(name).orElse(false));
    out.putObject("limits").put("rules", BinRule.MAX_RULES).put("delayMs", BinRule.MAX_DELAY_MS).put("keepsMb", Bins.SEGMENTS * Bins.SEGMENT_BYTES / (1024 * 1024));
    return out;
  }

  private ObjectNode listing(String id, Bins.Page page) {
    ObjectNode out = Jsons.object().put("id", id).put("total", page.total()).put("oldestKept", page.oldestKept());
    bins.expires(id).ifPresent(e -> out.put("expiresAt", e.toString()));
    out.set("requests", Bins.listing(page.requests()));
    return out;
  }

  private static ObjectNode reply(Bins.Reply r) {
    return Jsons.object().put("status", r.status()).put("contentType", r.contentType()).put("body", r.body()).put("template", r.template());
  }

  private static JsonNode read(String body) {
    try {
      return Jsons.MAPPER.readTree(body == null || body.isBlank() ? "{}" : body);
    } catch (Exception e) {
      throw new IllegalArgumentException("Send JSON");
    }
  }

  private static ResponseDefinition notYours(String id) {
    return PublicResponses.error(404, "No bin " + id + " with that token");
  }

  private static long parseLong(String s) {
    try {
      return s == null ? 0 : Long.parseLong(s);
    } catch (NumberFormatException e) {
      return 0;
    }
  }
}
