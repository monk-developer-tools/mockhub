package io.mockhub.playground;

import com.fasterxml.jackson.core.type.TypeReference;
import io.mockhub.store.JsonFile;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class PlaygroundHistory {
  public static final int MAX_ENTRIES = 100;
  static final int MAX_STORED_BODY = 64 * 1024;
  static final String REDACTED = "••••••";
  private static final Set<String> SECRET_HEADERS = Set.of("authorization", "proxy-authorization", "cookie", "set-cookie");

  private final Path dataDir;

  public PlaygroundHistory(Path dataDir) {
    this.dataDir = dataDir;
  }

  public synchronized List<PlaygroundEntry> list(String user) {
    return file(user).read(ArrayList::new);
  }

  public synchronized void add(String user, PlaygroundEntry entry) {
    List<PlaygroundEntry> entries = new ArrayList<>(list(user));
    entries.add(0, stored(entry));
    while (entries.size() > MAX_ENTRIES) entries.remove(entries.size() - 1);
    file(user).write(entries);
  }

  public synchronized boolean delete(String user, String id) {
    List<PlaygroundEntry> entries = new ArrayList<>(list(user));
    boolean removed = entries.removeIf(e -> e.id().equals(id));
    if (removed) file(user).write(entries);
    return removed;
  }

  public synchronized void clear(String user) {
    file(user).write(new ArrayList<>());
  }

  static PlaygroundEntry stored(PlaygroundEntry e) {
    PlaygroundRequest r = e.request();
    PlaygroundRequest request = new PlaygroundRequest(r.method(), r.url(), redact(r.headers()), cap(r.body()));
    PlaygroundResponse s = e.response();
    PlaygroundResponse response = s == null ? null
        : new PlaygroundResponse(s.status(), redact(s.headers()), cap(s.body()), s.size(), s.truncated() || s.body().length() > MAX_STORED_BODY, s.durationMs());
    return new PlaygroundEntry(e.id(), e.at(), request, response, e.error(), e.local());
  }

  private static List<Header> redact(List<Header> headers) {
    return headers.stream().map(h -> SECRET_HEADERS.contains(h.name().toLowerCase()) ? new Header(h.name(), REDACTED) : h).toList();
  }

  private static String cap(String body) {
    return body == null || body.length() <= MAX_STORED_BODY ? body : body.substring(0, MAX_STORED_BODY);
  }

  private JsonFile<List<PlaygroundEntry>> file(String user) {
    String safe = user.replaceAll("[^A-Za-z0-9_-]", "_");
    return new JsonFile<>(JsonFile.inDataDir(dataDir, "playground").resolve(safe + ".json"), new TypeReference<>() {});
  }
}
