package io.mockhub.runtime;

import io.mockhub.common.ClientAddress;
import com.github.tomakehurst.wiremock.common.Urls;
import com.github.tomakehurst.wiremock.extension.requestfilter.RequestFilterAction;
import com.github.tomakehurst.wiremock.extension.requestfilter.StubRequestFilterV2;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import io.mockhub.project.Project;
import io.mockhub.tls.ClientCertificates;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public final class RuntimeFilter implements StubRequestFilterV2 {
  private final Function<String, Optional<Project>> projectForPath;
  private final CorsHeaders cors;
  private final RateLimiter limiter;
  private final LoadMeter load;

  public RuntimeFilter(Function<String, Optional<Project>> projectForPath, CorsHeaders cors, RateLimiter limiter, LoadMeter load) {
    this.projectForPath = projectForPath;
    this.cors = cors;
    this.limiter = limiter;
    this.load = load;
  }

  @Override
  public String getName() {
    return "mockhub-runtime-filter";
  }

  @Override
  public RequestFilterAction filter(Request request, ServeEvent serveEvent) {
    Optional<Project> project = projectForPath.apply(firstSegment(Urls.getPath(request.getUrl())));
    RuntimeSettings settings = RuntimeSettings.of(project.map(Project::runtime).orElse(null));
    CorsRules rules = cors.rulesFor(settings);
    String origin = request.getHeader("Origin");
    if (isPreflight(request)) {
      return RequestFilterAction.stopWith(RuntimeResponses.preflight(cors.forPreflight(rules, origin, request.getHeader("Access-Control-Request-Headers"))));
    }
    if (project.isEmpty()) return RequestFilterAction.continueWith(request);
    load.record(project.get().id());
    Map<String, String> corsHeaders = cors.forResponse(rules, origin);
    if (settings.requireClientCert() && request.getHeader(ClientCertificates.SUBJECT) == null) {
      return RequestFilterAction.stopWith(RuntimeResponses.clientCertificateRequired(corsHeaders));
    }
    if (!settings.ipRules().allows(ClientAddress.of(request))) {
      return RequestFilterAction.stopWith(RuntimeResponses.forbiddenIp(ClientAddress.of(request), corsHeaders));
    }
    if (settings.rateLimit() != null) {
      RateLimiter.Decision decision = limiter.take(project.get().id(), settings.rateLimit());
      if (!decision.allowed()) {
        return RequestFilterAction.stopWith(RuntimeResponses.rateLimited(decision, settings.rateLimit().describe() + " for this project", corsHeaders));
      }
    }
    return RequestFilterAction.continueWith(request);
  }

  static boolean isPreflight(Request request) {
    return request.getMethod().equals(RequestMethod.OPTIONS) && request.getHeader("Origin") != null
        && request.getHeader("Access-Control-Request-Method") != null;
  }

  public static String firstSegment(String path) {
    String trimmed = path.startsWith("/") ? path.substring(1) : path;
    int slash = trimmed.indexOf('/');
    return slash < 0 ? trimmed : trimmed.substring(0, slash);
  }
}
