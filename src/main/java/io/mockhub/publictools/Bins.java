package io.mockhub.publictools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.api.Jsons;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

public final class Bins {
  public static final int MAX_BINS = 1000;
  public static final int MAX_STORED_BODY = 64 * 1024;
  public static final int MAX_ANSWER_BODY = 16 * 1024;
  public static final long SEGMENT_BYTES = 10L * 1024 * 1024;
  public static final int SEGMENTS = 5;
  public static final long MAX_TOTAL_BYTES = 4L * 1024 * 1024 * 1024;
  public static final int PAGE = 100;
  public static final int MAX_DELETED = 10_000;
  public static final int MAX_EXPORT = 1000;
  public static final int MAX_CALLOUTS = 1000;
  public static final int MAX_CALLOUT_BODY = 4 * 1024;
  static final int TAIL = 50;
  static final long TAIL_BYTES = 256 * 1024;
  public static final Duration IDLE = Duration.ofHours(24);
  private static final String CALLOUTS = "callouts.ndjson";
  private static final String ALPHABET = "abcdefghijkmnpqrstuvwxyz23456789";

  private final Map<String, Bin> bins = new ConcurrentHashMap<>();
  private final Path root;
  private final Clock clock;
  private final SecureRandom random = new SecureRandom();

  private final long segmentBytes;
  private final BinRecords records;

  public Bins(Path dataDir, Clock clock) {
    this(dataDir, clock, SEGMENT_BYTES);
  }

  Bins(Path dataDir, Clock clock, long segmentBytes) {
    this.root = dataDir.resolve("mockhub").resolve("bins");
    this.clock = clock;
    this.segmentBytes = segmentBytes;
    this.records = new BinRecords(Optional.of(root));
    load();
  }

  public record Created(String id, String token) {}

  public record Reply(int status, String contentType, String body, boolean template) {
    static final Reply DEFAULT = new Reply(200, "application/json", "{\"ok\":true}", false);
  }

  public record Settings(Reply reply, boolean rulesEnabled, List<BinRule> rules, BinRouting routing, BinOptions options) {
    static final Settings DEFAULT = new Settings(Reply.DEFAULT, true, List.of(), BinRouting.DEFAULT, BinOptions.DEFAULT);

    Settings withReply(Reply r) {
      return new Settings(r, rulesEnabled, rules, routing, options);
    }

    Settings withRules(boolean enabled, List<BinRule> list) {
      return new Settings(reply, enabled, list, routing, options);
    }

    Settings withRouting(BinRouting r) {
      return new Settings(reply, rulesEnabled, rules, r, options);
    }

    Settings withOptions(BinOptions o) {
      return new Settings(reply, rulesEnabled, rules, routing, o);
    }
  }

  public record Outcome(String by, String rule, int status, long millis, Map<String, String> headers, String body, boolean truncated) {
    ObjectNode toJson() {
      ObjectNode out = Jsons.object().put("by", by).put("rule", rule).put("status", status).put("millis", millis).put("body", body).put("truncated", truncated);
      ObjectNode h = out.putObject("headers");
      headers.forEach(h::put);
      return out;
    }

    static Outcome from(JsonNode n) {
      Map<String, String> h = new LinkedHashMap<>();
      n.path("headers").fields().forEachRemaining(e -> h.put(e.getKey(), e.getValue().asText()));
      return new Outcome(n.path("by").asText(), n.path("rule").asText(""), n.path("status").asInt(), n.path("millis").asLong(), h,
          n.path("body").asText(""), n.path("truncated").asBoolean());
    }
  }

  public record Callout(String mode, String state, String method, String url, int status, long millis, long waitMs, String error, String response) {
    static Callout scheduled(String method, String url, long waitMs) {
      return new Callout("async", "scheduled", method, url, 0, 0, waitMs, "", "");
    }

    Callout failed(String why) {
      return new Callout(mode, "failed", method, url, status, millis, waitMs, why, response);
    }

    ObjectNode toJson() {
      return Jsons.object().put("mode", mode).put("state", state).put("method", method).put("url", url).put("status", status)
          .put("millis", millis).put("waitMs", waitMs).put("error", error).put("response", response);
    }

    static Callout from(JsonNode n) {
      return new Callout(n.path("mode").asText("sync"), n.path("state").asText("sent"), n.path("method").asText(""), n.path("url").asText(""),
          n.path("status").asInt(), n.path("millis").asLong(), n.path("waitMs").asLong(), n.path("error").asText(""), n.path("response").asText(""));
    }
  }

  public record Captured(long seq, Instant at, String method, String path, String query, Map<String, String> headers, String body,
      String raw, int size, boolean truncated, String event, Outcome outcome, Callout callout) {
    Captured withCallout(Callout c) {
      return new Captured(seq, at, method, path, query, headers, body, raw, size, truncated, event, outcome, c);
    }

    ObjectNode toJson() {
      ObjectNode item = Jsons.object().put("seq", seq).put("at", at.toString()).put("method", method).put("path", path)
          .put("query", query).put("body", body).put("size", size).put("truncated", truncated).put("event", event);
      if (!raw.isEmpty()) item.put("raw", raw);
      ObjectNode h = item.putObject("headers");
      headers.forEach(h::put);
      if (outcome != null) item.set("outcome", outcome.toJson());
      if (callout != null) item.set("callout", callout.toJson());
      return item;
    }

    static Captured from(JsonNode n) {
      Map<String, String> h = new LinkedHashMap<>();
      n.path("headers").fields().forEachRemaining(e -> h.put(e.getKey(), e.getValue().asText()));
      return new Captured(n.path("seq").asLong(), Instant.parse(n.path("at").asText()), n.path("method").asText(), n.path("path").asText(),
          n.path("query").asText(""), h, n.path("body").asText(""), n.path("raw").asText(""), n.path("size").asInt(), n.path("truncated").asBoolean(),
          n.path("event").asText(""), n.has("outcome") ? Outcome.from(n.path("outcome")) : null, n.has("callout") ? Callout.from(n.path("callout")) : null);
    }
  }

  public record Page(List<Captured> requests, long total, long oldestKept, long deleted) {}

  static final class Bin {
    final String id;
    final byte[] tokenHash;
    final Instant created;
    volatile Instant lastUsed;
    volatile Settings settings;
    long seq;
    long oldest = 1;
    final Deque<Captured> tail = new ArrayDeque<>();
    final Set<Long> deleted = new HashSet<>();
    final TreeMap<Long, Callout> callouts = new TreeMap<>();
    BinState state = new BinState();

    Bin(String id, byte[] tokenHash, Instant created, Settings settings) {
      this.id = id;
      this.tokenHash = tokenHash;
      this.created = created;
      this.lastUsed = created;
      this.settings = settings;
    }
  }

  public Optional<Created> create() {
    prune();
    if (bins.size() >= MAX_BINS || totalBytes() >= MAX_TOTAL_BYTES) return Optional.empty();
    String id = random(12);
    String token = random(32);
    Bin bin = new Bin(id, hash(token), clock.instant(), Settings.DEFAULT);
    bins.put(id, bin);
    save(bin);
    return Optional.of(new Created(id, token));
  }

  public Optional<Settings> settingsFor(String id) {
    return Optional.ofNullable(live(id)).map(b -> b.settings);
  }

  public boolean verify(String id, String token) {
    return owned(id, token) != null;
  }

  public long record(String id, PublicRequest request, String path, String event, Outcome outcome) {
    return record(id, request, path, event, outcome, null);
  }

  public long record(String id, PublicRequest request, String path, String event, Outcome outcome, Callout callout) {
    Bin bin = live(id);
    if (bin == null) return 0;
    String body = request.body() == null ? "" : request.body();
    boolean truncated = body.getBytes(StandardCharsets.UTF_8).length > MAX_STORED_BODY;
    if (truncated) body = cut(body, MAX_STORED_BODY);
    String raw = rawOf(request.raw());
    synchronized (bin) {
      long seq = ++bin.seq;
      Captured captured = new Captured(seq, clock.instant(), request.method(), path, request.rawQuery(), withoutProxyHeaders(request.headers()),
          body, raw, request.bodySize(), truncated, event, outcome, callout);
      bin.tail.addFirst(captured);
      append(bin, captured);
      trimTail(bin.tail, bin.oldest);
      bin.lastUsed = clock.instant();
      return seq;
    }
  }

  public void calloutDone(String id, long seq, Callout callout) {
    Bin bin = live(id);
    if (bin == null) return;
    synchronized (bin) {
      if (seq < bin.oldest || seq > bin.seq) return;
      bin.callouts.put(seq, callout);
      while (bin.callouts.size() > MAX_CALLOUTS) bin.callouts.pollFirstEntry();
      appendCallout(bin, seq, callout);
    }
  }

  public void touch(String id) {
    Bin bin = live(id);
    if (bin != null) bin.lastUsed = clock.instant();
  }

  public Optional<Page> requests(String id, String key, long after, long before, int limit) {
    return requests(id, key, after, before, limit, c -> true);
  }

  public Optional<Page> requests(String id, String key, long after, long before, int limit, Predicate<Captured> filter) {
    Bin bin = readable(id, key);
    if (bin == null) return Optional.empty();
    int size = Math.max(1, Math.min(limit <= 0 ? PAGE : limit, PAGE));
    Predicate<Captured> range = before > 0 ? c -> c.seq() < before : c -> c.seq() > after;
    synchronized (bin) {
      return Optional.of(new Page(newest(bin, range.and(filter), size, before > 0 ? 0 : after), bin.seq, bin.oldest, deletedSince(bin)));
    }
  }

  public Optional<List<Captured>> export(String id, String key, Predicate<Captured> filter, int limit) {
    Bin bin = readable(id, key);
    if (bin == null) return Optional.empty();
    synchronized (bin) {
      return Optional.of(newest(bin, filter, limit, 0));
    }
  }

  public Optional<Captured> one(String id, String key, long seq) {
    Bin bin = live(id);
    if (bin == null || key == null) return Optional.empty();
    boolean allowed = readable(id, key) != null || MessageDigest.isEqual(shareKeyOf(bin.tokenHash, seq).getBytes(StandardCharsets.UTF_8), key.getBytes(StandardCharsets.UTF_8));
    if (!allowed) return Optional.empty();
    synchronized (bin) {
      return newest(bin, c -> c.seq() == seq, 1, 0).stream().findFirst();
    }
  }

  public Optional<String> shareKey(String id, String token, long seq) {
    Bin bin = owned(id, token);
    if (bin == null) return Optional.empty();
    synchronized (bin) {
      if (newest(bin, c -> c.seq() == seq, 1, 0).isEmpty()) return Optional.empty();
    }
    return Optional.of(shareKeyOf(bin.tokenHash, seq));
  }

  public boolean delete(String id, String token, long seq) {
    Bin bin = owned(id, token);
    if (bin == null) return false;
    synchronized (bin) {
      if (seq < bin.oldest || seq > bin.seq || bin.deleted.contains(seq)) return false;
      if (bin.deleted.size() >= MAX_DELETED) return false;
      bin.deleted.add(seq);
      bin.tail.removeIf(c -> c.seq() == seq);
      save(bin);
      return true;
    }
  }

  private List<Captured> newest(Bin bin, Predicate<Captured> wanted, int size, long after) {
    Predicate<Captured> kept = wanted.and(c -> !bin.deleted.contains(c.seq()));
    List<Captured> out = new ArrayList<>();
    for (Captured c : bin.tail) {
      if (out.size() >= size) break;
      if (kept.test(c)) out.add(c);
    }
    long tailOldest = bin.tail.isEmpty() ? bin.seq + 1 : bin.tail.getLast().seq();
    if (out.size() < size && tailOldest > bin.oldest && after < tailOldest - 1) {
      out.addAll(fromDisk(bin, tailOldest, kept, size - out.size()));
    }
    return out.stream().map(c -> withFinishedCallout(bin, c)).toList();
  }

  private static Captured withFinishedCallout(Bin bin, Captured c) {
    Callout done = bin.callouts.get(c.seq());
    return done == null ? c : c.withCallout(done);
  }

  private static long deletedSince(Bin bin) {
    return bin.deleted.stream().filter(s -> s >= bin.oldest).count();
  }

  private static String shareKeyOf(byte[] tokenHash, long seq) {
    return "s" + HexFormat.of().formatHex(hash("share|" + Base64.getEncoder().encodeToString(tokenHash) + "|" + seq)).substring(0, 31);
  }

  static String rawOf(byte[] body) {
    if (body == null || body.length == 0) return "";
    try {
      StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(body));
      return "";
    } catch (CharacterCodingException e) {
      return Base64.getEncoder().encodeToString(body.length > MAX_STORED_BODY ? Arrays.copyOf(body, MAX_STORED_BODY) : body);
    }
  }

  public Optional<String> viewKey(String id, String token) {
    return Optional.ofNullable(owned(id, token)).map(b -> viewKeyOf(b.tokenHash));
  }

  public boolean viewOnly(String id, String key) {
    Bin bin = live(id);
    return bin != null && key != null && MessageDigest.isEqual(viewKeyOf(bin.tokenHash).getBytes(StandardCharsets.UTF_8), key.getBytes(StandardCharsets.UTF_8));
  }

  public Optional<Settings> settings(String id, String token) {
    return Optional.ofNullable(owned(id, token)).map(b -> b.settings);
  }

  public boolean update(String id, String token, UnaryOperator<Settings> change) {
    Bin bin = owned(id, token);
    if (bin == null) return false;
    synchronized (bin) {
      bin.settings = change.apply(bin.settings);
      bin.lastUsed = clock.instant();
      save(bin);
    }
    return true;
  }

  public Optional<BinState> stateFor(String id) {
    return Optional.ofNullable(live(id)).map(b -> b.state);
  }

  public void keepState(String id) {
    Bin bin = live(id);
    if (bin == null || !bin.state.takeChanged()) return;
    synchronized (bin) {
      saveState(bin);
    }
  }

  public Optional<BinState> state(String id, String token) {
    return Optional.ofNullable(owned(id, token)).map(b -> b.state);
  }

  public boolean clearState(String id, String token) {
    Bin bin = owned(id, token);
    if (bin == null) return false;
    bin.state.clear();
    keepState(id);
    return true;
  }

  public boolean clear(String id, String token) {
    Bin bin = owned(id, token);
    if (bin == null) return false;
    synchronized (bin) {
      bin.tail.clear();
      bin.callouts.clear();
      segments(bin).forEach(Bins::delete);
      delete(root.resolve(bin.id).resolve(CALLOUTS));
      bin.oldest = bin.seq + 1;
      save(bin);
    }
    return true;
  }

  public Optional<Instant> expires(String id) {
    return Optional.ofNullable(live(id)).map(b -> b.lastUsed.plus(IDLE));
  }

  public void prune() {
    Instant cutoff = clock.instant().minus(IDLE);
    for (Bin bin : List.copyOf(bins.values())) {
      if (bin.lastUsed.isBefore(cutoff)) remove(bin);
    }
  }

  public void flush() {
    for (Bin bin : bins.values()) {
      synchronized (bin) {
        save(bin);
      }
    }
  }

  int size() {
    return bins.size();
  }

  private Bin live(String id) {
    Bin bin = id == null ? null : bins.get(id);
    if (bin == null) return null;
    if (bin.lastUsed.isBefore(clock.instant().minus(IDLE))) {
      remove(bin);
      return null;
    }
    return bin;
  }

  private Bin owned(String id, String token) {
    Bin bin = live(id);
    if (bin == null || token == null || !MessageDigest.isEqual(bin.tokenHash, hash(token))) return null;
    return bin;
  }

  static void trimTail(Deque<Captured> tail, long oldest) {
    tail.removeIf(c -> c.seq() < oldest);
    long bytes = 0;
    int kept = 0;
    for (Iterator<Captured> it = tail.iterator(); it.hasNext(); ) {
      Captured c = it.next();
      bytes += weight(c);
      kept++;
      if (kept > TAIL || (kept > 1 && bytes > TAIL_BYTES)) it.remove();
    }
  }

  private static long weight(Captured c) {
    return 512L + (c.body() == null ? 0 : c.body().length()) + (c.outcome() == null || c.outcome().body() == null ? 0 : c.outcome().body().length());
  }

  private Bin readable(String id, String key) {
    Bin owner = owned(id, key);
    if (owner != null) return owner;
    return viewOnly(id, key) ? live(id) : null;
  }

  private static String viewKeyOf(byte[] tokenHash) {
    return "v" + HexFormat.of().formatHex(hash("view|" + Base64.getEncoder().encodeToString(tokenHash))).substring(0, 31);
  }

  BinRecords records() {
    return records;
  }

  public boolean reads(String id, String key) {
    return readable(id, key) != null;
  }

  private void remove(Bin bin) {
    bins.remove(bin.id, bin);
    records.forget(bin.id);
    Path dir = root.resolve(bin.id);
    try (Stream<Path> files = Files.exists(dir) ? Files.walk(dir) : Stream.empty()) {
      files.sorted(Comparator.reverseOrder()).forEach(Bins::delete);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private void append(Bin bin, Captured captured) {
    Path dir = root.resolve(bin.id);
    try {
      Files.createDirectories(dir);
      List<Path> segs = segments(bin);
      Path current = segs.isEmpty() ? dir.resolve(segmentName(1)) : segs.get(segs.size() - 1);
      if (Files.exists(current) && Files.size(current) >= segmentBytes) {
        current = dir.resolve(segmentName(number(current) + 1));
        segs = new ArrayList<>(segs);
        segs.add(current);
        while (segs.size() > SEGMENTS) {
          Path dropped = segs.remove(0);
          delete(dropped);
          bin.oldest = firstSeq(segs.get(0)).orElse(captured.seq());
        }
      }
      Files.writeString(current, captured.toJson().toString() + "\n", StandardCharsets.UTF_8,
          StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private void appendCallout(Bin bin, long seq, Callout callout) {
    Path dir = root.resolve(bin.id);
    try {
      Files.createDirectories(dir);
      Path file = dir.resolve(CALLOUTS);
      if (Files.exists(file) && Files.size(file) > segmentBytes) {
        StringBuilder kept = new StringBuilder();
        bin.callouts.forEach((s, c) -> kept.append(calloutLine(s, c)));
        Files.writeString(file, kept.toString(), StandardCharsets.UTF_8);
        return;
      }
      Files.writeString(file, calloutLine(seq, callout), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static String calloutLine(long seq, Callout callout) {
    ObjectNode line = Jsons.object().put("seq", seq);
    line.set("callout", callout.toJson());
    return line + "\n";
  }

  private static void loadCallouts(Bin bin, Path file) throws IOException {
    if (!Files.exists(file)) return;
    try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.isBlank()) continue;
        JsonNode n = Jsons.MAPPER.readTree(line);
        long seq = n.path("seq").asLong();
        if (seq >= bin.oldest && seq <= bin.seq) bin.callouts.put(seq, Callout.from(n.path("callout")));
      }
    }
    while (bin.callouts.size() > MAX_CALLOUTS) bin.callouts.pollFirstEntry();
  }

  private List<Captured> fromDisk(Bin bin, long below, Predicate<Captured> wanted, int limit) {
    List<Captured> out = new ArrayList<>();
    List<Path> segs = new ArrayList<>(segments(bin));
    Collections.reverse(segs);
    for (Path seg : segs) {
      List<Captured> inSegment = new ArrayList<>();
      try (BufferedReader reader = Files.newBufferedReader(seg, StandardCharsets.UTF_8)) {
        String line;
        while ((line = reader.readLine()) != null) {
          if (line.isBlank()) continue;
          Captured c = Captured.from(Jsons.MAPPER.readTree(line));
          if (c.seq() < below && wanted.test(c)) inSegment.add(c);
        }
      } catch (IOException e) {
        continue;
      }
      Collections.reverse(inSegment);
      for (Captured c : inSegment) {
        out.add(c);
        if (out.size() >= limit) return out;
      }
    }
    return out;
  }

  private List<Path> segments(Bin bin) {
    Path dir = root.resolve(bin.id);
    if (!Files.isDirectory(dir)) return List.of();
    try (Stream<Path> files = Files.list(dir)) {
      return files.filter(p -> p.getFileName().toString().matches("requests-\\d{6}\\.ndjson")).sorted(Comparator.comparingInt(Bins::number)).toList();
    } catch (IOException e) {
      return List.of();
    }
  }

  private static String segmentName(int n) {
    return String.format(Locale.ROOT, "requests-%06d.ndjson", n);
  }

  private static int number(Path segment) {
    String name = segment.getFileName().toString();
    return Integer.parseInt(name.substring("requests-".length(), name.length() - ".ndjson".length()));
  }

  private static Optional<Long> firstSeq(Path segment) {
    try (BufferedReader reader = Files.newBufferedReader(segment, StandardCharsets.UTF_8)) {
      String line = reader.readLine();
      return line == null ? Optional.empty() : Optional.of(Jsons.MAPPER.readTree(line).path("seq").asLong());
    } catch (IOException e) {
      return Optional.empty();
    }
  }

  private long totalBytes() {
    if (!Files.isDirectory(root)) return 0;
    try (Stream<Path> files = Files.walk(root)) {
      return files.filter(Files::isRegularFile).mapToLong(p -> {
        try {
          return Files.size(p);
        } catch (IOException e) {
          return 0;
        }
      }).sum();
    } catch (IOException e) {
      return 0;
    }
  }

  private void save(Bin bin) {
    ObjectNode out = Jsons.object().put("id", bin.id).put("tokenHash", Base64.getEncoder().encodeToString(bin.tokenHash))
        .put("created", bin.created.toString()).put("lastUsed", bin.lastUsed.toString()).put("seq", bin.seq).put("oldest", bin.oldest);
    Settings s = bin.settings;
    out.putObject("reply").put("status", s.reply().status()).put("contentType", s.reply().contentType()).put("body", s.reply().body()).put("template", s.reply().template());
    out.put("rulesEnabled", s.rulesEnabled());
    bin.deleted.stream().sorted().forEach(out.withArray("deleted")::add);
    out.set("rules", BinRule.toJson(s.rules()));
    out.set("routing", s.routing().toJson());
    out.set("options", s.options().toJson());
    Path dir = root.resolve(bin.id);
    try {
      Files.createDirectories(dir);
      Path tmp = dir.resolve("bin.json.tmp");
      Files.writeString(tmp, Jsons.write(out), StandardCharsets.UTF_8);
      Files.move(tmp, dir.resolve("bin.json"), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private void saveState(Bin bin) {
    Path dir = root.resolve(bin.id);
    try {
      Files.createDirectories(dir);
      if (bin.state.isEmpty()) {
        Files.deleteIfExists(dir.resolve("state.json"));
        return;
      }
      Path tmp = dir.resolve("state.json.tmp");
      Files.writeString(tmp, Jsons.write(bin.state.toJson()), StandardCharsets.UTF_8);
      Files.move(tmp, dir.resolve("state.json"), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private void load() {
    if (!Files.isDirectory(root)) return;
    try (Stream<Path> dirs = Files.list(root)) {
      for (Path dir : dirs.filter(Files::isDirectory).toList()) {
        Path file = dir.resolve("bin.json");
        if (!Files.exists(file)) continue;
        try {
          JsonNode n = Jsons.MAPPER.readTree(Files.readString(file, StandardCharsets.UTF_8));
          JsonNode r = n.path("reply");
          Settings settings = new Settings(new Reply(r.path("status").asInt(200), r.path("contentType").asText("application/json"), r.path("body").asText(""), r.path("template").asBoolean(false)),
              n.path("rulesEnabled").asBoolean(true), BinRule.listFrom(n.path("rules")),
              n.has("routing") ? BinRouting.from(n.path("routing")) : BinRouting.DEFAULT,
              n.has("options") ? BinOptions.from(n.path("options")) : BinOptions.DEFAULT);
          Bin bin = new Bin(n.path("id").asText(), Base64.getDecoder().decode(n.path("tokenHash").asText()), Instant.parse(n.path("created").asText()), settings);
          bin.lastUsed = Instant.parse(n.path("lastUsed").asText());
          bin.seq = n.path("seq").asLong();
          bin.oldest = Math.max(1, n.path("oldest").asLong(1));
          n.path("deleted").forEach(d -> bin.deleted.add(d.asLong()));
          Path state = dir.resolve("state.json");
          if (Files.exists(state)) bin.state = BinState.from(Jsons.MAPPER.readTree(Files.readString(state, StandardCharsets.UTF_8)));
          List<Path> segs = segments(bin);
          if (!segs.isEmpty()) {
            Path newest = segs.get(segs.size() - 1);
            Instant touched = Files.getLastModifiedTime(newest).toInstant();
            if (touched.isAfter(bin.lastUsed)) bin.lastUsed = touched;
            firstSeq(segs.get(0)).ifPresent(first -> bin.oldest = Math.max(bin.oldest, first));
            List<Captured> recent = fromDisk(bin, Long.MAX_VALUE, c -> true, TAIL);
            recent.forEach(bin.tail::addLast);
            trimTail(bin.tail, bin.oldest);
            if (!recent.isEmpty()) bin.seq = Math.max(bin.seq, recent.get(0).seq());
          }
          loadCallouts(bin, dir.resolve(CALLOUTS));
          if (bin.id.matches("[a-z0-9]{12}")) bins.put(bin.id, bin);
        } catch (IOException | RuntimeException e) {
          continue;
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    prune();
  }

  static ArrayNode listing(List<Captured> list) {
    ArrayNode items = Jsons.MAPPER.createArrayNode();
    list.forEach(c -> items.add(c.toJson()));
    return items;
  }

  private static void delete(Path p) {
    try {
      Files.deleteIfExists(p);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static Map<String, String> withoutProxyHeaders(Map<String, String> headers) {
    Map<String, String> out = new LinkedHashMap<>();
    headers.forEach((k, v) -> {
      String lower = k.toLowerCase(Locale.ROOT);
      if (!lower.startsWith("x-forwarded-") && !lower.equals("x-real-ip")) out.put(k, v);
    });
    return out;
  }

  static String readable(byte[] body) {
    try {
      return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(body)).toString();
    } catch (CharacterCodingException e) {
      return "(" + body.length + " bytes of binary data)";
    }
  }

  static String cut(String body, int max) {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    if (bytes.length <= max) return body;
    return new String(bytes, 0, max, StandardCharsets.UTF_8);
  }

  private String random(int length) {
    StringBuilder out = new StringBuilder(length);
    for (int i = 0; i < length; i++) out.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
    return out.toString();
  }

  private static byte[] hash(String token) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
