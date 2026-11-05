package io.mockhub.domain;

import io.mockhub.project.Domains;
import io.mockhub.proxy.ProxyRequests;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.function.Function;

public final class CustomDomainFilter extends HttpFilter {
  private static final String ADMIN = "/__admin";

  private final Function<String, Optional<String>> basePathForHost;

  public CustomDomainFilter(Function<String, Optional<String>> basePathForHost) {
    this.basePathForHost = basePathForHost;
  }

  @Override
  protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
    chain.doFilter(rebased(request), response);
  }

  HttpServletRequest rebased(HttpServletRequest request) {
    String uri = request.getRequestURI();
    if (uri == null || uri.equals(ADMIN) || uri.startsWith(ADMIN + "/") || isGrpc(request) || ProxyRequests.isProxied(request)) return request;
    return Domains.hostOf(request.getHeader("Host"))
        .flatMap(basePathForHost)
        .filter(base -> !base.isEmpty())
        .<HttpServletRequest>map(base -> new Rebased(request, base))
        .orElse(request);
  }

  private static boolean isGrpc(HttpServletRequest request) {
    String type = request.getContentType();
    return type != null && type.startsWith("application/grpc");
  }

  private static final class Rebased extends HttpServletRequestWrapper {
    private final String base;

    Rebased(HttpServletRequest request, String base) {
      super(request);
      this.base = base;
    }

    @Override
    public String getRequestURI() {
      return base + super.getRequestURI();
    }

    @Override
    public StringBuffer getRequestURL() {
      StringBuffer url = super.getRequestURL();
      int pathStart = url.indexOf("/", url.indexOf("://") + 3);
      if (pathStart < 0) return url.append(base);
      return url.insert(pathStart, base);
    }
  }
}
