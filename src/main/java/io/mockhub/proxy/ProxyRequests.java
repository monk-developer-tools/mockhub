package io.mockhub.proxy;

import jakarta.servlet.http.HttpServletRequest;

public final class ProxyRequests {
  private static final String HTTP = "wiremock.isHttpProxyRequest";
  private static final String HTTPS = "wiremock.isHttpsProxyRequest";

  private ProxyRequests() {}

  public static boolean isProxied(HttpServletRequest request) {
    return Boolean.TRUE.equals(request.getAttribute(HTTP)) || Boolean.TRUE.equals(request.getAttribute(HTTPS));
  }
}
