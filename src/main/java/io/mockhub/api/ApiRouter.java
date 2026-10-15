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

public final class ApiRouter {
  @FunctionalInterface
  public interface Handler {
    Object handle(Ctx ctx) throws Exception;
  }

  private record Route(RequestMethod method, Pattern pattern, List<String> names, Handler handler) {}

  private final List<Route> routes = new ArrayList<>();
  private final Function<Request, Principal> principals;

  public ApiRouter(Function<Request, Principal> principals) {
    this.principals = principals;
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
    routes.add(new Route(method, Pattern.compile(regex.toString()), names, handler));
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
        try {
          Object result = route.handler.handle(new Ctx(request, params, principals));
          if (result == null) return Response.noContent();
          if (result instanceof ResponseDefinition rd) return rd;
          return Response.json(result);
        } catch (ApiException e) {
          return Response.error(e.status, e.getMessage());
        } catch (Exception e) {
          e.printStackTrace();
          return Response.error(500, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
      }
      return pathMatched ? Response.error(405, "Method not allowed") : Response.error(404, "No API route for " + path);
    }
  }
}
