package io.mockhub.publictools;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.api.Jsons;
import io.mockhub.data.CrudStubs;
import io.mockhub.stub.StubMetadata;
import io.mockhub.stub.StubSwitch;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

final class BinStubs {
  static final String FOLDER = "From webhook bin";
  private static final Set<String> STATE_HELPERS = Set.of("counter", "saveValue", "savedValue", "addToList", "savedList");
  private static final Pattern HELPER = Pattern.compile("\\{\\{\\s*(\\w+)");

  record Plan(ObjectNode defaultResponse, List<ObjectNode> stubs, List<String> notes) {}

  static final class Unconvertible extends RuntimeException {
    Unconvertible(String message) {
      super(message, null, false, false);
    }
  }

  private BinStubs() {}

  static Plan of(Bins.Settings settings) {
    List<String> notes = new ArrayList<>();
    List<ObjectNode> stubs = new ArrayList<>();
    boolean stateHelpers = usesStateHelpers(settings.reply().body(), settings.reply().template());
    for (int i = 0; i < settings.rules().size(); i++) {
      BinRule rule = settings.rules().get(i);
      stateHelpers |= rule.answers().stream().anyMatch(a -> a.template() && usesStateHelpers(a.body(), true));
      try {
        stubs.add(stub(rule, i, notes));
      } catch (Unconvertible e) {
        notes.add(label(rule, i) + ": " + e.getMessage());
      }
    }
    if (settings.rules().isEmpty()) notes.add("The bin had no rules, so the project only has the default answer");
    if (stateHelpers) notes.add("Bin memory helpers ({{counter}}, {{saveValue}}, {{savedValue}}, {{addToList}}, {{savedList}}) only exist in bins;"
        + " stubs using them will error in the project until you edit them");
    return new Plan(defaultResponse(settings.reply()), List.copyOf(stubs), List.copyOf(notes));
  }

  private static ObjectNode defaultResponse(Bins.Reply reply) {
    ObjectNode out = Jsons.object().put("status", reply.status());
    if (!reply.contentType().isEmpty()) out.putObject("headers").put("Content-Type", reply.contentType());
    return out.put("body", reply.body()).put("templated", reply.template());
  }

  private static ObjectNode stub(BinRule rule, int index, List<String> notes) {
    if (rule.crud().isPresent()) return crudStub(rule, index);
    ObjectNode stub = Jsons.object();
    stub.put("name", label(rule, index));
    stub.put("priority", (index + 1) * 10);
    ObjectNode meta = stub.putObject("metadata").putObject(StubMetadata.KEY);
    meta.put("folder", FOLDER);
    if (!rule.description().isEmpty()) meta.put("description", rule.description());
    if (!rule.enabled()) meta.put(StubSwitch.DISABLED, true);
    stub.set("request", request(rule));
    stub.set("response", response(rule, label(rule, index), notes));
    if (!rule.enabled()) StubSwitch.apply(stub);
    return stub;
  }

  private static ObjectNode crudStub(BinRule rule, int index) {
    ObjectNode stub = CrudStubs.stub(rule.crud().get().resource());
    if (!rule.name().isEmpty()) stub.put("name", rule.name());
    stub.put("priority", (index + 1) * 10);
    ObjectNode meta = (ObjectNode) stub.path("metadata").get(StubMetadata.KEY);
    meta.put("folder", FOLDER);
    if (!rule.description().isEmpty()) meta.put("description", rule.description());
    if (!rule.enabled()) {
      meta.put(StubSwitch.DISABLED, true);
      StubSwitch.apply(stub);
    }
    return stub;
  }

  private static ObjectNode request(BinRule rule) {
    ObjectNode request = Jsons.object().put("method", rule.method());
    String path = null;
    Map<String, ObjectNode> headers = new LinkedHashMap<>();
    Map<String, ObjectNode> query = new LinkedHashMap<>();
    List<ObjectNode> bodyPatterns = new ArrayList<>();
    Set<String> repeated = new HashSet<>();
    for (BinRule.Condition c : rule.conditions()) {
      String key = c.target() == BinRule.Target.QUERY || c.target() == BinRule.Target.HEADER ? c.key().trim() : "";
      Map<String, ObjectNode> into = c.target() == BinRule.Target.QUERY ? query : c.target() == BinRule.Target.HEADER ? headers : null;
      if (into != null && !key.isEmpty() && into.containsKey(key) && !repeated.add(c.target() + ":" + key)) {
        throw new Unconvertible("it checks " + c.target().name().toLowerCase() + " " + key + " twice; projects keep one check per name");
      }
      switch (c.target()) {
        case PATH -> {
          if (path != null) throw new Unconvertible("it checks the path twice; projects keep one path check per rule");
          path = pathRegex(c);
        }
        case QUERY -> into.put(key, matcher(c));
        case HEADER -> into.put(key, matcher(c));
        case BODY -> bodyPatterns.add(matcher(c));
        case JSON -> bodyPatterns.add(jsonMatcher(c));
        case XML -> bodyPatterns.add(xmlMatcher(c));
        case FORM -> throw new Unconvertible("form-field checks only exist in bins; check the body text instead");
        case SOAP_ACTION -> headers.put("SOAPAction", matcher(c, "SOAP action"));
        case GRAPHQL -> bodyPatterns.add(graphqlMatcher(c));
      }
    }
    if (path != null) request.put("urlPathPattern", path);
    if (!query.isEmpty()) request.set("queryParameters", object(query));
    if (!headers.isEmpty()) request.set("headers", object(headers));
    if (!bodyPatterns.isEmpty()) {
      ArrayNode list = request.putArray("bodyPatterns");
      bodyPatterns.forEach(list::add);
    }
    return request;
  }

  private static String pathRegex(BinRule.Condition c) {
    return switch (c.op()) {
      case EQUALS -> leading(c.value());
      case MATCHES -> c.value();
      case STARTS_WITH -> leading(c.value()) + ".*";
      case CONTAINS -> ".*" + exact(c.value()) + ".*";
      case TEMPLATE -> templateRegex(c.value());
      default -> throw new Unconvertible("a path can only be checked with equals, starts_with, contains, matches or a pattern");
    };
  }

  private static String leading(String value) {
    return value.startsWith("/") ? "/" + exact(value.substring(1)) : exact(value);
  }

  private static String templateRegex(String template) {
    StringBuilder out = new StringBuilder();
    for (String part : template.split("/", -1)) {
      out.append('/');
      out.append(part.isEmpty() ? "" : part.startsWith(":") && part.length() > 1 ? "[^/]+" : exact(part));
    }
    return out.substring(1);
  }

  private static ObjectNode response(BinRule rule, String label, List<String> notes) {
    if (rule.callout().isPresent() && !rule.callout().get().async()) return syncCallout(rule.callout().get());
    ObjectNode response = plainResponse(rule, label, notes);
    if (rule.callout().isPresent()) attachListener(response, rule.callout().get(), label, notes);
    return response;
  }

  private static ObjectNode plainResponse(BinRule rule, String label, List<String> notes) {
    List<BinRule.Answer> answers = rule.answers();
    if (answers.size() == 1) return answer(answers.get(0));
    ObjectNode response = Jsons.object().put("status", answers.get(0).status());
    response.putArray("transformers").add("mockhub-weighted");
    ArrayNode choices = response.putObject("transformerParameters").putArray("responses");
    for (BinRule.Answer a : answers) {
      ObjectNode choice = Jsons.object().put("weight", Math.max(1, a.weight())).put("status", a.status());
      if (!a.headers().isEmpty()) choice.set("headers", strings(a.headers()));
      String body = body(a);
      if (!body.isEmpty()) choice.put("body", body);
      choices.add(choice);
      if (a.delayMs() > 0) notes.add(label + ": weighted answers can't wait " + a.delayMs() + " ms in a project");
      if (a.template() && !body.isEmpty()) notes.add(label + ": weighted answers serve {{…}} templates as plain text in a project");
    }
    return response;
  }

  private static ObjectNode answer(BinRule.Answer a) {
    ObjectNode response = Jsons.object().put("status", a.status());
    if (!a.headers().isEmpty()) response.set("headers", strings(a.headers()));
    String body = body(a);
    if (!body.isEmpty()) response.put("body", body);
    if (a.template()) response.putArray("transformers").add("response-template");
    if (a.delayMs() > 0) response.put("fixedDelayMilliseconds", a.delayMs());
    return response;
  }

  private static String body(BinRule.Answer a) {
    if (!a.file()) return a.body();
    try {
      return StandardCharsets.UTF_8.newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(java.nio.ByteBuffer.wrap(a.bytes()))
          .toString();
    } catch (CharacterCodingException e) {
      throw new Unconvertible("file answers with binary content don't convert to a project stub");
    }
  }

  private static ObjectNode syncCallout(BinCallout callout) {
    ObjectNode response = Jsons.object().put("status", 200);
    response.putArray("transformers").add("mockhub-sync-callout");
    response.putObject("transformerParameters").set("callout", calloutParameters(callout, false));
    return response;
  }

  private static void attachListener(ObjectNode response, BinCallout callout, String label, List<String> notes) {
    ObjectNode listener = Jsons.object().put("name", "mockhub-callout");
    listener.set("parameters", calloutParameters(callout, true));
    if (callout.maxDelayMs() > callout.minDelayMs()) {
      notes.add(label + ": the background call waits a fixed " + callout.minDelayMs() + " ms instead of "
          + callout.minDelayMs() + "–" + callout.maxDelayMs() + " ms");
    }
    response.putArray("serveEventListeners").add(listener);
  }

  private static ObjectNode calloutParameters(BinCallout callout, boolean withDelay) {
    ObjectNode out = Jsons.object();
    out.put("method", BinCallout.SAME.equals(callout.method()) ? "{{request.method}}" : callout.method());
    out.put("url", callout.url());
    ObjectNode headers = Jsons.object();
    callout.headers().forEach(headers::put);
    BinCaller.authHeader(callout.auth()).ifPresent(h -> headers.put(h.name(), h.value()));
    if (!headers.isEmpty()) out.set("headers", headers);
    if (callout.original()) {
      out.put("forward", true);
    } else if (!callout.body().isEmpty()) {
      out.put("body", callout.body());
    }
    if (withDelay && callout.minDelayMs() > 0) {
      out.putObject("delay").put("type", "fixed").put("milliseconds", callout.minDelayMs());
    }
    return out;
  }

  private static ObjectNode matcher(BinRule.Condition c) {
    return matcher(c, c.target().name().toLowerCase());
  }

  private static ObjectNode matcher(BinRule.Condition c, String what) {
    ObjectNode out = Jsons.object();
    switch (c.op()) {
      case EQUALS -> out.put("equalTo", c.value());
      case NOT_EQUALS -> out.put("doesNotMatch", exact(c.value()));
      case STARTS_WITH -> out.put("matches", exact(c.value()) + ".*");
      case CONTAINS -> out.put("contains", c.value());
      case NOT_CONTAINS -> out.put("doesNotContain", c.value());
      case MATCHES -> out.put("matches", c.value());
      case EXISTS -> out.put("matches", ".*");
      case ABSENT -> out.put("absent", true);
      case TEMPLATE -> throw new Unconvertible(what + " can't be checked with a pattern");
    }
    return out;
  }

  private static ObjectNode jsonMatcher(BinRule.Condition c) {
    if (c.op() == BinRule.Op.EXISTS) return Jsons.object().put("matchesJsonPath", c.key());
    return inner(c, "matchesJsonPath", "JSON field");
  }

  private static ObjectNode xmlMatcher(BinRule.Condition c) {
    if (c.op() == BinRule.Op.EXISTS) return Jsons.object().put("matchesXPath", c.key());
    return inner(c, "matchesXPath", "XML field");
  }

  private static ObjectNode inner(BinRule.Condition c, String container, String what) {
    ObjectNode value = Jsons.object().put("expression", c.key());
    switch (c.op()) {
      case EQUALS -> value.put("equalTo", c.value());
      case CONTAINS -> value.put("contains", c.value());
      case MATCHES -> value.put("matches", c.value());
      case STARTS_WITH -> value.put("matches", exact(c.value()) + ".*");
      case NOT_EQUALS -> value.put("doesNotMatch", exact(c.value()));
      case NOT_CONTAINS -> value.put("doesNotMatch", ".*" + exact(c.value()) + ".*");
      default -> throw new Unconvertible(what + " " + c.key() + " can only be checked with equals, contains, matches, starts_with,"
          + " not_equals or not_contains in a project");
    }
    return Jsons.object().set(container, value);
  }

  private static ObjectNode graphqlMatcher(BinRule.Condition c) {
    if (c.op() != BinRule.Op.EQUALS) throw new Unconvertible("a GraphQL operation can only be checked with equals");
    return Jsons.object().put("matchesJsonPath", "$[?(@.operationName == '" + c.value().replace("'", "\\'") + "')]");
  }

  private static boolean usesStateHelpers(String text, boolean template) {
    if (!template || text == null || text.isEmpty()) return false;
    java.util.regex.Matcher m = HELPER.matcher(text);
    while (m.find()) {
      if (STATE_HELPERS.contains(m.group(1))) return true;
    }
    return false;
  }

  private static String label(BinRule rule, int index) {
    return rule.name().isEmpty() ? "Rule " + (index + 1) : rule.name();
  }

  private static ObjectNode strings(Map<String, String> values) {
    ObjectNode out = Jsons.object();
    values.forEach(out::put);
    return out;
  }

  private static ObjectNode object(Map<String, ObjectNode> values) {
    ObjectNode out = Jsons.object();
    values.forEach(out::set);
    return out;
  }

  private static String exact(String value) {
    return Pattern.quote(value);
  }
}
