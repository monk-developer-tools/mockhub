package io.mockhub.ext;

import com.github.tomakehurst.wiremock.extension.requestfilter.AdminRequestFilterV2;
import com.github.tomakehurst.wiremock.extension.requestfilter.RequestFilterAction;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import io.mockhub.Config.AuthUser;
import io.mockhub.api.AdminPaths;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder.responseDefinition;

public final class AuthFilter implements AdminRequestFilterV2 {
  private static final Set<String> READ_ONLY_POSTS =
      Set.of(
          "/requests/find",
          "/requests/count",
          "/near-misses/request",
          "/near-misses/request-pattern",
          "/mockhub/api/auth/login");

  private static final Set<String> ADMIN_ONLY =
      Set.of("/shutdown", "/reset", "/settings", "/mappings/reset", "/mappings/save");

  private final List<AuthUser> users;

  public AuthFilter(List<AuthUser> users) {
    this.users = users;
  }

  @Override
  public String getName() {
    return "mockhub-auth";
  }

  public boolean enabled() {
    return !users.isEmpty();
  }

  @Override
  public RequestFilterAction filter(Request request, ServeEvent serveEvent) {
    if (!enabled()) {
      return RequestFilterAction.continueWith(request);
    }
    String path = adminPath(request.getUrl());
    if (isPublic(path)) {
      return RequestFilterAction.continueWith(request);
    }

    Optional<AuthUser> user = authenticate(request.getHeader("Authorization"));
    if (user.isEmpty()) {
      return RequestFilterAction.stopWith(error(401, "Authentication required"));
    }
    if (!allowed(user.get().role(), request.getMethod(), path)) {
      return RequestFilterAction.stopWith(
          error(403, "Role '" + user.get().role() + "' may not " + request.getMethod() + " " + path));
    }
    return RequestFilterAction.continueWith(request);
  }

  public Optional<AuthUser> authenticate(String authorizationHeader) {
    if (authorizationHeader == null || !authorizationHeader.regionMatches(true, 0, "Basic ", 0, 6)) {
      return Optional.empty();
    }
    String decoded;
    try {
      decoded = new String(Base64.getDecoder().decode(authorizationHeader.substring(6).trim()), StandardCharsets.UTF_8);
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
    int colon = decoded.indexOf(':');
    if (colon < 0) return Optional.empty();
    String username = decoded.substring(0, colon);
    String password = decoded.substring(colon + 1);
    return users.stream()
        .filter(u -> u.username().equals(username) && passwordMatches(u.password(), password))
        .findFirst();
  }

  static boolean allowed(String role, RequestMethod method, String path) {
    if (role.equals("admin")) return true;
    boolean read = RequestMethod.GET.equals(method) || RequestMethod.OPTIONS.equals(method) || RequestMethod.HEAD.equals(method)
        || (RequestMethod.POST.equals(method) && READ_ONLY_POSTS.contains(path))
        || (RequestMethod.POST.equals(method) && path.matches("/mockhub/api/projects/[^/]+/export/.*"));
    if (role.equals("viewer")) return read;
    if (ADMIN_ONLY.contains(path) || path.startsWith("/settings")) return read;
    if (RequestMethod.DELETE.equals(method) && (path.equals("/mappings") || path.equals("/requests"))) return false;
    return true;
  }

  private static boolean isPublic(String path) {
    return path.equals("/health")
        || path.equals("/mockhub")
        || path.startsWith("/mockhub/ui")
        || path.equals("/mockhub/api/auth/info");
  }

  private static boolean passwordMatches(String configured, String supplied) {
    byte[] expected;
    byte[] actual;
    if (configured.startsWith("sha256:")) {
      expected = configured.substring(7).toLowerCase().getBytes(StandardCharsets.UTF_8);
      actual = sha256Hex(supplied).getBytes(StandardCharsets.UTF_8);
    } else {
      expected = configured.getBytes(StandardCharsets.UTF_8);
      actual = supplied.getBytes(StandardCharsets.UTF_8);
    }
    return MessageDigest.isEqual(expected, actual);
  }

  private static String sha256Hex(String s) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  static String adminPath(String url) {
    String path = AdminPaths.withinAdmin(url);
    if (path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 1);
    return path;
  }

  private static ResponseDefinition error(int status, String message) {
    return responseDefinition()
        .withStatus(status)
        .withHeader("Content-Type", "application/json")
        .withBody("{\"error\":\"" + message.replace("\"", "'") + "\"}")
        .build();
  }
}
