package io.mockhub.callout;

import io.mockhub.playground.Header;
import java.util.List;

public record OutboundRequest(String method, String url, List<Header> headers, String body) {
  public OutboundRequest {
    headers = headers == null ? List.of() : List.copyOf(headers);
    body = body == null ? "" : body;
  }
}
