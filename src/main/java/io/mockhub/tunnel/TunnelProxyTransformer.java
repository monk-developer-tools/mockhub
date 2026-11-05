package io.mockhub.tunnel;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformerV2;
import com.github.tomakehurst.wiremock.http.HttpHeader;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import io.mockhub.playground.Header;
import io.mockhub.stub.StubMetadata;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class TunnelProxyTransformer implements ResponseDefinitionTransformerV2 {
  public static final String ERROR_HEADER = "X-MockHub-Tunnel-Error";
  static final Duration TIMEOUT = Duration.ofSeconds(30);
  private static final Set<String> HOP_BY_HOP = Set.of("host", "connection", "content-length", "transfer-encoding", "keep-alive",
      "upgrade", "proxy-connection", "proxy-authorization", "te", "trailer");

  private final TunnelGateway tunnels;

  public TunnelProxyTransformer(TunnelGateway tunnels) {
    this.tunnels = tunnels;
  }

  @Override
  public String getName() {
    return "mockhub-tunnel";
  }

  @Override
  public boolean applyGlobally() {
    return true;
  }

  @Override
  public ResponseDefinition transform(ServeEvent event) {
    ResponseDefinition original = event.getResponseDefinition();
    if (!original.isProxyResponse() || !TunnelGateway.isTunnel(original.getProxyBaseUrl())) return original;
    Request request = event.getRequest();
    try {
      URI target = URI.create(stripSlash(original.getProxyBaseUrl()) + forwardedPath(request.getUrl(), original.getProxyUrlPrefixToRemove()));
      String projectId = event.getStubMapping() == null ? null : StubMetadata.projectId(event.getStubMapping());
      TunnelMessages.Response answer = tunnels.send(projectId, target, request.getMethod().getName(), headers(request, original),
          request.getBody() == null ? new byte[0] : request.getBody(), TIMEOUT);
      ResponseDefinitionBuilder out = new ResponseDefinitionBuilder().withStatus(answer.status()).withBody(answer.body());
      for (Header h : answer.headers()) {
        if (!HOP_BY_HOP.contains(h.name().toLowerCase(Locale.ROOT))) out.withHeader(h.name(), h.value());
      }
      return out.build();
    } catch (TunnelException e) {
      return failure(e.status(), e.getMessage());
    } catch (IllegalArgumentException e) {
      return failure(502, "The tunnel address " + original.getProxyBaseUrl() + " isn't valid");
    }
  }

  static String forwardedPath(String url, String prefixToRemove) {
    if (prefixToRemove != null && !prefixToRemove.isEmpty() && url.startsWith(prefixToRemove)) {
      String rest = url.substring(prefixToRemove.length());
      return rest.isEmpty() || rest.startsWith("?") ? "/" + rest : rest;
    }
    return url;
  }

  private static String stripSlash(String base) {
    return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
  }

  private static List<Header> headers(Request request, ResponseDefinition original) {
    List<String> removed = original.getRemoveProxyRequestHeaders() == null ? List.of()
        : original.getRemoveProxyRequestHeaders().stream().map(h -> h.toLowerCase(Locale.ROOT)).toList();
    List<Header> out = new ArrayList<>();
    for (HttpHeader h : request.getHeaders().all()) {
      String name = h.key().toLowerCase(Locale.ROOT);
      if (HOP_BY_HOP.contains(name) || removed.contains(name)) continue;
      h.values().forEach(v -> out.add(new Header(h.key(), v)));
    }
    if (original.getAdditionalProxyRequestHeaders() != null) {
      for (HttpHeader h : original.getAdditionalProxyRequestHeaders().all()) {
        out.removeIf(existing -> existing.name().equalsIgnoreCase(h.key()));
        out.add(new Header(h.key(), h.firstValue()));
      }
    }
    return out;
  }

  private static ResponseDefinition failure(int status, String message) {
    return new ResponseDefinitionBuilder().withStatus(status).withHeader("Content-Type", "text/plain; charset=utf-8")
        .withHeader(ERROR_HEADER, message).withBody(message + "\n").build();
  }
}
