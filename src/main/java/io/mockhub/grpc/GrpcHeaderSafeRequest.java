package io.mockhub.grpc;

import com.github.tomakehurst.wiremock.http.ContentTypeHeader;
import com.github.tomakehurst.wiremock.http.Cookie;
import com.github.tomakehurst.wiremock.http.FormParameter;
import com.github.tomakehurst.wiremock.http.HttpHeader;
import com.github.tomakehurst.wiremock.http.HttpHeaders;
import com.github.tomakehurst.wiremock.http.QueryParameter;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.RequestMethod;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

final class GrpcHeaderSafeRequest implements Request {
  private final Request delegate;

  GrpcHeaderSafeRequest(Request delegate) {
    this.delegate = delegate;
  }

  @Override
  public UUID getId() {
    return delegate.getId();
  }

  @Override
  public String getUrl() {
    return delegate.getUrl();
  }

  @Override
  public String getAbsoluteUrl() {
    return delegate.getAbsoluteUrl();
  }

  @Override
  public RequestMethod getMethod() {
    return delegate.getMethod();
  }

  @Override
  public String getScheme() {
    return delegate.getScheme();
  }

  @Override
  public String getHost() {
    return delegate.getHost();
  }

  @Override
  public int getPort() {
    return delegate.getPort();
  }

  @Override
  public String getClientIp() {
    return delegate.getClientIp();
  }

  @Override
  public String getHeader(String key) {
    HttpHeader header = header(key);
    return header.isPresent() ? header.firstValue() : null;
  }

  @Override
  public HttpHeader header(String key) {
    HttpHeaders headers = getHeaders();
    return headers == null ? HttpHeader.absent(key) : headers.getHeader(key);
  }

  @Override
  public ContentTypeHeader contentTypeHeader() {
    HttpHeaders headers = getHeaders();
    return headers == null ? ContentTypeHeader.absent() : headers.getContentTypeHeader();
  }

  @Override
  public HttpHeaders getHeaders() {
    return delegate.getHeaders();
  }

  @Override
  public boolean containsHeader(String key) {
    return header(key).isPresent();
  }

  @Override
  public Set<String> getAllHeaderKeys() {
    HttpHeaders headers = getHeaders();
    return headers == null ? Set.of() : headers.keys();
  }

  @Override
  public QueryParameter queryParameter(String key) {
    return QueryParameter.absent(key);
  }

  @Override
  public FormParameter formParameter(String key) {
    return FormParameter.absent(key);
  }

  @Override
  public Map<String, FormParameter> formParameters() {
    return Map.of();
  }

  @Override
  public Map<String, Cookie> getCookies() {
    return Map.of();
  }

  @Override
  public byte[] getBody() {
    return delegate.getBody();
  }

  @Override
  public String getBodyAsString() {
    return delegate.getBodyAsString();
  }

  @Override
  public String getBodyAsBase64() {
    return delegate.getBodyAsBase64();
  }

  @Override
  public boolean isMultipart() {
    return false;
  }

  @Override
  public Collection<Part> getParts() {
    return List.of();
  }

  @Override
  public Part getPart(String name) {
    return null;
  }

  @Override
  public boolean isBrowserProxyRequest() {
    return false;
  }

  @Override
  public Optional<Request> getOriginalRequest() {
    return Optional.empty();
  }

  @Override
  public String getProtocol() {
    return "HTTP/2";
  }
}
