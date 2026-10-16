package io.mockhub.ai;

import static io.mockhub.support.Fixtures.json;
import static io.mockhub.support.Fixtures.tree;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.mockhub.api.ApiException;
import io.mockhub.journal.RequestLogService;
import io.mockhub.project.Project;
import io.mockhub.project.ProjectService;
import io.mockhub.stub.StubService;
import io.mockhub.support.FakeLlmTransport;
import io.mockhub.support.FakeStubGateway;
import io.mockhub.support.InMemoryProjectRepository;
import io.mockhub.support.MutableClock;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AssistantJobsTest {

  @TempDir
  Path dir;

  private final FakeLlmTransport transport = new FakeLlmTransport();
  private final List<Runnable> queued = new ArrayList<>();
  private final Executor later = queued::add;
  private final MutableClock clock = new MutableClock(Instant.parse("2026-10-16T10:00:00Z"));
  private Project orders;
  private AiSettingsStore settings;
  private AssistantService assistant;

  @BeforeEach
  void setUp() {
    FakeStubGateway gateway = new FakeStubGateway();
    AtomicReference<ProjectService> ref = new AtomicReference<>();
    StubService stubs = new StubService(gateway, ref::get);
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

  private void runQueued() {
    List<Runnable> work = new ArrayList<>(queued);
    queued.clear();
    work.forEach(Runnable::run);
  }

  @Test
  void runsInTheBackgroundAndKeepsTheReplyForLaterPolls() {
    // Given
    transport.reply(FakeLlmTransport.toolCall("call_1", "create_stub",
            json("{'stub':{'request':{'method':'GET','urlPath':'/health'},'response':{'status':200}},'summary':'Health check'}")))
        .reply("{'choices':[{'message':{'content':'Staged a health check.'}}]}");
    AssistantJobs jobs = new AssistantJobs(assistant, later, clock);

    // When
    AssistantJobs.View started = jobs.start("u1", orders, "http://h/orders", ask("Add health"), List.of());
    AssistantJobs.View whileRunning = jobs.get("u1", orders.id(), started.id());
    runQueued();
    AssistantJobs.View finished = jobs.get("u1", orders.id(), started.id());

    // Then
    assertThat(started.status()).isEqualTo(AssistantJobs.RUNNING);
    assertThat(whileRunning.reply()).isNull();
    assertThat(finished.status()).isEqualTo(AssistantJobs.DONE);
    assertThat(finished.reply().reply()).isEqualTo("Staged a health check.");
    assertThat(finished.reply().changes()).hasSize(1);
    assertThat(finished.steps()).extracting(AssistantService.Step::detail).containsExactly("Health check");
  }

  @Test
  void recordsProviderFailures() {
    // Given
    AssistantJobs jobs = new AssistantJobs(assistant, Runnable::run, clock);

    // When
    AssistantJobs.View job = jobs.start("u1", orders, "http://h/orders", ask("Add health"), List.of());

    // Then
    AssistantJobs.View polled = jobs.get("u1", orders.id(), job.id());
    assertThat(polled.status()).isEqualTo(AssistantJobs.FAILED);
    assertThat(polled.error()).contains("No scripted reply left");
  }

  @Test
  void onlyTheOwnerCanReadAJobFromItsOwnProject() {
    // Given
    AssistantJobs jobs = new AssistantJobs(assistant, later, clock);
    String id = jobs.start("u1", orders, "http://h/orders", ask("Add health"), List.of()).id();

    // When / Then
    assertThatThrownBy(() -> jobs.get("u2", orders.id(), id)).isInstanceOf(ApiException.class).hasMessageContaining("no longer available");
    assertThatThrownBy(() -> jobs.get("u1", "other", id)).isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> jobs.get("u1", orders.id(), "missing")).isInstanceOf(ApiException.class);
  }

  @Test
  void limitsRunningJobsPerUserAndValidatesBeforeStarting() {
    // Given
    AssistantJobs jobs = new AssistantJobs(assistant, later, clock);
    for (int i = 0; i < AssistantJobs.MAX_RUNNING_PER_USER; i++) jobs.start("u1", orders, "u", ask("hi"), List.of());

    // When / Then
    assertThatThrownBy(() -> jobs.start("u1", orders, "u", ask("hi"), List.of())).hasMessageContaining("already have");
    assertThat(jobs.start("u2", orders, "u", ask("hi"), List.of()).status()).isEqualTo(AssistantJobs.RUNNING);
    assertThatThrownBy(() -> jobs.start("u2", orders, "u", List.of(), List.of())).hasMessageContaining("user message");
    settings.save(new AiSettings(false, "litellm", "http://llm", "", "gpt-test", 0.2, 3));
    assertThatThrownBy(() -> jobs.start("u3", orders, "u", ask("hi"), List.of())).hasMessageContaining("turned off");
  }

  @Test
  void forgetsFinishedJobsAfterThirtyMinutes() {
    // Given
    AssistantJobs jobs = new AssistantJobs(assistant, Runnable::run, clock);
    String id = jobs.start("u1", orders, "u", ask("hi"), List.of()).id();

    // When
    clock.advance(Duration.ofMinutes(31));

    // Then
    assertThatThrownBy(() -> jobs.get("u1", orders.id(), id)).isInstanceOf(ApiException.class);
  }
}
