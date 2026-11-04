package io.mockhub.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.api.ApiException;
import io.mockhub.api.Jsons;
import io.mockhub.callout.CalloutLog;
import io.mockhub.history.HistoryStore;
import io.mockhub.project.Project;
import io.mockhub.runtime.RuntimeService;
import io.mockhub.snapshot.SnapshotService;
import io.mockhub.stub.StubService;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

public final class AssistantService {
  private static final int MAX_HISTORY = 24;
  private static final int MAX_MESSAGE_CHARS = 60_000;
  private static final Set<String> ROLES = Set.of("user", "assistant");

  private final AiSettingsStore settings;
  private final ChatCompletions chat;
  private final StubService stubs;
  private final HistoryStore requestHistory;
  private final Clock clock;
  private final CalloutLog callouts;
  private final RuntimeService runtime;
  private final SnapshotService snapshots;

  public AssistantService(AiSettingsStore settings, ChatCompletions chat, StubService stubs, HistoryStore requestHistory, Clock clock,
      CalloutLog callouts, RuntimeService runtime, SnapshotService snapshots) {
    this.runtime = runtime;
    this.snapshots = snapshots;
    this.settings = settings;
    this.chat = chat;
    this.stubs = stubs;
    this.requestHistory = requestHistory;
    this.clock = clock;
    this.callouts = callouts;
  }

  public record Message(String role, String content) {}

  public record Step(String tool, String detail) {}

  public record Reply(String reply, List<ProposedChange> changes, List<Step> steps, int promptTokens, int completionTokens) {}

  public record Applied(String changeId, boolean ok, String stubId, String error) {}

  public boolean enabled() {
    return settings.get().enabled();
  }

  public Reply chat(Project project, String mockUrl, List<Message> history, List<ProposedChange> pending) {
    return chat(project, mockUrl, history, pending, step -> {});
  }

  public List<Message> prepare(List<Message> history) {
    if (!settings.get().enabled()) throw new ApiException(409, "The AI assistant is turned off. An admin can turn it on under AI assistant.");
    List<Message> trimmed = trim(history);
    if (trimmed.isEmpty() || !trimmed.get(trimmed.size() - 1).role().equals("user")) {
      throw ApiException.badRequest("Send at least one user message");
    }
    return trimmed;
  }

  public Reply chat(Project project, String mockUrl, List<Message> history, List<ProposedChange> pending, Consumer<Step> onStep) {
    List<Message> trimmed = prepare(history);
    AiSettings s = settings.get();

    MockTools tools = new MockTools(stubs, requestHistory, clock, callouts, runtime, snapshots::list, project.id(), pending);
    ArrayNode messages = Jsons.MAPPER.createArrayNode();
    messages.addObject().put("role", "system").put("content", SystemPrompt.forProject(project, mockUrl, stubs.stubsOf(project.id()).size()));
    if (!pending.isEmpty()) {
      messages.addObject().put("role", "system").put("content", "Changes already staged and not applied yet:\n" + describe(pending));
    }
    trimmed.forEach(m -> messages.addObject().put("role", m.role()).put("content", m.content()));

    ArrayNode definitions = MockTools.definitions();
    List<Step> steps = new ArrayList<>();
    int prompt = 0;
    int completion = 0;
    for (int round = 0; round < s.maxToolRounds(); round++) {
      ChatCompletions.Completion result;
      try {
        result = chat.complete(s, messages, definitions);
      } catch (AiException e) {
        return partial(e, tools, steps, prompt, completion);
      }
      prompt += result.promptTokens();
      completion += result.completionTokens();
      JsonNode calls = result.message().path("tool_calls");
      if (!calls.isArray() || calls.isEmpty()) {
        return new Reply(result.message().path("content").asText(""), tools.changes(), steps, prompt, completion);
      }
      ObjectNode assistant = messages.addObject().put("role", "assistant");
      assistant.set("content", result.message().path("content").isTextual() ? result.message().get("content") : null);
      assistant.set("tool_calls", calls);
      for (JsonNode call : calls) {
        String name = call.path("function").path("name").asText();
        JsonNode args = parseArguments(call.path("function").path("arguments"));
        String output = args == null ? Jsons.write(Jsons.object().put("error", "Arguments must be a JSON object")) : tools.execute(name, args);
        Step step = new Step(name, detail(name, args, output));
        steps.add(step);
        onStep.accept(step);
        messages.addObject().put("role", "tool").put("tool_call_id", call.path("id").asText()).put("content", output);
      }
    }
    messages.addObject().put("role", "user").put("content", "Stop using tools now and summarise what you staged.");
    ChatCompletions.Completion last;
    try {
      last = chat.complete(s, messages, null);
    } catch (AiException e) {
      return partial(e, tools, steps, prompt, completion);
    }
    return new Reply(last.message().path("content").asText(""), tools.changes(), steps,
        prompt + last.promptTokens(), completion + last.completionTokens());
  }

  public List<Applied> apply(String projectId, List<ProposedChange> changes, String by) {
    List<Applied> results = new ArrayList<>();
    List<ProposedChange> ordered = new ArrayList<>(changes.stream().filter(c -> ProposedChange.SNAPSHOT.equals(c.action())).toList());
    changes.stream().filter(c -> !ProposedChange.SNAPSHOT.equals(c.action())).forEach(ordered::add);
    for (ProposedChange c : ordered) {
      try {
        String stubId = switch (c.action()) {
          case ProposedChange.CREATE -> stubs.create(projectId, c.stub()).path("id").asText();
          case ProposedChange.UPDATE -> stubs.update(projectId, c.stubId(), c.stub()).path("id").asText();
          case ProposedChange.DELETE -> {
            stubs.delete(projectId, c.stubId());
            yield c.stubId();
          }
          case ProposedChange.RUNTIME -> {
            runtime.update(projectId, c.stub());
            yield null;
          }
          case ProposedChange.SNAPSHOT -> {
            snapshots.create(projectId, c.stub() == null ? Jsons.object() : c.stub(), by);
            yield null;
          }
          case ProposedChange.RESTORE -> {
            snapshots.restore(projectId, c.stubId(), by);
            yield null;
          }
          default -> throw ApiException.badRequest("Unknown change '" + c.action() + "'");
        };
        results.add(new Applied(c.id(), true, stubId, null));
      } catch (ApiException e) {
        results.add(new Applied(c.id(), false, c.stubId(), e.getMessage()));
      }
    }
    return results;
  }

  private static Reply partial(AiException e, MockTools tools, List<Step> steps, int prompt, int completion) {
    if (tools.changes().isEmpty()) throw e;
    String note = "The AI provider stopped before I finished: " + e.getMessage()
        + "\n\nThe changes I staged so far are ready to review. Ask me to continue for the rest.";
    return new Reply(note, tools.changes(), steps, prompt, completion);
  }

  static List<Message> trim(List<Message> history) {
    List<Message> valid = history.stream()
        .filter(m -> m != null && ROLES.contains(m.role()) && m.content() != null && !m.content().isBlank())
        .map(m -> new Message(m.role(), m.content().length() > MAX_MESSAGE_CHARS ? m.content().substring(0, MAX_MESSAGE_CHARS) : m.content()))
        .toList();
    return valid.size() > MAX_HISTORY ? valid.subList(valid.size() - MAX_HISTORY, valid.size()) : valid;
  }

  private static String describe(List<ProposedChange> pending) {
    StringBuilder sb = new StringBuilder();
    for (ProposedChange c : pending) {
      sb.append("- ").append(c.id()).append(": ").append(c.action());
      if (c.stubId() != null) sb.append(" stub ").append(c.stubId());
      sb.append(" — ").append(c.summary()).append('\n');
    }
    return sb.toString();
  }

  private static JsonNode parseArguments(JsonNode raw) {
    try {
      JsonNode args = raw.isTextual() ? Jsons.MAPPER.readTree(raw.asText().isBlank() ? "{}" : raw.asText()) : raw;
      return args != null && args.isObject() ? args : null;
    } catch (Exception e) {
      return null;
    }
  }

  private static String detail(String tool, JsonNode args, String output) {
    if (output.contains("\"error\"")) {
      try {
        return "Error: " + Jsons.MAPPER.readTree(output).path("error").asText();
      } catch (Exception e) {
        return "Error";
      }
    }
    if (args == null) return "";
    return switch (tool) {
      case "create_stub", "update_stub", "delete_stub", "set_runtime" -> args.path("summary").asText("");
      case "get_stub" -> args.path("id").asText("");
      case "search_requests" -> requestFilters(args);
      default -> "";
    };
  }

  private static String requestFilters(JsonNode args) {
    List<String> parts = new ArrayList<>();
    if (!args.path("text").asText("").isBlank()) parts.add("\"" + args.path("text").asText() + "\"");
    if (!args.path("method").asText("").isBlank()) parts.add(args.path("method").asText().toUpperCase(Locale.ROOT));
    if (!args.path("status").asText("").isBlank()) parts.add("status " + args.path("status").asText());
    if (args.path("unmatched_only").asBoolean(false)) parts.add("unmatched only");
    if (!args.path("header").asText("").isBlank()) parts.add("header " + args.path("header").asText());
    if (args.path("since_minutes").asInt(0) > 0) parts.add("last " + args.path("since_minutes").asInt() + " min");
    return String.join(", ", parts);
  }
}
