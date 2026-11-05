package io.mockhub.proxy;

import io.mockhub.project.Project;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public final class ProxyCaptureFilter extends HttpFilter {
  private final ProxyService proxy;

  public ProxyCaptureFilter(ProxyService proxy) {
    this.proxy = proxy;
  }

  @Override
  protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
    if (!ProxyRequests.isProxied(request)) {
      chain.doFilter(request, response);
      return;
    }
    if (!proxy.enabled() || !proxy.allows(request.getRemoteAddr())) {
      refuse(response, 403, "This MockHub proxy doesn't take requests from " + request.getRemoteAddr()
          + ". An admin can allow more networks with MOCKHUB_PROXY_ALLOW.");
      return;
    }
    Optional<Project> project = proxy.project();
    if (project.isEmpty()) {
      refuse(response, 503, "Choose a project for proxied traffic on the MockHub Server page first.");
      return;
    }
    chain.doFilter(new Captured(request, project.get().basePath()), response);
  }

  private static void refuse(HttpServletResponse response, int status, String message) throws IOException {
    response.setStatus(status);
    response.setContentType("text/plain; charset=utf-8");
    response.getOutputStream().write((message + "\n").getBytes(StandardCharsets.UTF_8));
  }

  private static final class Captured extends HttpServletRequestWrapper {
    private final String base;

    Captured(HttpServletRequest request, String base) {
      super(request);
      this.base = base;
    }

    @Override
    public String getRequestURI() {
      return base + super.getRequestURI();
    }
  }
}
