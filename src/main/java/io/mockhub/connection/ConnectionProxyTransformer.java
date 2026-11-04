package io.mockhub.connection;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformerV2;
import com.github.tomakehurst.wiremock.http.HttpHeader;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import io.mockhub.callout.CalloutAuth;
import io.mockhub.callout.CalloutException;
import io.mockhub.playground.Header;
import io.mockhub.stub.StubMetadata;
import io.mockhub.api.Jsons;
import java.util.Locale;

public final class ConnectionProxyTransformer implements ResponseDefinitionTransformerV2 {
  public static final String NAME = "mockhub-connection";
  public static final String PARAMETER = "connection";

  private final CalloutAuth auth;

  public ConnectionProxyTransformer(CalloutAuth auth) {
    this.auth = auth;
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public boolean applyGlobally() {
    return false;
  }

  @Override
  public ResponseDefinition transform(ServeEvent event) {
    ResponseDefinition original = event.getResponseDefinition();
    Object key = original.getTransformerParameters() == null ? null : original.getTransformerParameters().get(PARAMETER);
    if (!original.isProxyResponse() || key == null || key.toString().isBlank()) return original;
    StubMapping stub = event.getStubMapping();
    Header credential;
    try {
      credential = auth.header(stub == null ? null : StubMetadata.projectId(stub), key.toString().trim());
    } catch (CalloutException e) {
      return failure(e.getMessage());
    }
    ResponseDefinitionBuilder.ProxyResponseDefinitionBuilder out = ResponseDefinitionBuilder.like(original).proxiedFrom(original.getProxyBaseUrl());
    String name = credential.name().toLowerCase(Locale.ROOT);
    if (original.getAdditionalProxyRequestHeaders() != null) {
      for (HttpHeader h : original.getAdditionalProxyRequestHeaders().all()) {
        if (!h.key().toLowerCase(Locale.ROOT).equals(name)) out.withAdditionalRequestHeader(h.key(), h.firstValue());
      }
    }
    if (original.getRemoveProxyRequestHeaders() != null) {
      for (String removed : original.getRemoveProxyRequestHeaders()) {
        if (!removed.toLowerCase(Locale.ROOT).equals(name)) out.withRemoveRequestHeader(removed);
      }
    }
    if (original.getProxyUrlPrefixToRemove() != null) out.withProxyUrlPrefixToRemove(original.getProxyUrlPrefixToRemove());
    return out.withAdditionalRequestHeader(credential.name(), credential.value()).build();
  }

  private static ResponseDefinition failure(String message) {
    return new ResponseDefinitionBuilder()
        .withStatus(502)
        .withHeader("Content-Type", "application/json")
        .withBody(Jsons.write(Jsons.object().put("error", "Proxy connection failed: " + message)))
        .build();
  }
}
