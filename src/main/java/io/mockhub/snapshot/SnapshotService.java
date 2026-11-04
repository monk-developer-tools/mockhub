package io.mockhub.snapshot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.api.ApiException;
import io.mockhub.api.Jsons;
import io.mockhub.common.Slugs;
import io.mockhub.files.BodyFiles;
import io.mockhub.project.Project;
import io.mockhub.project.ProjectService;
import io.mockhub.runtime.ProjectScopeMatcher;
import io.mockhub.runtime.RuntimeService;
import io.mockhub.runtime.RuntimeSettings;
import io.mockhub.stub.StubMetadata;
import io.mockhub.stub.StubService;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SnapshotService {
  public static final String CURRENT = "current";
  public static final int MAX_SNAPSHOTS = 50;
  public static final int MAX_SAFETY = 10;

  public record Restored(String name, Snapshot.Summary safety, int stubs, int files) {}

  public record Download(String fileName, byte[] content) {}

  private final SnapshotRepository repository;
  private final ProjectService projects;
  private final StubService stubs;
  private final RuntimeService runtime;
  private final BodyFiles files;
  private final Clock clock;

  public SnapshotService(SnapshotRepository repository, ProjectService projects, StubService stubs, RuntimeService runtime,
      BodyFiles files, Clock clock) {
    this.repository = repository;
    this.projects = projects;
    this.stubs = stubs;
    this.runtime = runtime;
    this.files = files;
    this.clock = clock;
  }

  public List<Snapshot.Summary> list(String projectId) {
    return repository.list(projects.get(projectId).id());
  }

  public Snapshot.Summary create(String projectId, JsonNode body, String by) {
    Project p = projects.get(projectId);
    String name = body.path("name").asText("").trim();
    if (name.isEmpty() || name.length() > 80) throw ApiException.badRequest("Give the snapshot a name of up to 80 characters");
    String purpose = body.path("purpose").asText("").trim();
    if (purpose.length() > 500) throw ApiException.badRequest("Keep the purpose under 500 characters");
    requireRoom(p.id());
    Snapshot snapshot = capture(p, name, purpose, by, false);
    repository.save(snapshot);
    return snapshot.summary();
  }

  public Snapshot get(String projectId, String snapshotId) {
    Project p = projects.get(projectId);
    return repository.find(p.id(), snapshotId).orElseThrow(() -> ApiException.notFound("Snapshot " + snapshotId));
  }

  public Snapshot.Summary delete(String projectId, String snapshotId) {
    Snapshot snapshot = get(projectId, snapshotId);
    repository.delete(snapshot.projectId(), snapshot.id());
    return snapshot.summary();
  }

  public ObjectNode compare(String projectId, String from, String to) {
    Project p = projects.get(projectId);
    if (from == null || from.isBlank() || to == null || to.isBlank()) throw ApiException.badRequest("Say what to compare: from and to");
    return SnapshotDiff.compare(side(p, from), side(p, to));
  }

  public Restored restore(String projectId, String snapshotId, String by) {
    Project p = projects.get(projectId);
    Snapshot snapshot = get(p.id(), snapshotId);
    return apply(p, snapshot, by, "restoring “" + snapshot.name() + "”", snapshot.name());
  }

  public ObjectNode previewPull(String targetId, String sourceId, String ref) {
    Project target = projects.get(targetId);
    Snapshot incoming = source(target, sourceId, ref);
    return SnapshotDiff.compare(side(target, CURRENT), side(target, incoming, incoming.name()));
  }

  public Restored pull(String targetId, String sourceId, String ref, String by) {
    Project target = projects.get(targetId);
    Snapshot incoming = source(target, sourceId, ref);
    String from = incoming.sourceProjectName();
    String label = ref == null || ref.isBlank() || CURRENT.equals(ref) ? from : from + " · " + incoming.name();
    return apply(target, incoming, by, "pulling from " + from, label);
  }

  public Snapshot.Summary upload(String projectId, JsonNode file, String by) {
    Project p = projects.get(projectId);
    Snapshot parsed = SnapshotFile.read(file);
    for (ObjectNode stub : into(parsed, p)) stubs.validate(p.id(), stub);
    if (parsed.runtime() != null) runtime.validate(parsed.runtime());
    requireRoom(p.id());
    Snapshot owned = parsed.ownedBy(newId(), p.id(), parsed.name(), now(), by);
    repository.save(owned);
    return owned.summary();
  }

  public Download download(String projectId, String snapshotId) {
    Project p = projects.get(projectId);
    Snapshot snapshot = get(p.id(), snapshotId);
    String name = Slugs.slugify(snapshot.name(), "");
    String fileName = "mockhub-snapshot-" + p.slug() + (name.isEmpty() ? "" : "-" + name) + ".json";
    try {
      return new Download(fileName, Jsons.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(SnapshotFile.write(snapshot)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private Snapshot source(Project target, String sourceId, String ref) {
    if (sourceId == null || sourceId.isBlank()) throw ApiException.badRequest("Choose the project to pull from");
    Project source = projects.get(sourceId);
    if (source.id().equals(target.id())) throw ApiException.badRequest("Pick another project: to go back in this one, restore a snapshot");
    if (ref == null || ref.isBlank() || CURRENT.equals(ref)) return capture(source, "Now", "", "", false);
    return get(source.id(), ref);
  }

  private Restored apply(Project target, Snapshot snapshot, String by, String reason, String label) {
    List<ObjectNode> incoming = into(snapshot, target);
    for (ObjectNode stub : incoming) stubs.validate(target.id(), stub);
    if (snapshot.runtime() != null) runtime.validate(snapshot.runtime());

    Snapshot safety = capture(target, "Before " + reason, "Saved automatically", by, true);
    repository.save(safety);
    pruneSafety(target.id());

    int count = stubs.replaceAll(target.id(), incoming);
    for (BodyFiles.Info existing : files.list(target.id())) {
      if (!snapshot.files().containsKey(existing.name())) files.delete(target.id(), existing.name());
    }
    snapshot.files().forEach((name, base64) -> files.save(target.id(), name, Base64.getDecoder().decode(base64)));
    if (snapshot.runtime() != null) runtime.update(target.id(), snapshot.runtime());
    return new Restored(label, safety.summary(), count, snapshot.files().size());
  }

  private SnapshotDiff.Side side(Project p, String ref) {
    if (CURRENT.equals(ref)) return side(p, capture(p, "Now", "", "", false), "Now");
    Snapshot snapshot = get(p.id(), ref);
    return side(p, snapshot, snapshot.name());
  }

  private SnapshotDiff.Side side(Project p, Snapshot snapshot, String label) {
    List<ObjectNode> normalized = new ArrayList<>();
    for (ObjectNode stub : into(snapshot, p)) {
      ProjectScopeMatcher.retarget(stub, p.id());
      normalized.add(stub);
    }
    return new SnapshotDiff.Side(label, normalized, snapshot.runtime(), snapshot.apiKeyHeader(), snapshot.files());
  }

  private Snapshot capture(Project p, String name, String purpose, String by, boolean safety) {
    List<ObjectNode> captured = new ArrayList<>();
    for (ObjectNode stub : stubs.inMatchingOrder(p.id())) captured.add(clean(stub));
    Map<String, String> encoded = new LinkedHashMap<>();
    files.snapshot(p.id()).forEach((file, bytes) -> encoded.put(file, Base64.getEncoder().encodeToString(bytes)));
    JsonNode settings = Jsons.MAPPER.valueToTree(RuntimeSettings.of(p.runtime()));
    String header = p.apiKey() == null ? null : p.apiKey().header();
    return new Snapshot(newId(), p.id(), p.id(), p.name(), name, purpose, now(), by, safety, settings, header, captured, encoded);
  }

  private List<ObjectNode> into(Snapshot snapshot, Project target) {
    boolean sameProject = target.id().equals(snapshot.sourceProjectId());
    String fromPrefix = snapshot.sourceProjectId() == null || snapshot.sourceProjectId().isBlank() ? null
        : BodyFiles.bodyFileName(snapshot.sourceProjectId(), "");
    String toPrefix = BodyFiles.bodyFileName(target.id(), "");
    List<ObjectNode> out = new ArrayList<>();
    for (ObjectNode stub : snapshot.stubs()) {
      ObjectNode node = clean(stub);
      String id = node.path("id").asText("");
      if (id.isEmpty()) id = UUID.randomUUID().toString();
      else if (!sameProject) id = UUID.nameUUIDFromBytes((target.id() + ":" + id).getBytes(StandardCharsets.UTF_8)).toString();
      node.put("id", id);
      JsonNode bodyFile = node.path("response").path("bodyFileName");
      if (fromPrefix != null && !sameProject && bodyFile.isTextual() && bodyFile.asText().startsWith(fromPrefix)) {
        ((ObjectNode) node.get("response")).put("bodyFileName", toPrefix + bodyFile.asText().substring(fromPrefix.length()));
      }
      out.add(node);
    }
    return out;
  }

  private static ObjectNode clean(ObjectNode stub) {
    ObjectNode node = stub.deepCopy();
    node.remove("uuid");
    node.remove("insertionIndex");
    if (node.path("metadata").path(StubMetadata.KEY) instanceof ObjectNode meta) meta.remove(StubMetadata.PROJECT_ID);
    return node;
  }

  private void requireRoom(String projectId) {
    long kept = repository.list(projectId).stream().filter(s -> !s.safety()).count();
    if (kept >= MAX_SNAPSHOTS) throw ApiException.badRequest("This project has " + MAX_SNAPSHOTS + " snapshots; delete one first");
  }

  private void pruneSafety(String projectId) {
    List<Snapshot.Summary> safety = repository.list(projectId).stream().filter(Snapshot.Summary::safety).toList();
    for (int i = MAX_SAFETY; i < safety.size(); i++) repository.delete(projectId, safety.get(i).id());
  }

  private String now() {
    return Instant.now(clock).toString();
  }

  private static String newId() {
    return UUID.randomUUID().toString().substring(0, 8);
  }
}
