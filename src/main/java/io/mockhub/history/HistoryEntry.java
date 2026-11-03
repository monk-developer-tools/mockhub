package io.mockhub.history;

import io.mockhub.playground.Header;
import java.util.List;

public record HistoryEntry(
    String id,
    long at,
    String projectId,
    String method,
    String url,
    String absoluteUrl,
    String clientIp,
    List<Header> requestHeaders,
    String requestBody,
    boolean requestBodyTruncated,
    int status,
    List<Header> responseHeaders,
    String responseBody,
    boolean responseBodyTruncated,
    boolean matched,
    String stubId,
    String stubName,
    long durationMs) {

  public static final int MAX_BODY = 64 * 1024;

  public HistoryEntry {
    requestHeaders = requestHeaders == null ? List.of() : List.copyOf(requestHeaders);
    responseHeaders = responseHeaders == null ? List.of() : List.copyOf(responseHeaders);
    requestBody = requestBody == null ? "" : requestBody;
    responseBody = responseBody == null ? "" : responseBody;
  }

  public String path() {
    int q = url.indexOf('?');
    return q < 0 ? url : url.substring(0, q);
  }

  public String statusClass() {
    return status <= 0 ? "error" : (status / 100) + "xx";
  }

  public HistoryEntry summary() {
    return new HistoryEntry(id, at, projectId, method, url, absoluteUrl, clientIp, List.of(), "", false, status, List.of(), "", false,
        matched, stubId, stubName, durationMs);
  }

  public static String cap(String body) {
    return body == null || body.length() <= MAX_BODY ? body : body.substring(0, MAX_BODY);
  }
}
