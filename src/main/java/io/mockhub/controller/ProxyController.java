package io.mockhub.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import io.mockhub.api.ApiRouter;
import io.mockhub.proxy.ProxyService;

public final class ProxyController implements ApiController {
  private final ProxyService proxy;

  public ProxyController(ProxyService proxy) {
    this.proxy = proxy;
  }

  @Override
  public void register(ApiRouter r) {
    r.get("/proxy", ctx -> proxy.view());
    r.put("/proxy", ctx -> {
      JsonNode body = ctx.json();
      return proxy.choose(body == null ? null : body.path("projectId").asText(null));
    });
    r.get("/proxy/ca.pem", ctx -> new ResponseDefinitionBuilder()
        .withStatus(200)
        .withHeader("Content-Type", "application/x-pem-file")
        .withHeader("Content-Disposition", "attachment; filename=\"mockhub-proxy-ca.pem\"")
        .withBody(proxy.caPem())
        .build());
  }
}
