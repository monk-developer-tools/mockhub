package io.mockhub.support;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.common.Json;
import com.github.tomakehurst.wiremock.http.LoggedResponse;
import com.github.tomakehurst.wiremock.http.HttpHeader;
import com.github.tomakehurst.wiremock.http.HttpHeaders;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.mockhub.api.Jsons;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

public final class Requests {
  private Requests() {}

  public static LoggedRequest logged(String method, String absoluteUrl, Map<String, String> headers, String body) {
    URI uri = URI.create(absoluteUrl);
    ObjectNode node = Jsons.object()
        .put("url", uri.getRawPath() + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery()))
        .put("absoluteUrl", absoluteUrl)
        .put("method", method)
        .put("clientIp", "127.0.0.1")
        .put("browserProxyRequest", false)
        .put("bodyAsBase64", Base64.getEncoder().encodeToString(body.getBytes(StandardCharsets.UTF_8)));
    ObjectNode h = node.putObject("headers");
    headers.forEach(h::put);
    node.putObject("cookies");
    return Json.read(Jsons.write(node), LoggedRequest.class);
  }

  public static ServeEvent event(LoggedRequest request, StubMapping stub) {
    return ServeEvent.of(request).withStubMapping(stub).withResponseDefinition(new ResponseDefinitionBuilder().withStatus(200).build());
  }

  public static LoggedResponse response(int status, String contentType, String body) {
    return new LoggedResponse(status, new HttpHeaders(new HttpHeader("Content-Type", contentType)),
        Base64.getEncoder().encodeToString(body.getBytes(StandardCharsets.UTF_8)), null, null);
  }
}
