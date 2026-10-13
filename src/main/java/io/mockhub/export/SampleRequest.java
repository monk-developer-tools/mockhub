package io.mockhub.export;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import io.mockhub.api.Jsons;
import io.mockhub.project.Project;
import io.mockhub.stub.StubMetadata;
import io.mockhub.stub.StubPaths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record SampleRequest(
    String name,
    String folder,
    String method,
    String path,
    Map<String, String> pathVariables,
    Map<String, String> query,
    Map<String, String> headers,
    String body,
    String bodyType,
    Integer expectedStatus,
    String description) {
  private static final Pattern TEMPLATE_VAR = Pattern.compile("\\{([^}]+)}");

  public static List<SampleRequest> fromStubs(Project project, List<StubMapping> stubs) {
    List<SampleRequest> out = new ArrayList<>();
    for (StubMapping s : stubs) out.add(from(project, s));
    return out;
  }

  public static SampleRequest from(Project project, StubMapping stub) {
    ObjectNode node = (ObjectNode) Jsons.fromWireMock(stub);
    StubPaths.toRelative(node, project.basePath());
    JsonNode req = node.path("request");
    JsonNode mockhubMeta = node.path("metadata").path(StubMetadata.KEY);
    JsonNode override = mockhubMeta.path("sampleRequest");
    boolean soap = "soap".equals(mockhubMeta.path("type").asText());

    String method = req.path("method").asText("GET");
    if (method.equals("ANY")) method = soap ? "POST" : "GET";

    Map<String, String> query = new LinkedHashMap<>();
    Map<String, String> pathVars = new LinkedHashMap<>();
    String path;
    String description = "";
    if (req.hasNonNull("url")) {
      String url = req.get("url").asText();
      int q = url.indexOf('?');
      path = q >= 0 ? url.substring(0, q) : url;
      if (q >= 0) {
        for (String pair : url.substring(q + 1).split("&")) {
          if (pair.isEmpty()) continue;
          int eq = pair.indexOf('=');
          query.put(eq >= 0 ? pair.substring(0, eq) : pair, eq >= 0 ? pair.substring(eq + 1) : "");
        }
      }
    } else if (req.hasNonNull("urlPath")) {
      path = req.get("urlPath").asText();
    } else if (req.hasNonNull("urlPathTemplate")) {
      Matcher m = TEMPLATE_VAR.matcher(req.get("urlPathTemplate").asText());
      StringBuilder sb = new StringBuilder();
      while (m.find()) {
        String var = m.group(1);
        pathVars.put(var, "1");
        m.appendReplacement(sb, ":" + Matcher.quoteReplacement(var));
      }
      m.appendTail(sb);
      path = sb.toString();
    } else if (req.hasNonNull("urlPathPattern") || req.hasNonNull("urlPattern")) {
      String regex = req.hasNonNull("urlPathPattern") ? req.get("urlPathPattern").asText() : req.get("urlPattern").asText();
      path = sampleFromRegex(regex);
      description = "Matches the pattern `" + regex + "`. The path is an example; adjust it as needed.\n\n";
    } else {
      path = "/";
    }

    JsonNode qp = req.path("queryParameters");
    for (Iterator<Map.Entry<String, JsonNode>> it = qp.fields(); it.hasNext(); ) {
      var e = it.next();
      String v = sampleValue(e.getValue());
      if (v != null) query.put(e.getKey(), v);
    }
    Map<String, String> headers = new LinkedHashMap<>();
    for (Iterator<Map.Entry<String, JsonNode>> it = req.path("headers").fields(); it.hasNext(); ) {
      var e = it.next();
      String v = sampleValue(e.getValue());
      if (v != null) headers.put(e.getKey(), v);
    }

    String body = null;
    String bodyType = "none";
    JsonNode patterns = req.path("bodyPatterns");
    if (patterns.isArray() && !patterns.isEmpty()) {
      JsonNode p = patterns.get(0);
      if (p.has("equalToJson")) {
        JsonNode v = p.get("equalToJson");
        body = pretty(v.isTextual() ? v.asText() : Jsons.write(v));
        bodyType = "json";
      } else if (p.has("matchesJsonPath")) {
        body = sampleFromJsonPaths(patterns);
        bodyType = "json";
      } else if (p.has("equalToXml")) {
        body = p.get("equalToXml").asText();
        bodyType = "xml";
      } else if (p.has("matchesXPath")) {
        body = "<!-- Body must match XPath: " + xpathText(p.get("matchesXPath")) + " -->";
        bodyType = "xml";
      } else if (p.has("equalTo") || p.has("contains")) {
        body = p.has("equalTo") ? p.get("equalTo").asText() : p.get("contains").asText();
        bodyType = "text";
      }
    } else if (!method.equals("GET") && !method.equals("DELETE") && !method.equals("HEAD") && !method.equals("OPTIONS")) {
      body = soap ? "" : "{}";
      bodyType = soap ? "xml" : "json";
    }

    if (override.isObject()) {
      if (override.hasNonNull("path")) path = override.get("path").asText();
      if (override.hasNonNull("method")) method = override.get("method").asText();
      if (override.hasNonNull("body")) {
        body = override.get("body").asText();
        String trimmed = body.trim();
        bodyType = trimmed.startsWith("<") ? "xml" : trimmed.startsWith("{") || trimmed.startsWith("[") ? "json" : "text";
      }
      override.path("headers").fields().forEachRemaining(e -> headers.put(e.getKey(), e.getValue().asText()));
      override.path("query").fields().forEachRemaining(e -> query.put(e.getKey(), e.getValue().asText()));
    }
    if (soap && body != null && !body.isEmpty() && !headers.containsKey("Content-Type")) {
      boolean soap12 = "1.2".equals(mockhubMeta.path("soapVersion").asText());
      headers.put("Content-Type", soap12 ? "application/soap+xml; charset=utf-8" : "text/xml; charset=utf-8");
    }
    if (soap && mockhubMeta.hasNonNull("soapAction") && !headers.containsKey("SOAPAction")) {
      headers.put("SOAPAction", "\"" + mockhubMeta.get("soapAction").asText() + "\"");
    }
    if ("json".equals(bodyType) && !headers.containsKey("Content-Type")) {
      headers.put("Content-Type", "application/json");
    }

    JsonNode resp = node.path("response");
    Integer status = resp.has("fault") || resp.has("proxyBaseUrl") ? null : resp.path("status").asInt(200);

    String name = node.hasNonNull("name") ? node.get("name").asText() : method + " " + path;
    String folder = mockhubMeta.hasNonNull("folder") ? mockhubMeta.get("folder").asText() : soap ? "SOAP" : firstSegment(path);
    if (mockhubMeta.hasNonNull("description")) description += mockhubMeta.get("description").asText();

    return new SampleRequest(name, folder, method, path, pathVars, query, headers, body, bodyType, status, description.trim());
  }

  static String sampleValue(JsonNode matcher) {
    if (matcher.has("equalTo")) return matcher.get("equalTo").asText();
    if (matcher.has("contains")) return matcher.get("contains").asText();
    if (matcher.has("matches")) return sampleFromRegex(matcher.get("matches").asText());
    if (matcher.has("equalToJson")) return matcher.get("equalToJson").toString();
    if (matcher.has("absent")) return matcher.get("absent").asBoolean() ? null : "value";
    if (matcher.has("hasExactly") || matcher.has("includes")) {
      JsonNode list = matcher.has("hasExactly") ? matcher.get("hasExactly") : matcher.get("includes");
      if (list.isArray() && !list.isEmpty()) return sampleValue(list.get(0));
    }
    if (matcher.has("doesNotMatch") || matcher.has("doesNotContain")) return "value";
    return null;
  }

  static String sampleFromRegex(String regex) {
    String s = regex;
    s = s.replaceAll("^\\^", "").replaceAll("\\$$", "");
    s = s.replace("(/.*)?", "").replace("([/?].*)?", "");
    s = s.replaceAll("\\\\d\\+|\\[0-9]\\+|\\\\d\\*|\\[0-9]\\*|\\\\d\\{[^}]*}|\\[0-9]\\{[^}]*}", "1");
    s = s.replaceAll("\\[\\^/]\\+|\\[\\^/]\\*|\\.\\+|\\.\\*|\\\\w\\+|\\[a-zA-Z0-9_-]\\+|\\[a-z]\\+", "sample");
    s = s.replaceAll("\\(([^|()]*)\\|[^()]*\\)", "$1");
    s = s.replaceAll("\\\\(.)", "$1");
    s = s.replaceAll("[()?*+^$\\[\\]{}|]", "");
    if (s.isEmpty()) return "/";
    return s;
  }

  private static String sampleFromJsonPaths(JsonNode patterns) {
    ObjectNode root = Jsons.object();
    for (JsonNode p : patterns) {
      if (!p.has("matchesJsonPath")) continue;
      JsonNode jp = p.get("matchesJsonPath");
      String expr = jp.isTextual() ? jp.asText() : jp.path("expression").asText();
      Matcher m = Pattern.compile("^\\$((?:\\.[A-Za-z_][\\w-]*)+)").matcher(expr);
      if (!m.find()) continue;
      String[] parts = m.group(1).substring(1).split("\\.");
      ObjectNode cur = root;
      for (int i = 0; i < parts.length - 1; i++) {
        cur = cur.has(parts[i]) && cur.get(parts[i]).isObject() ? (ObjectNode) cur.get(parts[i]) : cur.putObject(parts[i]);
      }
      String value = jp.isObject() ? sampleValue(jp) : null;
      Matcher eq = Pattern.compile("==\\s*'([^']*)'").matcher(expr);
      if (value == null && eq.find()) value = eq.group(1);
      cur.put(parts[parts.length - 1], value == null ? "value" : value);
    }
    return Jsons.write(root);
  }

  private static String xpathText(JsonNode xp) {
    return xp.isTextual() ? xp.asText() : xp.path("expression").asText();
  }

  private static String pretty(String json) {
    try {
      return Jsons.write(Jsons.MAPPER.readTree(json));
    } catch (Exception e) {
      return json;
    }
  }

  private static String firstSegment(String path) {
    String[] parts = path.split("/");
    for (String p : parts) {
      if (!p.isEmpty() && !p.startsWith(":")) return p;
    }
    return "root";
  }
}
