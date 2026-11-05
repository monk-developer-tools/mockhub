package io.mockhub.soap;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformerV2;
import com.github.tomakehurst.wiremock.http.HttpHeader;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import java.util.Optional;

public final class SoapEndpointTransformer implements ResponseDefinitionTransformerV2 {
  public static final String NAME = "mockhub-soap";
  public static final String UNKNOWN = "unknown";
  public static final String OPERATION_HEADER = "X-MockHub-Soap-Operation";

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
    Request request = event.getRequest();
    String contentType = request.contentTypeHeader().isPresent() ? request.contentTypeHeader().firstValue() : null;
    String action = request.containsHeader("SOAPAction") ? request.getHeader("SOAPAction") : null;
    Optional<SoapMessage> message = SoapMessage.detect(contentType, action, request.getBodyAsString());
    if (message.isEmpty()) {
      return answer(original, 400, "text/xml; charset=utf-8", null, SoapResponses.fault(SoapMessage.SOAP11, "The request isn't a SOAP 1.1 or 1.2 envelope"));
    }
    SoapMessage m = message.get();
    if (m.operation() == null) return answer(original, 400, m.contentType(), null, SoapResponses.fault(m.version(), "The SOAP Body is empty"));
    Object unknown = original.getTransformerParameters() == null ? null : original.getTransformerParameters().get(UNKNOWN);
    if ("fault".equals(String.valueOf(unknown))) {
      return answer(original, 500, m.contentType(), m.operation(), SoapResponses.fault(m.version(), "No mock configured for operation " + m.operation()));
    }
    return answer(original, 200, m.contentType(), m.operation(), SoapResponses.answer(m));
  }

  private static ResponseDefinition answer(ResponseDefinition original, int status, String contentType, String operation, String body) {
    ResponseDefinitionBuilder out = new ResponseDefinitionBuilder()
        .withStatus(status)
        .withFixedDelay(original.getFixedDelayMilliseconds())
        .withRandomDelay(original.getDelayDistribution());
    if (original.getHeaders() != null) {
      for (HttpHeader h : original.getHeaders().all()) {
        if (!h.key().equalsIgnoreCase("Content-Type")) out.withHeader(h.key(), h.values().toArray(String[]::new));
      }
    }
    out.withHeader("Content-Type", contentType);
    if (operation != null) out.withHeader(OPERATION_HEADER, operation);
    return out.withBody(body).build();
  }
}
