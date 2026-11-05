package io.mockhub;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class Config {
  public final int port;
  public final int httpsPort;
  public final Path dataDir;
  public final String publicUrl;
  public final int journalMaxEntries;
  public final boolean stubCors;
  public final List<AuthUser> users;
  public final List<String> extraWireMockArgs;
  public final String loginNote;
  public final String ssoClientSecret;
  public final String ssoAuthority;
  public final String aiApiKey;
  public final String fakeLocale;
  public final boolean playgroundAllowPrivate;
  public final boolean calloutAllowPrivate;
  public final int auditRetentionDays;
  public final int historyDays;
  public final int historyMaxMb;
  public final boolean publicTools;
  public final int maxRecords;
  public final String secretKey;

  public Config(Map<String, String> env) {
    this.port = Integer.parseInt(env.getOrDefault("PORT", "8080"));
    String https = env.getOrDefault("MOCKHUB_HTTPS_PORT", "").trim();
    this.httpsPort = https.isEmpty() ? -1 : Integer.parseInt(https);
    this.dataDir = Path.of(env.getOrDefault("MOCKHUB_DATA_DIR", "./mockhub-data")).toAbsolutePath().normalize();
    this.publicUrl = trimTrailingSlash(env.getOrDefault("MOCKHUB_PUBLIC_URL", ""));
    this.journalMaxEntries = Integer.parseInt(env.getOrDefault("MOCKHUB_JOURNAL_MAX_ENTRIES", "5000"));
    this.stubCors = Boolean.parseBoolean(env.getOrDefault("MOCKHUB_STUB_CORS", "true"));
    this.users = AuthUser.parseAll(env.getOrDefault("MOCKHUB_USERS", ""));
    this.extraWireMockArgs = splitArgs(env.getOrDefault("WIREMOCK_OPTIONS", ""));
    this.loginNote = env.getOrDefault("MOCKHUB_LOGIN_NOTE", "");
    this.ssoClientSecret = env.getOrDefault("MOCKHUB_SSO_CLIENT_SECRET", "");
    this.ssoAuthority = env.getOrDefault("MOCKHUB_SSO_AUTHORITY", "");
    this.aiApiKey = env.getOrDefault("MOCKHUB_AI_API_KEY", "");
    this.fakeLocale = env.getOrDefault("MOCKHUB_FAKE_LOCALE", "en");
    this.playgroundAllowPrivate = Boolean.parseBoolean(env.getOrDefault("MOCKHUB_PLAYGROUND_ALLOW_PRIVATE", "false"));
    this.calloutAllowPrivate = Boolean.parseBoolean(env.getOrDefault("MOCKHUB_CALLOUT_ALLOW_PRIVATE", "false"));
    this.auditRetentionDays = Math.max(1, Integer.parseInt(env.getOrDefault("MOCKHUB_AUDIT_RETENTION_DAYS", "90")));
    this.historyDays = Math.max(1, Integer.parseInt(env.getOrDefault("MOCKHUB_HISTORY_DAYS", "7")));
    this.historyMaxMb = Math.max(1, Integer.parseInt(env.getOrDefault("MOCKHUB_HISTORY_MAX_MB", "200")));
    this.publicTools = Boolean.parseBoolean(env.getOrDefault("MOCKHUB_PUBLIC_TOOLS", "false"));
    this.maxRecords = Math.max(0, Integer.parseInt(env.getOrDefault("MOCKHUB_MAX_RECORDS", "10000")));
    this.secretKey = env.getOrDefault("MOCKHUB_SECRET_KEY", "");
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
