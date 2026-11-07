package io.mockhub.publictools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import io.mockhub.api.ApiException;
import io.mockhub.api.Jsons;
import io.mockhub.auth.Principal;
import io.mockhub.playground.JdkHttpSender;
import io.mockhub.playground.TargetGuard;
import io.mockhub.tunnel.TunnelGateway;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BinTool implements PublicTool {
  public static final int MAX_REPLY_BODY = 4 * 1024;
  private static final Pattern CAPTURE = Pattern.compile("/bins/([a-z0-9]{12})(/.*)?");
  private static final Pattern INSPECT = Pattern.compile("/inspect/([a-z0-9]{12})(/requests(?:/\\d{1,12}(?:/share|/parts(?:/\\d{1,3})?)?)?|/response|/settings|/rules(?:/[a-z0-9-]{1,40})?|/state/[a-z]{1,10}/[^/]{1,200}|/routing|/draft(?:/fix)?|/project|/state|/export|/options|/data)?");
  private static final Pattern ONE = Pattern.compile("/requests/(\\d{1,12})(/share)?");
  private static final Pattern PARTS = Pattern.compile("/requests/(\\d{1,12})/parts(?:/(\\d{1,3}))?");
  private static final Pattern RULE = Pattern.compile("/rules/([a-z0-9-]{1,40})");
  private static final Pattern STATE_ONE = Pattern.compile("/state/([a-z]{1,10})/([^/]{1,200})");

  private final Bins bins;
  private final PublicLimits limits;
  private final BinResponder responder;
  private final BinDrafter drafter;
  private final Function<String, Optional<Boolean>> tunnelOnline;
  private final Function<String, Optional<Principal>> signedIn;
  private final LongSupplier millis;
  private final BinGate gate;
  private final BinScheduler scheduler;
  private final BinCrud records;
  private final BinProject saves;

  BinTool(Bins bins, PublicLimits limits, BinResponder responder, BinDrafter drafter, Function<String, Optional<Boolean>> tunnelOnline,
      Function<String, Optional<Principal>> signedIn, LongSupplier millis, BinProject saves) {
    this(bins, limits, responder, drafter, tunnelOnline, signedIn, millis, BinScheduler.pooled(4), saves);
  }

  BinTool(Bins bins, PublicLimits limits, BinResponder responder, BinDrafter drafter, Function<String, Optional<Boolean>> tunnelOnline,
      Function<String, Optional<Principal>> signedIn, LongSupplier millis, BinScheduler scheduler, BinProject saves) {
    this.scheduler = scheduler;
    this.saves = saves;
    this.records = new BinCrud(bins.records());
    this.bins = bins;
    this.limits = limits;
    this.responder = responder;
    this.drafter = drafter;
    this.tunnelOnline = tunnelOnline;
    this.signedIn = signedIn;
    this.millis = millis;
    this.gate = new BinGate(millis);
  }

  public static BinTool create(Bins bins, PublicLimits limits, TemplatePlayground templates, TargetGuard guard, TunnelGateway tunnels,
      Function<String, Optional<Boolean>> tunnelOnline, Function<String, Optional<Principal>> signedIn, BooleanSupplier aiOn, BinDrafter.Llm llm,
      BinProject saves) {
    BinResponder responder = new BinResponder(templates, new JdkHttpSender(), new JdkHttpSender(true), guard, tunnels,
        bound -> ThreadLocalRandom.current().nextInt(bound), System::currentTimeMillis, bins.records());
    return new BinTool(bins, limits, responder, new BinDrafter(aiOn, llm), tunnelOnline, signedIn, System::currentTimeMillis, saves);
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

  static boolean capturesAt(String path) {
    return CAPTURE.matcher(path).matches();
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
    BinOptions options = settings.get().options();
    if (options.cors() && request.method().equals("OPTIONS") && request.header("Access-Control-Request-Method") != null) {
      bins.touch(id);
      return PublicResponses.binPreflight(request.header("Access-Control-Request-Method"), request.header("Access-Control-Request-Headers"));
    }
    String rest = request.path().substring(("/bins/" + id).length());
    BinMatcher.Call call = new BinMatcher.Call(request.method(), rest.isEmpty() ? "/" : rest, request);
    long started = millis.getAsLong();
    BinResponder.Answer answer = gated(id, options, request).orElseGet(() -> {
      BinResponder.Answer a = responder.answer(id, settings.get(), bins.stateFor(id).orElseGet(BinState::new), call);
      bins.keepState(id);
      return a;
    });
    int delay = answer.delayMs() > 0 || answer.by().equals("auth") || answer.by().equals("limit") ? answer.delayMs() : options.latencyMs();
    long took = millis.getAsLong() - started + delay;
    String text = Bins.readable(answer.body());
    boolean cut = answer.body().length > Bins.MAX_ANSWER_BODY;
    Map<String, String> shown = new LinkedHashMap<>(answer.headers());
    long seq = bins.record(id, request, call.path(), BinMatcher.eventOf(request, call.json()),
        new Bins.Outcome(answer.by(), answer.rule(), answer.status(), took, shown, cut ? Bins.cut(text, Bins.MAX_ANSWER_BODY) : text, cut),
        answer.callout().map(BinResponder.Followup::shown).orElse(null));
    answer.callout().ifPresent(f -> schedule(id, seq, f));
    ResponseDefinitionBuilder out = PublicResponses.forBin(answer.status(), options.cors()).withHeader("X-MockHub-Bin", id);
    answer.headers().forEach((name, value) -> {
      if (!setByBin(name, options.cors())) out.withHeader(name, value);
    });
    if (answer.headers().keySet().stream().noneMatch(k -> k.equalsIgnoreCase("Cache-Control"))) out.withHeader("Cache-Control", "no-store");
    if (delay > 0) out.withFixedDelay(delay);
    return out.withBody(answer.body()).build();
  }

  private static boolean setByBin(String name, boolean cors) {
    String lower = name.toLowerCase(Locale.ROOT);
    return lower.equals("x-mockhub-bin") || lower.equals("x-robots-tag")
        || cors && (lower.equals("access-control-allow-origin") || lower.equals("access-control-expose-headers"));
  }

  private void schedule(String id, long seq, BinResponder.Followup followup) {
    if (followup.pending().isEmpty() || seq == 0) return;
    BinCaller.Prepared prepared = followup.pending().get();
    boolean queued = scheduler.later(id, followup.waitMs(), () -> bins.calloutDone(id, seq, responder.callLater(prepared, followup.waitMs())));
    if (!queued) {
      bins.calloutDone(id, seq, followup.shown().failed("Too many callouts are waiting; this bin can have " + BinScheduler.MAX_PER_BIN + " at a time."));
    }
  }

  private Optional<BinResponder.Answer> gated(String id, BinOptions options, PublicRequest request) {
    Optional<BinOptions.RequiredHeader> required = options.requiredHeader();
    if (required.isPresent() && !required.get().accepts(request.header(required.get().name()))) {
      return Optional.of(refusal(401, Map.of(), "auth", "Send the " + required.get().name() + " header with the bin's value."));
    }
    Optional<BinOptions.RateLimit> rate = options.rateLimit();
    if (rate.isPresent()) {
      Optional<Long> wait = gate.take(id, rate.get());
      if (wait.isPresent()) {
        BinOptions.RateLimit r = rate.get();
        return Optional.of(refusal(429, Map.of("Retry-After", String.valueOf(wait.get())), "limit",
            "This bin allows " + r.requests() + " requests a " + r.per().name().toLowerCase(Locale.ROOT) + ". Try again in " + wait.get() + " s."));
      }
    }
    return Optional.empty();
  }

  private static BinResponder.Answer refusal(int status, Map<String, String> extra, String by, String message) {
    Map<String, String> headers = new LinkedHashMap<>(extra);
    headers.put("Content-Type", "application/json");
    byte[] body = Jsons.write(Jsons.object().put("error", message).put("status", status)).getBytes(StandardCharsets.UTF_8);
    return new BinResponder.Answer(status, headers, body, 0, by, "");
  }

  private ResponseDefinition inspect(String id, String part, PublicRequest request) {
    String token = Optional.ofNullable(request.header("X-Bin-Token")).orElse(request.firstQuery("token"));
    String method = request.method();
    String what = part == null ? "/requests" : part;
    Matcher one = ONE.matcher(what);
    boolean single = one.matches();
    boolean sharing = single && one.group(2) != null;
    Matcher parts = PARTS.matcher(what);
    boolean partsAsked = parts.matches();
    boolean reading = method.equals("GET") && (what.equals("/requests") || what.equals("/export") || what.equals("/data") || partsAsked || (single && !sharing));
    if (!reading && bins.viewOnly(id, token)) {
      return PublicResponses.error(403, "This is a view-only link. Only the bin's owner can change it.");
    }
    if (single) return single(id, token, Long.parseLong(one.group(1)), sharing, method);
    if (partsAsked) return parts(id, token, Long.parseLong(parts.group(1)), parts.group(2), method);
    Matcher rule = RULE.matcher(what);
    if (rule.matches()) return rule(id, token, rule.group(1), method, request);
    if (what.equals("/rules") && method.equals("GET")) return rules(id, token);
    if (what.equals("/rules") && method.equals("POST")) return editRules(id, token, list -> BinRuleEdits.create(list, read(request.body()), -1), 201, BinTool::added);
    Matcher stateOne = STATE_ONE.matcher(what);
    if (stateOne.matches()) return stateOne(id, token, stateOne.group(1), decode(stateOne.group(2)), method, request);
    if (what.equals("/export") && method.equals("GET")) return export(id, token, request);
    if (what.equals("/data")) return data(id, token, method, request);
    if (part == null && method.equals("DELETE")) {
      return bins.clear(id, token) ? PublicResponses.json(200, Jsons.object().put("cleared", true)) : notYours(id);
    }
    if (reading) {
      long after = parseLong(request.firstQuery("after"));
      long before = parseLong(request.firstQuery("before"));
      int limit = (int) parseLong(request.firstQuery("limit"));
      Predicate<Bins.Captured> filter;
      try {
        filter = BinLogFilter.of(request);
      } catch (IllegalArgumentException e) {
        return PublicResponses.error(400, e.getMessage());
      }
      return bins.requests(id, token, after, before, limit, filter).map(page -> PublicResponses.json(200, listing(id, page))).orElseGet(() -> notYours(id));
    }
    if (what.equals("/response") && method.equals("GET")) {
      return bins.settings(id, token).map(s -> PublicResponses.json(200, reply(s.reply()))).orElseGet(() -> notYours(id));
    }
    if (what.equals("/response") && method.equals("PUT")) return setReply(id, token, request.body());
    if (what.equals("/settings") && method.equals("GET")) {
      return bins.settings(id, token).map(s -> PublicResponses.json(200, settings(id, token, s))).orElseGet(() -> notYours(id));
    }
    if (what.equals("/state") && method.equals("GET")) {
      return bins.state(id, token).map(st -> PublicResponses.json(200, st.toJson())).orElseGet(() -> notYours(id));
    }
    if (what.equals("/state") && method.equals("DELETE")) {
      return bins.clearState(id, token) ? PublicResponses.json(200, Jsons.object().put("cleared", true)) : notYours(id);
    }
    if (what.equals("/rules") && method.equals("PUT")) return setRules(id, token, request.body());
    if (what.equals("/routing") && method.equals("PUT")) return setRouting(id, token, request.body());
    if (what.equals("/options") && method.equals("PUT")) return setOptions(id, token, request.body());
    if (what.equals("/draft") && method.equals("POST")) return withAi(id, token, request, this::draft);
    if (what.equals("/draft/fix") && method.equals("POST")) return withAi(id, token, request, this::fix);
    if (what.equals("/project") && method.equals("POST")) return saveProject(id, token, request);
    return PublicResponses.error(405, "Read a bin with GET, change it with PUT /response, /rules or /routing, clear it with DELETE, read and clear its saved values with GET and DELETE /state, export it with GET /export, change its settings with PUT /options, read and change stored records with /data, edit one rule at /rules/{ruleId} and one saved entry at /state/{kind}/{name}, or save the whole bin as a MockHub project with POST /project");
  }

  private ResponseDefinition saveProject(String id, String token, PublicRequest request) {
    if (!bins.verify(id, token)) return notYours(id);
    Optional<Principal> who = signedIn.apply(request.header("Authorization"));
    if (who.isEmpty()) return PublicResponses.error(401, "Sign in to MockHub to save this bin as a project.");
    if (saves == null) return PublicResponses.error(503, "Saving projects isn't set up on this server.");
    Optional<Long> wait = limits.take("user:" + who.get().id(), PublicLimits.Kind.CREATE);
    if (wait.isPresent()) return PublicResponses.tooMany(wait.get(), "saves");
    Optional<Bins.Settings> settings = bins.settings(id, token);
    if (settings.isEmpty()) return notYours(id);
    try {
      BinProject.Saved saved = saves.save(id, settings.get(), who.get(), read(request.body()).path("name").asText(""));
      ObjectNode out = Jsons.object()
          .put("projectId", saved.projectId())
          .put("name", saved.name())
          .put("slug", saved.slug())
          .put("url", "/" + saved.slug())
          .put("ui", saved.ui());
      saved.notes().forEach(out.putArray("notes")::add);
      return PublicResponses.json(201, out);
    } catch (IllegalArgumentException e) {
      return PublicResponses.error(400, e.getMessage());
    } catch (ApiException e) {
      return PublicResponses.error(e.status, e.getMessage());
    } catch (RuntimeException e) {
      return PublicResponses.error(502, "The project couldn't be created. Try again.");
    }
  }

  private ResponseDefinition rules(String id, String token) {
    return bins.settings(id, token)
        .map(s -> PublicResponses.json(200, Jsons.object().put("rulesEnabled", s.rulesEnabled()).set("rules", BinRule.toJson(s.rules()))))
        .orElseGet(() -> notYours(id));
  }

  private ResponseDefinition rule(String id, String token, String ruleId, String method, PublicRequest request) {
    if (ruleId.equals(BinRule.RESERVED_ID)) {
      if (!method.equals("PUT")) return PublicResponses.error(405, "Reorder rules with PUT /rules/order and {\"ids\": [...]}");
      return editRules(id, token, list -> BinRuleEdits.reorder(list, read(request.body()).path("ids")), 200, BinTool::allRules);
    }
    return switch (method) {
      case "GET" -> bins.settings(id, token)
          .map(s -> BinRuleEdits.find(s.rules(), ruleId).map(r -> PublicResponses.json(200, r.toJson())).orElseGet(() -> PublicResponses.error(404, new BinRuleEdits.Missing(ruleId).getMessage())))
          .orElseGet(() -> notYours(id));
      case "PUT" -> editRules(id, token, list -> BinRuleEdits.replace(list, ruleId, read(request.body())), 200, one(ruleId));
      case "PATCH" -> editRules(id, token, list -> BinRuleEdits.patch(list, ruleId, read(request.body())), 200, one(ruleId));
      case "DELETE" -> editRules(id, token, list -> BinRuleEdits.delete(list, ruleId), 200, BinTool::allRules);
      default -> PublicResponses.error(405, "Read a rule with GET, replace it with PUT, change fields with PATCH or delete it with DELETE");
    };
  }

  private ResponseDefinition editRules(String id, String token, UnaryOperator<List<BinRule>> edit, int status, BiFunction<List<BinRule>, List<BinRule>, JsonNode> view) {
    AtomicReference<List<BinRule>> before = new AtomicReference<>();
    try {
      boolean owned = bins.update(id, token, s -> {
        before.set(s.rules());
        return s.withRules(s.rulesEnabled(), edit.apply(s.rules()));
      });
      if (!owned) return notYours(id);
    } catch (BinRuleEdits.Missing e) {
      return PublicResponses.error(404, e.getMessage());
    } catch (BinRuleEdits.Taken e) {
      return PublicResponses.error(409, e.getMessage());
    } catch (IllegalArgumentException e) {
      return PublicResponses.error(400, e.getMessage());
    }
    return bins.settings(id, token).map(now -> PublicResponses.json(status, view.apply(before.get(), now.rules()))).orElseGet(() -> notYours(id));
  }

  private static JsonNode allRules(List<BinRule> before, List<BinRule> after) {
    return Jsons.object().set("rules", BinRule.toJson(after));
  }

  private static JsonNode added(List<BinRule> before, List<BinRule> after) {
    return after.stream().filter(r -> before.stream().noneMatch(b -> b.id().equals(r.id()))).findFirst().map(BinRule::toJson).orElseGet(Jsons::object);
  }

  private static BiFunction<List<BinRule>, List<BinRule>, JsonNode> one(String ruleId) {
    return (before, after) -> BinRuleEdits.find(after, ruleId).map(r -> (JsonNode) r.toJson()).orElseGet(Jsons::object);
  }

  private ResponseDefinition stateOne(String id, String token, String kindName, String name, String method, PublicRequest request) {
    Optional<BinState> state = bins.state(id, token);
    if (state.isEmpty()) return notYours(id);
    try {
      BinState.Kind kind = BinState.Kind.of(kindName);
      if (method.equals("PUT")) {
        JsonNode node = read(request.body());
        if (!node.has("value")) return PublicResponses.error(400, "Send {\"value\": ...}: a number for a counter, text for a value, a list of text for a list");
        state.get().put(kind, name, node.get("value"));
      } else if (method.equals("DELETE")) {
        if (!state.get().remove(kind, name)) return PublicResponses.error(404, "No " + kindName + " entry named " + name);
      } else {
        return PublicResponses.error(405, "Set one entry with PUT or remove it with DELETE");
      }
    } catch (IllegalArgumentException | BinState.Full e) {
      return PublicResponses.error(400, e.getMessage());
    }
    bins.keepState(id);
    return PublicResponses.json(200, state.get().toJson());
  }

  private ResponseDefinition parts(String id, String key, long seq, String index, String method) {
    if (!method.equals("GET")) return PublicResponses.error(405, "Read a request's parts with GET");
    Optional<Bins.Captured> found = bins.one(id, key, seq);
    if (found.isEmpty()) return missing(id, seq);
    Bins.Captured c = found.get();
    byte[] body = c.raw().isEmpty() ? c.body().getBytes(StandardCharsets.UTF_8) : Base64.getDecoder().decode(c.raw());
    String type = c.headers().entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase("Content-Type")).map(Map.Entry::getValue).findFirst().orElse("");
    List<BinMultipart.Part> list = BinMultipart.parts(body, type);
    if (index == null) {
      ObjectNode out = Jsons.object().put("seq", seq).put("truncated", c.truncated());
      var items = out.putArray("parts");
      for (int i = 0; i < list.size(); i++) {
        BinMultipart.Part p = list.get(i);
        items.addObject().put("index", i).put("name", p.name()).put("filename", p.filename()).put("type", p.type()).put("size", p.bytes().length);
      }
      return PublicResponses.json(200, out);
    }
    int i = Integer.parseInt(index);
    if (i >= list.size()) return PublicResponses.error(404, "Request " + seq + " has " + list.size() + " multipart parts");
    BinMultipart.Part p = list.get(i);
    String file = (p.filename().isEmpty() ? (p.name().isEmpty() ? "part-" + i : p.name()) : p.filename()).replaceAll("[^A-Za-z0-9._-]", "_");
    return PublicResponses.base(200)
        .withHeader("Content-Type", p.type().isEmpty() ? "application/octet-stream" : p.type())
        .withHeader("Content-Disposition", "attachment; filename=\"" + file + "\"")
        .withHeader("X-Content-Type-Options", "nosniff")
        .withHeader("Content-Security-Policy", "sandbox")
        .withBody(p.bytes())
        .build();
  }

  private static String decode(String segment) {
    try {
      return URLDecoder.decode(segment.replace("+", "%2B"), StandardCharsets.UTF_8);
    } catch (IllegalArgumentException e) {
      return segment;
    }
  }

  private ResponseDefinition data(String id, String token, String method, PublicRequest request) {
    if (method.equals("GET")) return bins.reads(id, token) ? PublicResponses.json(200, collections(id)) : notYours(id);
    if (!bins.verify(id, token)) return notYours(id);
    BinRecords store = bins.records();
    if (method.equals("DELETE")) {
      String name = request.firstQuery("collection");
      if (name == null || name.isBlank()) store.clearProject(id);
      else store.clear(id, name.replaceAll("^/+", ""));
      return PublicResponses.json(200, collections(id));
    }
    if (method.equals("PUT")) {
      try {
        JsonNode node = read(request.body());
        BinCrudRoute route = BinCrudRoute.from(node.path("route"));
        Map<String, Map<String, ObjectNode>> before = store.snapshot(id);
        store.clear(id, route.resource().name());
        try {
          int seeded = records.seed(id, route, node.path("records"));
          return PublicResponses.json(200, collections(id).put("seeded", seeded));
        } catch (IllegalArgumentException e) {
          store.restore(id, before);
          throw e;
        }
      } catch (IllegalArgumentException e) {
        return PublicResponses.error(400, e.getMessage());
      }
    }
    return PublicResponses.error(405, "Read stored records with GET, replace a collection with PUT or clear them with DELETE");
  }

  private ObjectNode collections(String id) {
    ObjectNode out = Jsons.object().put("id", id);
    out.putObject("limits").put("records", BinRecords.MAX_RECORDS).put("recordBytes", BinRecords.MAX_RECORD_BYTES).put("collections", BinRecords.MAX_RESOURCES);
    var list = out.putArray("collections");
    bins.records().snapshot(id).forEach((name, recs) -> {
      ObjectNode c = list.addObject().put("name", name).put("path", "/" + name).put("count", recs.size());
      var items = c.putArray("records");
      recs.values().forEach(items::add);
    });
    return out;
  }

  private ResponseDefinition single(String id, String token, long seq, boolean sharing, String method) {
    if (sharing && method.equals("GET")) {
      return bins.shareKey(id, token, seq).map(key -> PublicResponses.json(200, Jsons.object().put("key", key))).orElseGet(() -> missing(id, seq));
    }
    if (sharing) return PublicResponses.error(405, "Get a request's share key with GET");
    if (method.equals("GET")) {
      return bins.one(id, token, seq).map(c -> {
        ObjectNode out = c.toJson().put("bin", id);
        bins.expires(id).ifPresent(e -> out.put("expiresAt", e.toString()));
        return PublicResponses.json(200, out);
      }).orElseGet(() -> missing(id, seq));
    }
    if (method.equals("DELETE")) {
      return bins.delete(id, token, seq) ? PublicResponses.json(200, Jsons.object().put("deleted", seq)) : missing(id, seq);
    }
    return PublicResponses.error(405, "Read one request with GET or delete it with DELETE");
  }

  private ResponseDefinition export(String id, String token, PublicRequest request) {
    Predicate<Bins.Captured> filter;
    try {
      filter = BinLogFilter.of(request);
    } catch (IllegalArgumentException e) {
      return PublicResponses.error(400, e.getMessage());
    }
    boolean har = !"json".equals(request.firstQuery("format"));
    return bins.export(id, token, filter, Bins.MAX_EXPORT).map(list -> PublicResponses.json(200, har
        ? BinHar.of(binUrl(request, id), list)
        : Jsons.object().put("id", id).put("exported", list.size()).set("requests", Bins.listing(list)))).orElseGet(() -> notYours(id));
  }

  private static String binUrl(PublicRequest request, String id) {
    String host = Optional.ofNullable(request.header("Host")).orElse("localhost");
    String proto = Optional.ofNullable(request.header("X-Forwarded-Proto")).orElse("http");
    return proto + "://" + host + PublicRequest.PREFIX + "/bins/" + id;
  }

  private static ResponseDefinition missing(String id, long seq) {
    return PublicResponses.error(404, "No request " + seq + " in bin " + id + " for that key. It may have been deleted, or the bin expired.");
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

  private ResponseDefinition setOptions(String id, String token, String body) {
    try {
      BinOptions options = BinOptions.from(read(body));
      if (!bins.update(id, token, s -> s.withOptions(options))) return notYours(id);
      return bins.settings(id, token).map(s -> PublicResponses.json(200, settings(id, token, s))).orElseGet(() -> notYours(id));
    } catch (IllegalArgumentException e) {
      return PublicResponses.error(400, e.getMessage());
    }
  }

  private ResponseDefinition withAi(String id, String token, PublicRequest request, Function<JsonNode, ObjectNode> work) {
    if (!bins.verify(id, token)) return notYours(id);
    if (!drafter.available()) return PublicResponses.error(409, "This server has no AI provider set up.");
    Optional<Principal> who = signedIn.apply(request.header("Authorization"));
    if (who.isEmpty()) return PublicResponses.error(401, "Sign in to MockHub to use AI in a bin.");
    Optional<Long> wait = limits.take("user:" + who.get().id(), PublicLimits.Kind.AI);
    if (wait.isPresent()) return PublicResponses.tooMany(wait.get(), "AI requests");
    try {
      return PublicResponses.json(200, work.apply(read(request.body())));
    } catch (IllegalArgumentException e) {
      return PublicResponses.error(400, e.getMessage());
    } catch (BinDrafter.Unavailable e) {
      return PublicResponses.error(409, e.getMessage());
    } catch (RuntimeException e) {
      return PublicResponses.error(502, "The AI provider didn't answer: " + e.getMessage());
    }
  }

  private ObjectNode draft(JsonNode body) {
    BinDrafter.Drafted drafted = drafter.draft(body.path("description").asText(""));
    ObjectNode out = Jsons.object();
    out.set("rule", drafted.rules().get(0).toJson());
    out.set("rules", BinRule.toJson(drafted.rules()));
    drafted.skipped().forEach(out.putArray("skipped")::add);
    return out;
  }

  private ObjectNode fix(JsonNode body) {
    return Jsons.object().put("template", drafter.fix(body.path("template").asText(""), body.path("error").asText(""), body.path("request").asText("")));
  }

  private ObjectNode settings(String id, String token, Bins.Settings s) {
    ObjectNode out = Jsons.object().put("id", id);
    out.set("reply", reply(s.reply()));
    out.put("rulesEnabled", s.rulesEnabled());
    out.set("rules", BinRule.toJson(s.rules()));
    out.set("routing", s.routing().toJson());
    out.set("options", s.options().toJson());
    out.put("ai", drafter.available());
    bins.viewKey(id, token).ifPresent(k -> out.put("viewKey", k));
    String name = BinResponder.tunnelName(id);
    out.putObject("tunnel").put("name", name).put("online", tunnelOnline.apply(name).orElse(false));
    out.putObject("limits").put("rules", BinRule.MAX_RULES).put("delayMs", BinRule.MAX_DELAY_MS).put("calloutWaitMs", BinCallout.MAX_TRIGGER_MS).put("keepsMb", Bins.SEGMENTS * Bins.SEGMENT_BYTES / (1024 * 1024));
    return out;
  }

  private ObjectNode listing(String id, Bins.Page page) {
    ObjectNode out = Jsons.object().put("id", id).put("total", page.total()).put("oldestKept", page.oldestKept()).put("deleted", page.deleted());
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
