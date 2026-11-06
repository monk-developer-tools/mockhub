package io.mockhub.publictools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.api.Jsons;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
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
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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
  static final int TAIL = 50;
  static final long TAIL_BYTES = 256 * 1024;
  public static final Duration IDLE = Duration.ofHours(24);
  private static final String ALPHABET = "abcdefghijkmnpqrstuvwxyz23456789";

  private final Map<String, Bin> bins = new ConcurrentHashMap<>();
  private final Path root;
  private final Clock clock;
  private final SecureRandom random = new SecureRandom();

  private final long segmentBytes;

  public Bins(Path dataDir, Clock clock) {
    this(dataDir, clock, SEGMENT_BYTES);
  }

  Bins(Path dataDir, Clock clock, long segmentBytes) {
    this.root = dataDir.resolve("mockhub").resolve("bins");
    this.clock = clock;
    this.segmentBytes = segmentBytes;
    load();
  }

  public record Created(String id, String token) {}

  public record Reply(int status, String contentType, String body, boolean template) {
    static final Reply DEFAULT = new Reply(200, "application/json", "{\"ok\":true}", false);
  }

  public record Settings(Reply reply, boolean rulesEnabled, List<BinRule> rules, BinRouting routing) {
    static final Settings DEFAULT = new Settings(Reply.DEFAULT, true, List.of(), BinRouting.DEFAULT);

    Settings withReply(Reply r) {
      return new Settings(r, rulesEnabled, rules, routing);
    }

    Settings withRules(boolean enabled, List<BinRule> list) {
      return new Settings(reply, enabled, list, routing);
    }

    Settings withRouting(BinRouting r) {
      return new Settings(reply, rulesEnabled, rules, r);
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

  public record Captured(long seq, Instant at, String method, String path, String query, Map<String, String> headers, String body,
      int size, boolean truncated, String event, Outcome outcome) {
    ObjectNode toJson() {
      ObjectNode item = Jsons.object().put("seq", seq).put("at", at.toString()).put("method", method).put("path", path)
          .put("query", query).put("body", body).put("size", size).put("truncated", truncated).put("event", event);
      ObjectNode h = item.putObject("headers");
      headers.forEach(h::put);
      if (outcome != null) item.set("outcome", outcome.toJson());
      return item;
    }

    static Captured from(JsonNode n) {
      Map<String, String> h = new LinkedHashMap<>();
      n.path("headers").fields().forEachRemaining(e -> h.put(e.getKey(), e.getValue().asText()));
      return new Captured(n.path("seq").asLong(), Instant.parse(n.path("at").asText()), n.path("method").asText(), n.path("path").asText(),
          n.path("query").asText(""), h, n.path("body").asText(""), n.path("size").asInt(), n.path("truncated").asBoolean(),
          n.path("event").asText(""), n.has("outcome") ? Outcome.from(n.path("outcome")) : null);
    }
  }

  public record Page(List<Captured> requests, long total, long oldestKept) {}

  static final class Bin {
    final String id;
    final byte[] tokenHash;
    final Instant created;
    volatile Instant lastUsed;
    volatile Settings settings;
    long seq;
    long oldest = 1;
    final Deque<Captured> tail = new ArrayDeque<>();

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
    Bin bin = live(id);
    if (bin == null) return 0;
    String body = request.body() == null ? "" : request.body();
    boolean truncated = body.getBytes(StandardCharsets.UTF_8).length > MAX_STORED_BODY;
    if (truncated) body = cut(body, MAX_STORED_BODY);
    synchronized (bin) {
      long seq = ++bin.seq;
      Captured captured = new Captured(seq, clock.instant(), request.method(), path, request.rawQuery(), withoutProxyHeaders(request.headers()),
          body, request.bodySize(), truncated, event, outcome);
      bin.tail.addFirst(captured);
      append(bin, captured);
      trimTail(bin.tail, bin.oldest);
      bin.lastUsed = clock.instant();
      return seq;
    }
  }

  public void touch(String id) {
    Bin bin = live(id);
    if (bin != null) bin.lastUsed = clock.instant();
  }

  public Optional<Page> requests(String id, String key, long after, long before, int limit) {
    Bin bin = readable(id, key);
    if (bin == null) return Optional.empty();
    int size = Math.max(1, Math.min(limit <= 0 ? PAGE : limit, PAGE));
    Predicate<Captured> wanted = before > 0 ? c -> c.seq() < before : c -> c.seq() > after;
    synchronized (bin) {
      List<Captured> out = new ArrayList<>();
      for (Captured c : bin.tail) {
        if (out.size() >= size) break;
        if (wanted.test(c)) out.add(c);
      }
      long tailOldest = bin.tail.isEmpty() ? bin.seq + 1 : bin.tail.getLast().seq();
      boolean more = out.size() < size && tailOldest > bin.oldest && (before > 0 || after < tailOldest - 1);
      if (more) {
        for (Captured c : fromDisk(bin, tailOldest, wanted, size - out.size())) out.add(c);
      }
      return Optional.of(new Page(List.copyOf(out), bin.seq, bin.oldest));
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

  public boolean clear(String id, String token) {
    Bin bin = owned(id, token);
    if (bin == null) return false;
    synchronized (bin) {
      bin.tail.clear();
      segments(bin).forEach(Bins::delete);
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

  private void remove(Bin bin) {
    bins.remove(bin.id, bin);
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
    out.set("rules", BinRule.toJson(s.rules()));
    out.set("routing", s.routing().toJson());
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
              n.has("routing") ? BinRouting.from(n.path("routing")) : BinRouting.DEFAULT);
          Bin bin = new Bin(n.path("id").asText(), Base64.getDecoder().decode(n.path("tokenHash").asText()), Instant.parse(n.path("created").asText()), settings);
          bin.lastUsed = Instant.parse(n.path("lastUsed").asText());
          bin.seq = n.path("seq").asLong();
          bin.oldest = Math.max(1, n.path("oldest").asLong(1));
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
