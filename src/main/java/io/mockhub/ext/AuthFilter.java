package io.mockhub.ext;

import com.github.tomakehurst.wiremock.extension.requestfilter.AdminRequestFilterV2;
import com.github.tomakehurst.wiremock.extension.requestfilter.RequestFilterAction;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import io.mockhub.api.AdminPaths;
import io.mockhub.api.Response;
import io.mockhub.auth.AccessPolicy;
import io.mockhub.auth.AuthService;
import io.mockhub.auth.Principal;
import java.util.Optional;

public final class AuthFilter implements AdminRequestFilterV2 {
  private final AuthService auth;
  private final AccessPolicy policy;

  public AuthFilter(AuthService auth, AccessPolicy policy) {
    this.auth = auth;
    this.policy = policy;
  }

  @Override
  public String getName() {
    return "mockhub-auth";
  }

  @Override
  public RequestFilterAction filter(Request request, ServeEvent serveEvent) {
    String path = adminPath(request.getUrl());
    if (AccessPolicy.isPublic(path)) return RequestFilterAction.continueWith(request);

    Optional<Principal> principal = auth.principal(request.getHeader("Authorization"));
    if (principal.isEmpty()) return RequestFilterAction.stopWith(Response.error(401, "Authentication required"));
    AccessPolicy.Decision decision = policy.decide(principal.get(), request.getMethod(), path);
    if (!decision.allowed()) return RequestFilterAction.stopWith(Response.error(403, decision.reason()));
    return RequestFilterAction.continueWith(request);
  }

  static String adminPath(String url) {
    String path = AdminPaths.withinAdmin(url);
    if (path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 1);
    return path;
  }
}
