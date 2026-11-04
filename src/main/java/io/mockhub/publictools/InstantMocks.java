package io.mockhub.publictools;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.api.Jsons;
import io.mockhub.org.Organization;
import io.mockhub.project.Project;
import io.mockhub.project.ProjectService;
import io.mockhub.store.JsonFile;
import io.mockhub.stub.StubService;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class InstantMocks {
  public static final int MAX_MOCKS = 200;
  public static final int MAX_STUBS = 20;
  public static final int DAILY_REQUESTS = 1000;
  public static final Duration IDLE = Duration.ofDays(7);
  public static final String SLUG_PREFIX = "m-";
  private static final String ALPHABET = "abcdefghijkmnpqrstuvwxyz23456789";

  private final JsonFile<List<Entry>> file;
  private final ProjectService projects;
  private final StubService stubs;
  private final Supplier<String> teamId;
  private final Clock clock;
  private final SecureRandom random = new SecureRandom();
  private final Map<String, Entry> bySlug = new LinkedHashMap<>();
  private final Map<String, Count> counts = new ConcurrentHashMap<>();

  public InstantMocks(Path dataDir, ProjectService projects, StubService stubs, Supplier<String> teamId, Clock clock) {
    this.file = new JsonFile<>(JsonFile.inDataDir(dataDir, "instant-mocks.json"), new TypeReference<>() {});
    this.projects = projects;
    this.stubs = stubs;
    this.teamId = teamId;
    this.clock = clock;
    file.read(ArrayList::new).forEach(e -> bySlug.put(e.slug(), e));
  }

  public record Entry(String projectId, String slug, String tokenHash, String createdAt, String changedAt) {
    Entry changed(Instant at) {
      return new Entry(projectId, slug, tokenHash, createdAt, at.toString());
    }
  }

  public record Created(String slug, String token, String projectId) {}

  public static final class Full extends RuntimeException {
    Full(String message) {
      super(message, null, false, false);
    }
  }

  private record Count(LocalDate day, int count) {}

  public synchronized Created create(List<JsonNode> starters) {
    prune();
    if (bySlug.size() >= MAX_MOCKS) throw new Full("The demo holds as many instant mocks as it can right now. Try again later.");
    if (starters.size() > MAX_STUBS) throw new InstantStubRules.Refused("An instant mock holds up to " + MAX_STUBS + " stubs");
    List<ObjectNode> checked = starters.stream().map(InstantStubRules::check).toList();
    String slug = freeSlug();
    String token = randomText(32);
    ObjectNode body = Jsons.object().put("name", "Instant mock " + slug).put("slug", slug)
        .put("description", "Made without an account on the public tools page")
        .put("orgId", Organization.INSTANT_ID).put("teamId", teamId.get());
    Project project = projects.create(body);
    checked.forEach(stub -> stubs.create(project.id(), stub));
    Instant now = clock.instant();
    Entry entry = new Entry(project.id(), slug, hash(token), now.toString(), now.toString());
    bySlug.put(slug, entry);
    save();
    return new Created(slug, token, project.id());
  }

  public synchronized Optional<Entry> owned(String slug, String token) {
    Entry entry = bySlug.get(slug);
    if (entry == null || token == null || token.isBlank()) return Optional.empty();
    if (!MessageDigest.isEqual(HexFormat.of().parseHex(entry.tokenHash()), HexFormat.of().parseHex(hash(token)))) return Optional.empty();
    if (projects.find(entry.projectId()).isEmpty()) {
      bySlug.remove(slug);
      save();
      return Optional.empty();
    }
    return Optional.of(entry);
  }

  public boolean isInstant(Project project) {
    return Organization.INSTANT_ID.equals(project.orgId());
  }

  public List<ObjectNode> stubsOf(Entry entry) {
    List<ObjectNode> out = new ArrayList<>();
    stubs.list(entry.projectId()).forEach(s -> out.add(InstantStubRules.forDisplay(s)));
    return out;
  }

  public synchronized ObjectNode addStub(Entry entry, JsonNode stub) {
    if (stubs.list(entry.projectId()).size() >= MAX_STUBS) throw new InstantStubRules.Refused("An instant mock holds up to " + MAX_STUBS + " stubs");
    ObjectNode created = stubs.create(entry.projectId(), InstantStubRules.check(stub));
    touch(entry);
    return InstantStubRules.forDisplay(created);
  }

  public synchronized ObjectNode updateStub(Entry entry, String stubId, JsonNode stub) {
    ObjectNode updated = stubs.update(entry.projectId(), stubId, InstantStubRules.check(stub));
    touch(entry);
    return InstantStubRules.forDisplay(updated);
  }

  public synchronized void deleteStub(Entry entry, String stubId) {
    stubs.delete(entry.projectId(), stubId);
    touch(entry);
  }

  public synchronized void delete(Entry entry) {
    bySlug.remove(entry.slug());
    counts.remove(entry.projectId());
    projects.find(entry.projectId()).ifPresent(p -> projects.delete(p.id()));
    save();
  }

  public Instant expires(Entry entry) {
    return Instant.parse(entry.changedAt()).plus(IDLE);
  }

  public int requestsToday(String projectId) {
    Count count = counts.get(projectId);
    return count == null || !count.day().equals(today()) ? 0 : count.count();
  }

  public boolean takeRequest(String projectId) {
    LocalDate today = today();
    Count count = counts.compute(projectId, (k, c) -> c == null || !c.day().equals(today) ? new Count(today, 1) : new Count(today, c.count() + 1));
    return count.count() <= DAILY_REQUESTS;
  }

  public synchronized int prune() {
    Instant cutoff = clock.instant().minus(IDLE);
    List<Entry> gone = bySlug.values().stream()
        .filter(e -> Instant.parse(e.changedAt()).isBefore(cutoff) || projects.find(e.projectId()).isEmpty()).toList();
    for (Entry e : gone) {
      bySlug.remove(e.slug());
      counts.remove(e.projectId());
      projects.find(e.projectId()).ifPresent(p -> projects.delete(p.id()));
    }
    if (!gone.isEmpty()) save();
    return gone.size();
  }

  synchronized int size() {
    return bySlug.size();
  }

  private void touch(Entry entry) {
    bySlug.put(entry.slug(), entry.changed(clock.instant()));
    save();
  }

  private void save() {
    file.write(new ArrayList<>(bySlug.values()));
  }

  private LocalDate today() {
    return LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
  }

  private String freeSlug() {
    for (int i = 0; i < 20; i++) {
      String slug = SLUG_PREFIX + randomText(10);
      if (projects.findBySlug(slug).isEmpty()) return slug;
    }
    throw new IllegalStateException("No free slug");
  }

  private String randomText(int length) {
    StringBuilder out = new StringBuilder(length);
    for (int i = 0; i < length; i++) out.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
    return out.toString();
  }

  private static String hash(String token) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
