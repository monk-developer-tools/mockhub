package io.mockhub.auth;

import com.github.tomakehurst.wiremock.http.RequestMethod;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AccessPolicy {
  private static final String API = "/mockhub/api";
  private static final Pattern PROJECT = Pattern.compile("/projects/([^/]+)(/.*)?");
  private static final Pattern ORG = Pattern.compile("/orgs/([^/]+)(/.*)?");

  private static final Set<String> PUBLIC_API =
      Set.of("/auth/info", "/auth/login", "/auth/sso/exchange");
  private static final Set<String> ANY_SIGNED_IN =
      Set.of("/auth/me", "/auth/password", "/info", "/projects", "/orgs", "/examples");
  private static final Set<String> READ_ONLY_NATIVE_POSTS =
      Set.of("/requests/find", "/requests/count", "/near-misses/request", "/near-misses/request-pattern");
  private static final Set<String> ADMIN_ONLY_NATIVE =
      Set.of("/shutdown", "/reset", "/settings", "/mappings/reset", "/mappings/save");

  private final Function<String, Optional<String>> orgOfProject;

  public AccessPolicy(Function<String, Optional<String>> orgOfProject) {
    this.orgOfProject = orgOfProject;
  }

  public record Decision(boolean allowed, String reason) {
    static Decision allow() {
      return new Decision(true, "");
    }

    static Decision deny(String reason) {
      return new Decision(false, reason);
    }
  }

  public static boolean isPublic(String adminPath) {
    if (adminPath.equals("/health") || adminPath.equals("/mockhub") || adminPath.startsWith("/mockhub/ui")) return true;
    if (!adminPath.startsWith(API)) return false;
    String api = adminPath.substring(API.length());
    return PUBLIC_API.contains(api) || api.matches("/auth/sso/[^/]+/(start|callback)");
  }

  public Decision decide(Principal principal, RequestMethod method, String adminPath) {
    if (principal.isAdmin()) return Decision.allow();
    if (adminPath.startsWith(API)) return decideApi(principal, method, adminPath.substring(API.length()));
    return decideNative(principal.highestRole(), method, adminPath);
  }

  private Decision decideApi(Principal p, RequestMethod method, String path) {
    boolean read = isRead(method);
    if (ANY_SIGNED_IN.contains(path)) {
      return path.equals("/orgs") && !read ? Decision.deny("Only admins can create organisations") : Decision.allow();
    }
    if (path.matches("/examples/[^/]+/install")) return Decision.allow();
    if (path.equals("/stubs/unassigned")) return need(p.highestRole(), Role.VIEWER, "see stubs outside projects");

    Matcher project = PROJECT.matcher(path);
    if (project.matches()) {
      Optional<String> orgId = orgOfProject.apply(project.group(1));
      if (orgId.isEmpty()) return Decision.allow();
      return need(p.roleIn(orgId.get()), read ? Role.VIEWER : Role.EDITOR, read ? "view this project" : "change this project");
    }

    Matcher org = ORG.matcher(path);
    if (org.matches()) {
      Role role = p.roleIn(org.group(1));
      return read ? need(role, Role.VIEWER, "view this organisation") : need(role, Role.OWNER, "manage this organisation");
    }

    if (path.equals("/users") && read) return need(p.highestRole(), Role.OWNER, "list users");
    return Decision.deny("Only admins can do this");
  }

  static Decision decideNative(Role role, RequestMethod method, String path) {
    boolean read = isRead(method) || (RequestMethod.POST.equals(method) && READ_ONLY_NATIVE_POSTS.contains(path));
    if (role == Role.NONE) return Decision.deny("Your account has no role yet; ask an admin for access");
    if (role == Role.VIEWER) return read ? Decision.allow() : Decision.deny("Viewers can only read");
    if (ADMIN_ONLY_NATIVE.contains(path) || path.startsWith("/settings")) {
      return read ? Decision.allow() : Decision.deny("Only admins can do this");
    }
    if (RequestMethod.DELETE.equals(method) && (path.equals("/mappings") || path.equals("/requests"))) {
      return Decision.deny("Only admins can delete everything at once");
    }
    return Decision.allow();
  }

  private static Decision need(Role actual, Role needed, String what) {
    return actual.atLeast(needed) ? Decision.allow() : Decision.deny("You need the " + needed.id() + " role to " + what);
  }

  private static boolean isRead(RequestMethod method) {
    return RequestMethod.GET.equals(method) || RequestMethod.HEAD.equals(method) || RequestMethod.OPTIONS.equals(method);
  }
}
