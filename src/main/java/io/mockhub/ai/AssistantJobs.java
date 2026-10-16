package io.mockhub.ai;

import io.mockhub.api.ApiException;
import io.mockhub.project.Project;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;

public final class AssistantJobs {
  public static final String RUNNING = "running";
  public static final String DONE = "done";
  public static final String FAILED = "failed";
  static final int MAX_RUNNING_PER_USER = 3;
  private static final Duration KEEP = Duration.ofMinutes(30);

  public record View(String id, String status, List<AssistantService.Step> steps, AssistantService.Reply reply, String error) {}

  private static final class Job {
    private final String id = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    private final String owner;
    private final String projectId;
    private final List<AssistantService.Step> steps = new CopyOnWriteArrayList<>();
    private volatile String status = RUNNING;
    private volatile AssistantService.Reply reply;
    private volatile String error;
    private volatile Instant finished;

    private Job(String owner, String projectId) {
      this.owner = owner;
      this.projectId = projectId;
    }

    private View view() {
      return new View(id, status, List.copyOf(steps), reply, error);
    }
  }

  private final AssistantService assistant;
  private final Executor executor;
  private final Clock clock;
  private final Map<String, Job> jobs = new ConcurrentHashMap<>();

  public AssistantJobs(AssistantService assistant, Executor executor, Clock clock) {
    this.assistant = assistant;
    this.executor = executor;
    this.clock = clock;
  }

  public View start(String owner, Project project, String mockUrl, List<AssistantService.Message> history, List<ProposedChange> pending) {
    prune();
    List<AssistantService.Message> trimmed = assistant.prepare(history);
    long running = jobs.values().stream().filter(j -> j.owner.equals(owner) && RUNNING.equals(j.status)).count();
    if (running >= MAX_RUNNING_PER_USER) {
      throw new ApiException(429, "You already have " + MAX_RUNNING_PER_USER + " assistant requests running. Wait for one to finish.");
    }
    Job job = new Job(owner, project.id());
    jobs.put(job.id, job);
    executor.execute(() -> run(job, project, mockUrl, trimmed, pending));
    return job.view();
  }

  public View get(String owner, String projectId, String jobId) {
    prune();
    Job job = jobs.get(jobId);
    if (job == null || !job.owner.equals(owner) || !job.projectId.equals(projectId)) {
      throw new ApiException(404, "This assistant request is no longer available. The server may have restarted, so send your message again.");
    }
    return job.view();
  }

  private void run(Job job, Project project, String mockUrl, List<AssistantService.Message> history, List<ProposedChange> pending) {
    try {
      job.reply = assistant.chat(project, mockUrl, history, pending, job.steps::add);
      job.status = DONE;
    } catch (AiException | ApiException e) {
      job.error = e.getMessage();
      job.status = FAILED;
    } catch (RuntimeException e) {
      job.error = "The assistant failed: " + e.getMessage();
      job.status = FAILED;
    } finally {
      job.finished = clock.instant();
    }
  }

  private void prune() {
    Instant cutoff = clock.instant().minus(KEEP);
    jobs.values().removeIf(j -> j.finished != null && j.finished.isBefore(cutoff));
  }
}
