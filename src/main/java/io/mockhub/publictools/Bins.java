package io.mockhub.publictools;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class Bins {
  public static final int MAX_BINS = 200;
  public static final int MAX_REQUESTS = 50;
  public static final int MAX_STORED_BODY = 10 * 1024;
  public static final Duration IDLE = Duration.ofHours(24);
  private static final String ALPHABET = "abcdefghijkmnpqrstuvwxyz23456789";

  private final Map<String, Bin> bins = new ConcurrentHashMap<>();
  private final Clock clock;
  private final SecureRandom random = new SecureRandom();

  public Bins(Clock clock) {
    this.clock = clock;
  }

  public record Created(String id, String token) {}

  public record Captured(long seq, Instant at, String method, String path, String query, Map<String, String> headers, String body,
      int size, boolean truncated) {}

  public record Reply(int status, String contentType, String body) {
    static final Reply DEFAULT = new Reply(200, "application/json", "{\"ok\":true}");
  }

  static final class Bin {
    final String id;
    final byte[] tokenHash;
    final Instant created;
    volatile Instant lastUsed;
    volatile Reply reply = Reply.DEFAULT;
    final Deque<Captured> requests = new ArrayDeque<>();
    final AtomicLong seq = new AtomicLong();

    Bin(String id, byte[] tokenHash, Instant created) {
      this.id = id;
      this.tokenHash = tokenHash;
      this.created = created;
      this.lastUsed = created;
    }
  }

  public Optional<Created> create() {
    prune();
    if (bins.size() >= MAX_BINS) return Optional.empty();
    String id = random(12);
    String token = random(32);
    bins.put(id, new Bin(id, hash(token), clock.instant()));
    return Optional.of(new Created(id, token));
  }

  public Optional<Reply> capture(String id, PublicRequest request) {
    Bin bin = live(id);
    if (bin == null) return Optional.empty();
    String body = request.body() == null ? "" : request.body();
    boolean truncated = body.getBytes(StandardCharsets.UTF_8).length > MAX_STORED_BODY;
    if (truncated) body = cut(body);
    String rest = request.path().substring(("/bins/" + id).length());
    Captured captured = new Captured(bin.seq.incrementAndGet(), clock.instant(), request.method(), rest.isEmpty() ? "/" : rest,
        request.rawQuery(), withoutProxyHeaders(request.headers()), body, request.bodySize(), truncated);
    synchronized (bin.requests) {
      bin.requests.addFirst(captured);
      while (bin.requests.size() > MAX_REQUESTS) bin.requests.removeLast();
    }
    bin.lastUsed = clock.instant();
    return Optional.of(bin.reply);
  }

  public Optional<List<Captured>> requests(String id, String token, long after) {
    Bin bin = owned(id, token);
    if (bin == null) return Optional.empty();
    synchronized (bin.requests) {
      return Optional.of(bin.requests.stream().filter(c -> c.seq() > after).toList());
    }
  }

  public Optional<Reply> reply(String id, String token) {
    return Optional.ofNullable(owned(id, token)).map(b -> b.reply);
  }

  public boolean setReply(String id, String token, Reply reply) {
    Bin bin = owned(id, token);
    if (bin == null) return false;
    bin.reply = reply;
    bin.lastUsed = clock.instant();
    return true;
  }

  public boolean clear(String id, String token) {
    Bin bin = owned(id, token);
    if (bin == null) return false;
    synchronized (bin.requests) {
      bin.requests.clear();
    }
    return true;
  }

  public Optional<Instant> expires(String id) {
    return Optional.ofNullable(live(id)).map(b -> b.lastUsed.plus(IDLE));
  }

  public void prune() {
    Instant cutoff = clock.instant().minus(IDLE);
    bins.values().removeIf(b -> b.lastUsed.isBefore(cutoff));
  }

  int size() {
    return bins.size();
  }

  private Bin live(String id) {
    Bin bin = id == null ? null : bins.get(id);
    if (bin == null) return null;
    if (bin.lastUsed.isBefore(clock.instant().minus(IDLE))) {
      bins.remove(id);
      return null;
    }
    return bin;
  }

  private Bin owned(String id, String token) {
    Bin bin = live(id);
    if (bin == null || token == null || !MessageDigest.isEqual(bin.tokenHash, hash(token))) return null;
    return bin;
  }

  private static Map<String, String> withoutProxyHeaders(Map<String, String> headers) {
    Map<String, String> out = new LinkedHashMap<>();
    headers.forEach((k, v) -> {
      String lower = k.toLowerCase(Locale.ROOT);
      if (!lower.startsWith("x-forwarded-") && !lower.equals("x-real-ip")) out.put(k, v);
    });
    return out;
  }

  private static String cut(String body) {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    return new String(bytes, 0, MAX_STORED_BODY, StandardCharsets.UTF_8);
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
