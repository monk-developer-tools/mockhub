package io.mockhub.environment;

import com.github.tomakehurst.wiremock.extension.requestfilter.RequestWrapper;
import com.github.tomakehurst.wiremock.http.HttpHeader;
import com.github.tomakehurst.wiremock.http.HttpHeaders;
import com.github.tomakehurst.wiremock.http.Request;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class EnvironmentRequest extends RequestWrapper {
  private final HttpHeaders headers;

  EnvironmentRequest(Request delegate, String environment) {
    super(delegate, null, null, List.of(), List.of(), Map.of(), Map.of(), List.of(), Map.of(), null, null);
    List<HttpHeader> kept = new ArrayList<>();
    for (HttpHeader h : delegate.getHeaders().all()) {
      if (!h.key().equalsIgnoreCase(ProjectEnvironments.HEADER)) kept.add(h);
    }
    kept.add(new HttpHeader(ProjectEnvironments.HEADER, environment));
    this.headers = new HttpHeaders(kept);
  }

  @Override
  public HttpHeaders getHeaders() {
    return headers;
  }

  @Override
  public HttpHeader header(String key) {
    return headers.getHeader(key);
  }

  @Override
  public String getHeader(String key) {
    HttpHeader header = header(key);
    return header.isPresent() ? header.firstValue() : null;
  }

  @Override
  public boolean containsHeader(String key) {
    return header(key).isPresent();
  }

  @Override
  public Set<String> getAllHeaderKeys() {
    Set<String> keys = new LinkedHashSet<>();
    headers.all().forEach(h -> keys.add(h.key()));
    return keys;
  }
}
