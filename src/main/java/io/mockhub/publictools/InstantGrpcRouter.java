package io.mockhub.publictools;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.IOException;
import java.util.function.Predicate;

public final class InstantGrpcRouter implements Filter {
  private final Predicate<String> serviceExists;

  public InstantGrpcRouter(Predicate<String> serviceExists) {
    this.serviceExists = serviceExists;
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
    chain.doFilter(request instanceof HttpServletRequest http ? route(http) : request, response);
  }

  HttpServletRequest route(HttpServletRequest request) {
    String contentType = request.getContentType();
    if (contentType == null || !contentType.startsWith("application/grpc")) return request;
    String mock = request.getHeader(InstantGrpc.HEADER);
    if (!InstantGrpc.isSlug(mock)) return request;
    String path = request.getRequestURI();
    int slash = path == null ? -1 : path.lastIndexOf('/');
    if (slash <= 1) return request;
    String service = path.substring(1, slash);
    String namespace = InstantGrpc.namespace(mock);
    if (service.startsWith(namespace + ".")) return request;
    String routed = namespace + "." + service;
    if (!serviceExists.test(routed)) return request;
    String uri = "/" + routed + path.substring(slash);
    return new HttpServletRequestWrapper(request) {
      @Override
      public String getRequestURI() {
        return uri;
      }

      @Override
      public StringBuffer getRequestURL() {
        StringBuffer url = new StringBuffer(request.getRequestURL());
        int at = url.indexOf(path, url.indexOf("//") + 2);
        if (at >= 0) url.replace(at, at + path.length(), uri);
        return url;
      }

      @Override
      public String getServletPath() {
        return "";
      }

      @Override
      public String getPathInfo() {
        return uri;
      }
    };
  }
}
