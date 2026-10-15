package io.mockhub;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class Config {
  public final int port;
  public final Path dataDir;
  public final String publicUrl;
  public final int journalMaxEntries;
  public final boolean stubCors;
  public final List<AuthUser> users;
  public final List<String> extraWireMockArgs;
  public final String loginNote;
  public final String ssoClientSecret;
  public final String ssoAuthority;

  public Config(Map<String, String> env) {
    this.port = Integer.parseInt(env.getOrDefault("PORT", "8080"));
    this.dataDir = Path.of(env.getOrDefault("MOCKHUB_DATA_DIR", "./mockhub-data")).toAbsolutePath().normalize();
    this.publicUrl = trimTrailingSlash(env.getOrDefault("MOCKHUB_PUBLIC_URL", ""));
    this.journalMaxEntries = Integer.parseInt(env.getOrDefault("MOCKHUB_JOURNAL_MAX_ENTRIES", "5000"));
    this.stubCors = Boolean.parseBoolean(env.getOrDefault("MOCKHUB_STUB_CORS", "true"));
    this.users = AuthUser.parseAll(env.getOrDefault("MOCKHUB_USERS", ""));
    this.extraWireMockArgs = splitArgs(env.getOrDefault("WIREMOCK_OPTIONS", ""));
    this.loginNote = env.getOrDefault("MOCKHUB_LOGIN_NOTE", "");
    this.ssoClientSecret = env.getOrDefault("MOCKHUB_SSO_CLIENT_SECRET", "");
    this.ssoAuthority = env.getOrDefault("MOCKHUB_SSO_AUTHORITY", "");
  }

  private static String trimTrailingSlash(String s) {
    return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
  }

  private static List<String> splitArgs(String s) {
    List<String> out = new ArrayList<>();
    for (String part : s.trim().split("\\s+")) {
      if (!part.isEmpty()) out.add(part);
    }
    return out;
  }

  public record AuthUser(String username, String password, String role) {
    static List<AuthUser> parseAll(String spec) {
      List<AuthUser> users = new ArrayList<>();
      for (String entry : spec.split(",")) {
        entry = entry.trim();
        if (entry.isEmpty()) continue;
        String[] parts = entry.split(":", 3);
        if (parts.length < 2) {
          throw new IllegalArgumentException("MOCKHUB_USERS entry must be user:password[:role], got: " + entry);
        }
        String role = parts.length == 3 ? parts[2].toLowerCase() : "admin";
        if (!List.of("admin", "editor", "viewer").contains(role)) {
          throw new IllegalArgumentException("Unknown role '" + role + "' for user " + parts[0]);
        }
        users.add(new AuthUser(parts[0], parts[1], role));
      }
      return users;
    }
  }
}
