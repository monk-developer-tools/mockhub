package io.mockhub.ai;

import static io.mockhub.support.Fixtures.json;
import static io.mockhub.support.Fixtures.tree;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import io.mockhub.journal.RequestLogService;
import io.mockhub.project.Project;
import io.mockhub.project.ProjectService;
import io.mockhub.stub.StubService;
import io.mockhub.support.FakeLlmTransport;
import io.mockhub.support.FakeStubGateway;
import io.mockhub.support.InMemoryProjectRepository;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AssistantServiceTest {

  private static final JsonNode TOOL_CALL = FakeLlmTransport.toolCall("call_1", "create_stub",
      json("{'stub':{'name':'Health','request':{'method':'GET','urlPath':'/health'},'response':{'status':200}},'summary':'Health check'}"));

  @TempDir
  Path dir;

  private final FakeLlmTransport transport = new FakeLlmTransport();
  private StubService stubs;
  private Project orders;
  private AiSettingsStore settings;
  private AssistantService assistant;

  @BeforeEach
  void setUp() {
    FakeStubGateway gateway = new FakeStubGateway();
    AtomicReference<ProjectService> ref = new AtomicReference<>();
    stubs = new StubService(gateway, ref::get);
    ProjectService projects = new ProjectService(new InMemoryProjectRepository(), List.of(stubs), Clock.systemUTC());
    ref.set(projects);
    orders = projects.create(tree("{'orgId':'org1','name':'Orders','slug':'orders'}"));
    settings = new AiSettingsStore(dir, "");
    settings.save(new AiSettings(true, "litellm", "http://llm", "", "gpt-test", 0.2, 3));
    assistant = new AssistantService(settings, new ChatCompletions(transport, AiProviders.defaults(), ""), stubs, new RequestLogService(gateway, projects));
  }

  private static List<AssistantService.Message> ask(String text) {
    return List.of(new AssistantService.Message("user", text));
  }

  @Test
  void runsToolsUntilTheModelAnswersAndReturnsStagedChanges() {
    // Given
    transport.reply(TOOL_CALL).reply("{'choices':[{'message':{'content':'Staged a health check.'}}],'usage':{'prompt_tokens':150,'completion_tokens':10}}");

    // When
    AssistantService.Reply reply = assistant.chat(orders, "http://h/orders", ask("Add a health endpoint"), List.of());

    // Then
    assertThat(reply.reply()).isEqualTo("Staged a health check.");
    assertThat(reply.changes()).singleElement().satisfies(c -> assertThat(c.stub().at("/request/urlPath").asText()).isEqualTo("/health"));
    assertThat(reply.steps()).singleElement().satisfies(s -> {
      assertThat(s.tool()).isEqualTo("create_stub");
      assertThat(s.detail()).isEqualTo("Health check");
    });
    assertThat(reply.promptTokens()).isEqualTo(250);
    assertThat(stubs.stubsOf(orders.id())).isEmpty();
    var second = transport.calls.get(1).body().get("messages");
    assertThat(second.get(0).get("content").asText()).contains("Project: \"Orders\"", "base path /orders");
    assertThat(second.get(second.size() - 1).get("role").asText()).isEqualTo("tool");
    assertThat(second.get(second.size() - 1).get("tool_call_id").asText()).isEqualTo("call_1");
  }

  @Test
  void stopsAfterTheToolRoundLimitAndAsksForASummary() {
    // Given
    transport.reply(TOOL_CALL).reply(TOOL_CALL).reply(TOOL_CALL).reply("{'choices':[{'message':{'content':'Done.'}}]}");

    // When
    AssistantService.Reply reply = assistant.chat(orders, "http://h/orders", ask("Add health"), List.of());

    // Then
    assertThat(reply.reply()).isEqualTo("Done.");
    assertThat(transport.calls).hasSize(4);
    assertThat(transport.calls.get(3).body().has("tools")).isFalse();
  }

  @Test
  void mentionsPendingChangesAndRefusesWhenOff() {
    // Given
    transport.reply("{'choices':[{'message':{'content':'Still staged.'}}]}");
    ProposedChange pending = new ProposedChange("c-9", "create", null, json("{'request':{'urlPath':'/a'}}"), null, "Earlier stub");

    // When
    assistant.chat(orders, "http://h/orders", ask("What is staged?"), List.of(pending));

    // Then
    assertThat(transport.calls.get(0).body().at("/messages/1/content").asText()).contains("c-9", "Earlier stub");
    settings.save(new AiSettings(false, "litellm", "http://llm", "", "gpt-test", 0.2, 3));
    assertThatThrownBy(() -> assistant.chat(orders, "http://h/orders", ask("x"), List.of())).hasMessageContaining("turned off");
    assertThatThrownBy(() -> {
      settings.save(new AiSettings(true, "litellm", "http://llm", "", "gpt-test", 0.2, 3));
      assistant.chat(orders, "u", List.of(new AssistantService.Message("assistant", "hi")), List.of());
    }).hasMessageContaining("user message");
  }

  @Test
  void appliesChangesAndReportsFailuresPerChange() {
    // Given
    String id = stubs.create(orders.id(), json("{'request':{'method':'GET','urlPath':'/old'}}")).get("id").asText();
    List<ProposedChange> changes = List.of(
        new ProposedChange("c-1", "create", null, json("{'request':{'method':'GET','urlPath':'/new'},'response':{'status':200}}"), null, "new"),
        new ProposedChange("c-2", "delete", id, null, null, "old"),
        new ProposedChange("c-3", "update", "missing", json("{'request':{'urlPath':'/x'}}"), null, "bad"));

    // When
    List<AssistantService.Applied> results = assistant.apply(orders.id(), changes);

    // Then
    assertThat(results).extracting(AssistantService.Applied::ok).containsExactly(true, true, false);
    assertThat(stubs.list(orders.id()).findValuesAsText("urlPath")).containsExactly("/new");
  }

  @Test
  void trimsHistoryToRecentUserAndAssistantMessages() {
    // Given
    List<AssistantService.Message> history = new java.util.ArrayList<>();
    history.add(new AssistantService.Message("system", "ignore me"));
    for (int i = 0; i < 30; i++) history.add(new AssistantService.Message(i % 2 == 0 ? "user" : "assistant", "m" + i));

    // When
    List<AssistantService.Message> trimmed = AssistantService.trim(history);

    // Then
    assertThat(trimmed).hasSize(24);
    assertThat(trimmed).noneMatch(m -> m.role().equals("system"));
    assertThat(trimmed.get(23).content()).isEqualTo("m29");
  }
}
