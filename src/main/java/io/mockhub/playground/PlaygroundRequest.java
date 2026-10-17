package io.mockhub.playground;

import java.util.List;

public record PlaygroundRequest(String method, String url, List<Header> headers, String body) {
  public PlaygroundRequest {
    method = method == null || method.isBlank() ? "GET" : method.trim().toUpperCase();
    url = url == null ? "" : url.trim();
    headers = headers == null ? List.of() : headers.stream().filter(h -> h != null && h.name() != null && !h.name().isBlank()).toList();
    body = body == null ? "" : body;
  }
}
