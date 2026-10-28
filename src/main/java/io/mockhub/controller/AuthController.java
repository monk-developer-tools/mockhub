package io.mockhub.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import io.mockhub.api.ApiException;
import io.mockhub.api.ApiRouter;
import io.mockhub.api.Ctx;
import io.mockhub.api.Jsons;
import io.mockhub.api.Response;
import io.mockhub.auth.AuthService;
import io.mockhub.auth.Principal;
import io.mockhub.auth.Role;
import io.mockhub.sso.SsoException;
import io.mockhub.sso.SsoService;
import io.mockhub.sso.SsoSettings;
import io.mockhub.sso.SsoSettingsStore;
import io.mockhub.user.User;
import io.mockhub.user.UserService;
import io.mockhub.user.UserSource;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

public final class AuthController implements ApiController {
  private static final Logger LOG = LoggerFactory.getLogger(AuthController.class);

  public static final String UI_PATH = "/__admin/mockhub/ui/";

  private final AuthService auth;
  private final UserService users;
  private final UserViews views;
  private final SsoService sso;
  private final SsoSettingsStore ssoSettings;
  private final BaseUrls urls;
  private final String loginNote;

  public AuthController(AuthService auth, UserService users, UserViews views, SsoService sso, SsoSettingsStore ssoSettings,
      BaseUrls urls, String loginNote) {
    this.auth = auth;
    this.users = users;
    this.views = views;
    this.sso = sso;
    this.ssoSettings = ssoSettings;
    this.urls = urls;
    this.loginNote = loginNote;
  }

  public static String callbackPath(String provider) {
    return "/__admin/mockhub/api/auth/sso/" + provider + "/callback";
  }

  @Override
  public void register(ApiRouter r) {
    r.get("/auth/info", ctx -> {
      SsoSettings s = ssoSettings.get();
      ObjectNode out = Jsons.object()
          .put("authEnabled", auth.enabled())
          .put("loginNote", loginNote)
          .put("passwordLogin", !s.active() || s.allowPasswordLogin());
      ObjectNode node = out.putObject("sso").put("enabled", s.active());
      if (s.active()) {
        node.put("provider", s.provider()).put("label", s.buttonLabel())
            .put("startUrl", "/__admin/mockhub/api/auth/sso/" + s.provider() + "/start");
      }
      return out;
    });

    r.post("/auth/login", ctx -> {
      if (!auth.enabled()) return session(Principal.anonymousAdmin(), null);
      Credentials c = credentials(ctx);
      User user = auth.login(c.username(), c.password()).orElseThrow(() -> {
        LOG.warn("Failed sign-in for '{}'", c.username());
        return new ApiException(401, "Invalid username or password");
      });
      LOG.info("{} signed in", user.username());
      SsoSettings s = ssoSettings.get();
      if (s.active() && !s.allowPasswordLogin() && user.source() == UserSource.LOCAL && !user.effectiveServerRole().atLeast(Role.ADMIN)) {
        throw new ApiException(403, "Password sign-in is turned off. Use " + s.buttonLabel() + ".");
      }
      return session(Principal.of(user), auth.issueToken(user));
    });

    r.get("/auth/me", ctx -> views.me(ctx.principal(), auth.enabled()));

    r.post("/auth/password", ctx -> {
      Principal p = ctx.principal();
      if (p.anonymous()) throw ApiException.badRequest("Sign-in is off, so there is no password to change");
      JsonNode body = ctx.json();
      users.changeOwnPassword(p.id(), body.path("currentPassword").asText(""), body.path("newPassword").asText(""));
      User refreshed = users.get(p.id());
      return session(Principal.of(refreshed), auth.issueToken(refreshed));
    });

    r.get("/auth/sso/{provider}/start", ctx -> {
      try {
        String provider = ctx.path("provider");
        return Response.redirect(sso.start(provider, urls.server(ctx) + callbackPath(provider)));
      } catch (SsoException e) {
        return toUi(ctx, "sso_error", e.getMessage());
      }
    });

    r.get("/auth/sso/{provider}/callback", ctx -> {
      String error = ctx.query("error");
      if (error != null) {
        return toUi(ctx, "sso_error", ctx.query("error_description", error));
      }
      try {
        return toUi(ctx, "sso_code", sso.complete(ctx.path("provider"), ctx.query("code"), ctx.query("state")));
      } catch (SsoException e) {
        return toUi(ctx, "sso_error", e.getMessage());
      }
    });

    r.post("/auth/sso/exchange", ctx -> {
      String code = ctx.json().path("code").asText("");
      User user = sso.redeem(code).orElseThrow(() -> new ApiException(401, "The sign-in code has expired. Sign in again."));
      return session(Principal.of(user), auth.issueToken(user));
    });
  }

  private ObjectNode session(Principal principal, String token) {
    ObjectNode out = Jsons.object();
    if (token != null) {
      out.put("token", token);
      out.put("expiresIn", auth.tokenLifetimeSeconds());
    }
    out.set("user", views.me(principal, auth.enabled()));
    return out;
  }

  private ResponseDefinition toUi(Ctx ctx, String key, String value) {
    return Response.redirect(urls.server(ctx) + UI_PATH + "#" + key + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8));
  }

  private record Credentials(String username, String password) {}

  private static Credentials credentials(Ctx ctx) {
    String body = ctx.body();
    if (body != null && !body.isBlank()) {
      JsonNode json = ctx.json();
      return new Credentials(json.path("username").asText(""), json.path("password").asText(""));
    }
    String header = Optional.ofNullable(ctx.request.getHeader("Authorization")).orElse("");
    if (header.regionMatches(true, 0, "Basic ", 0, 6)) {
      try {
        String decoded = new String(Base64.getDecoder().decode(header.substring(6).trim()), StandardCharsets.UTF_8);
        int colon = decoded.indexOf(':');
        if (colon > 0) return new Credentials(decoded.substring(0, colon), decoded.substring(colon + 1));
      } catch (IllegalArgumentException ignored) {
        throw new ApiException(401, "Invalid username or password");
      }
    }
    throw new ApiException(401, "Send a username and password");
  }
}
