package io.mockhub.common;

import com.github.tomakehurst.wiremock.http.Request;

public final class ClientAddress {
  private ClientAddress() {}

  public static String of(Request request) {
    return nearest(request.getClientIp());
  }

  static String nearest(String reported) {
    if (reported == null) return "";
    int comma = reported.lastIndexOf(',');
    return (comma < 0 ? reported : reported.substring(comma + 1)).trim();
  }
}
