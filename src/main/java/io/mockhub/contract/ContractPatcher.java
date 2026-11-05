package io.mockhub.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.mockhub.ai.AiException;
import io.mockhub.ai.AiSettings;
import io.mockhub.ai.AiSettingsStore;
import io.mockhub.ai.ChatCompletions;
import io.mockhub.api.ApiException;
import io.mockhub.api.Jsons;
import io.mockhub.history.HistoryEntry;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ContractPatcher {
  static final int MAX_FINDINGS = 30;
  private static final int MAX_EXAMPLES = 5;
  private static final int MAX_EXAMPLE_BODY = 1500;
  private static final int MAX_EXCERPT = 40_000;
  private static final Pattern FENCE = Pattern.compile("```[a-zA-Z0-9+-]*\\s*\\n?(.*?)```", Pattern.DOTALL);
  private static final Pattern REF = Pattern.compile("\"\\$ref\"\\s*:\\s*\"#/(?:components/schemas|definitions)/([^\"]+)\"");

  public record Suggestion(String summary, JsonNode operations, JsonNode spec) {}

  private final AiSettingsStore settings;
  private final ChatCompletions chat;
  private final BiFunction<String, String, Optional<HistoryEntry>> examples;

  public ContractPatcher(AiSettingsStore settings, ChatCompletions chat, BiFunction<String, String, Optional<HistoryEntry>> examples) {
    this.settings = settings;
    this.chat = chat;
    this.examples = examples;
  }

  public Suggestion suggest(String projectId, String basePath, Contract contract, List<DriftState.Finding> findings) {
    AiSettings s = settings.get();
    if (!s.enabled()) throw new ApiException(409, "The AI assistant is turned off. An admin can turn it on under AI assistant.");
    if (findings.isEmpty()) throw ApiException.badRequest("Choose at least one finding to fix");
    ArrayNode messages = Jsons.MAPPER.createArrayNode();
    messages.addObject().put("role", "system").put("content", systemPrompt());
    messages.addObject().put("role", "user").put("content", userPrompt(projectId, basePath, contract.spec(), findings));
    String text = clean(chat.complete(s, messages, null).message().path("content").asText(""));
    JsonNode answer;
    try {
      answer = Jsons.MAPPER.readTree(text);
    } catch (Exception e) {
      throw new AiException("The AI provider didn't return a JSON patch. Try again.");
    }
    JsonNode operations = answer.isArray() ? answer : answer.path("patch");
    if (!operations.isArray() || operations.isEmpty()) throw new AiException("The AI provider didn't suggest any change. Try again with fewer findings.");
    JsonNode patched;
    try {
      patched = JsonPatch.apply(contract.spec(), operations);
      ContractDocument.check(patched);
      ApiContract.of(patched);
    } catch (JsonPatch.PatchException | ApiException e) {
      throw new AiException("The AI suggested a patch that doesn't fit the contract (" + e.getMessage() + "). Try again.");
    }
    String summary = answer.isObject() ? answer.path("summary").asText("") : "";
    return new Suggestion(summary, operations, patched);
  }

  static String clean(String content) {
    String text = content.strip();
    Matcher fence = FENCE.matcher(text);
    if (fence.find()) text = fence.group(1).strip();
    return text;
  }

  private static String systemPrompt() {
    return """
        You maintain OpenAPI contracts. You get findings where recorded API traffic disagrees with the contract, \
        with the parts of the contract they touch. Reply with one JSON object and nothing else: \
        {"summary": "one sentence on what the patch documents", "patch": [ ...RFC 6902 operations... ]}. \
        Use only "add", "replace" and "remove". Paths are JSON Pointers into the contract: escape "/" as "~1" and "~" as "~0", \
        so the GET operation of /orders/{id} is /paths/~1orders~1{id}/get. Make the smallest change that documents what \
        the traffic shows: add the missing path, method, parameter, header, status, content type or property, or relax a \
        type that is wrong. Prefer $ref to an existing schema when one fits. Never remove operations, and never invent \
        behaviour the traffic doesn't show. If a finding looks like a client bug rather than a contract gap, leave it out.""";
  }

  private String userPrompt(String projectId, String basePath, JsonNode spec, List<DriftState.Finding> findings) {
    StringBuilder b = new StringBuilder();
    b.append("Contract: ").append(ContractDocument.dialect(spec)).append(", ").append(ContractDocument.title(spec)).append('\n');
    b.append("Documented paths: ").append(String.join(", ", iterable(spec.path("paths").fieldNames()))).append("\n\n");
    b.append("Findings:\n");
    int examplesShown = 0;
    Set<String> paths = new LinkedHashSet<>();
    for (DriftState.Finding f : findings.stream().limit(MAX_FINDINGS).toList()) {
      b.append("- ").append(f.kind().label()).append(": ").append(f.method()).append(' ').append(f.path());
      if (!f.detail().isEmpty()) b.append(" — ").append(f.detail());
      b.append(" (seen ").append(f.count()).append(" times, e.g. ").append(f.exampleUrl()).append(" -> ").append(f.exampleStatus()).append(")\n");
      if (spec.path("paths").has(f.path())) paths.add(f.path());
      if (examplesShown < MAX_EXAMPLES && f.exampleId() != null) {
        Optional<HistoryEntry> example = examples.apply(projectId, f.exampleId());
        if (example.isPresent()) {
          examplesShown++;
          appendExample(b, example.get(), basePath);
        }
      }
    }
    StringBuilder excerpt = new StringBuilder();
    Set<String> schemas = new LinkedHashSet<>();
    for (String path : paths) {
      String json = Jsons.write(spec.path("paths").get(path));
      excerpt.append("paths[\"").append(path).append("\"] = ").append(json).append('\n');
      Matcher m = REF.matcher(json);
      while (m.find()) schemas.add(m.group(1));
    }
    JsonNode definitions = spec.has("swagger") ? spec.path("definitions") : spec.path("components").path("schemas");
    for (String name : schemas) {
      if (excerpt.length() > MAX_EXCERPT) break;
      if (definitions.has(name)) excerpt.append("schema ").append(name).append(" = ").append(Jsons.write(definitions.get(name))).append('\n');
    }
    b.append("\nSchemas defined: ").append(String.join(", ", iterable(definitions.fieldNames()))).append('\n');
    String cut = excerpt.length() > MAX_EXCERPT ? excerpt.substring(0, MAX_EXCERPT) : excerpt.toString();
    if (!cut.isEmpty()) b.append("\nContract excerpt:\n").append(cut);
    return b.append("\nReply with the JSON object.").toString();
  }

  private static void appendExample(StringBuilder b, HistoryEntry e, String basePath) {
    String url = !basePath.isEmpty() && e.url().startsWith(basePath) ? e.url().substring(basePath.length()) : e.url();
    b.append("  Example: ").append(e.method()).append(' ').append(url);
    if (!e.requestBody().isEmpty()) b.append("\n  Request body: ").append(cap(e.requestBody()));
    b.append("\n  Response ").append(e.status());
    if (!e.responseBody().isEmpty()) b.append(": ").append(cap(e.responseBody()));
    b.append('\n');
  }

  private static String cap(String body) {
    return body.length() > MAX_EXAMPLE_BODY ? body.substring(0, MAX_EXAMPLE_BODY) + "…" : body;
  }

  private static List<String> iterable(java.util.Iterator<String> names) {
    List<String> out = new java.util.ArrayList<>();
    names.forEachRemaining(out::add);
    return out;
  }
}
