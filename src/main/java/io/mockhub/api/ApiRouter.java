package io.mockhub.api;

import com.github.tomakehurst.wiremock.admin.AdminTask;
import com.github.tomakehurst.wiremock.common.url.PathParams;
import com.github.tomakehurst.wiremock.core.Admin;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import io.mockhub.auth.Principal;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ApiRouter {
  @FunctionalInterface
  public interface Handler {
    Object handle(Ctx ctx) throws Exception;
  }

  private static final Logger LOG = LoggerFactory.getLogger(ApiRouter.class);
  private static final int MAX_AUDITED_BODY = 1_000_000;

  private record Route(RequestMethod method, String template, Pattern pattern, List<String> names, Handler handler) {}

  private final List<Route> routes = new ArrayList<>();
  private final Function<Request, Principal> principals;
  private final ChangeAudit audit;

  public ApiRouter(Function<Request, Principal> principals) {
    this(principals, change -> ChangeAudit.Pending.NONE);
  }

  public ApiRouter(Function<Request, Principal> principals, ChangeAudit audit) {
    this.principals = principals;
    this.audit = audit;
  }

  public ApiRouter get(String path, Handler h) { return add(RequestMethod.GET, path, h); }
  public ApiRouter post(String path, Handler h) { return add(RequestMethod.POST, path, h); }
  public ApiRouter put(String path, Handler h) { return add(RequestMethod.PUT, path, h); }
  public ApiRouter delete(String path, Handler h) { return add(RequestMethod.DELETE, path, h); }

  private ApiRouter add(RequestMethod method, String template, Handler handler) {
    List<String> names = new ArrayList<>();
    Matcher m = Pattern.compile("\\{([^}]+)}").matcher(template);
    StringBuilder regex = new StringBuilder();
    int last = 0;
    while (m.find()) {
      regex.append(Pattern.quote(template.substring(last, m.start()))).append("([^/]+)");
      names.add(m.group(1));
      last = m.end();
    }
    regex.append(Pattern.quote(template.substring(last)));
    routes.add(new Route(method, template, Pattern.compile(regex.toString()), names, handler));
    return this;
  }

  public AdminTask task() {
    return new DispatchTask();
  }

  final class DispatchTask implements AdminTask {
    @Override
    public ResponseDefinition execute(Admin admin, ServeEvent serveEvent, PathParams ignored) {
      var request = serveEvent.getRequest();
      String path = AdminPaths.withinApi(request.getUrl());

      boolean pathMatched = false;
      for (Route route : routes) {
        Matcher m = route.pattern.matcher(path);
        if (!m.matches()) continue;
        pathMatched = true;
        if (!route.method.equals(request.getMethod())) continue;
        Map<String, String> params = new HashMap<>();
        for (int i = 0; i < route.names.size(); i++) {
          params.put(route.names.get(i), URLDecoder.decode(m.group(i + 1), StandardCharsets.UTF_8));
        }
        Ctx ctx = new Ctx(request, params, principals);
        ChangeAudit.Pending pending = beginAudit(route, ctx, params);
        try {
          Object result = route.handler.handle(ctx);
          ResponseDefinition response = result == null ? Response.noContent()
              : result instanceof ResponseDefinition rd ? rd : Response.json(result);
          if (response.getStatus() < 400) finishAudit(pending, route, result);
          return response;
        } catch (ApiException e) {
          if (e.status >= 500) LOG.error("{} {} failed with {}: {}", route.method, path, e.status, e.getMessage(), e);
          return Response.error(e.status, e.getMessage());
        } catch (Exception e) {
          LOG.error("{} {} failed", route.method, path, e);
          return Response.error(500, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
      }
      return pathMatched ? Response.error(405, "Method not allowed") : Response.error(404, "No API route for " + path);
    }
  }

  private ChangeAudit.Pending beginAudit(Route route, Ctx ctx, Map<String, String> params) {
    if (RequestMethod.GET.equals(route.method) || !audit.covers(route.method, route.template)) return ChangeAudit.Pending.NONE;
    try {
      Principal principal = ctx.principal();
      if (principal == null) return ChangeAudit.Pending.NONE;
      return audit.begin(new ChangeAudit.Change(principal, route.method, route.template, Map.copyOf(params), jsonBody(ctx)));
    } catch (ApiException e) {
      return ChangeAudit.Pending.NONE;
    } catch (Exception e) {
      LOG.warn("Could not prepare the audit entry for {} {}", route.method, route.template, e);
      return ChangeAudit.Pending.NONE;
    }
  }

  private static void finishAudit(ChangeAudit.Pending pending, Route route, Object result) {
    try {
      pending.succeeded(result instanceof ResponseDefinition rd ? rd.getBody() : result);
    } catch (Exception e) {
      LOG.warn("Could not write the audit entry for {} {}", route.method, route.template, e);
    }
  }

  private static JsonNode jsonBody(Ctx ctx) {
    try {
      String body = ctx.request.getBodyAsString();
      if (body == null || body.length() > MAX_AUDITED_BODY) return null;
      String trimmed = body.stripLeading();
      return trimmed.startsWith("{") || trimmed.startsWith("[") ? Jsons.MAPPER.readTree(trimmed) : null;
    } catch (Exception e) {
      return null;
    }
  }
}
