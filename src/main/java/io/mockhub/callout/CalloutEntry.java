package io.mockhub.callout;

import io.mockhub.playground.Header;
import java.util.List;

public record CalloutEntry(
    String id,
    String at,
    String projectId,
    String stubId,
    String stubName,
    String mode,
    Trigger trigger,
    OutboundRequest request,
    Result response,
    String error,
    long durationMs,
    String requestId) {

  public record Trigger(String method, String url) {}

  public record Result(int status, List<Header> headers, String body, boolean truncated) {}

  public boolean succeeded() {
    return error == null && response != null && response.status() < 400;
  }
}
