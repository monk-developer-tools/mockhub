package io.mockhub.chaos;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformerV2;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import io.mockhub.stub.StubMetadata;
import java.util.function.DoubleSupplier;

public final class ErrorRateTransformer implements ResponseDefinitionTransformerV2 {
  public static final String HEADER = "X-MockHub-Simulated-Error";

  private final DoubleSupplier random;

  public ErrorRateTransformer(DoubleSupplier random) {
    this.random = random;
  }

  @Override
  public String getName() {
    return "mockhub-error-rate";
  }

  @Override
  public boolean applyGlobally() {
    return true;
  }

  @Override
  public ResponseDefinition transform(ServeEvent event) {
    ResponseDefinition original = event.getResponseDefinition();
    if (event.getStubMapping() == null) return original;
    ErrorRate rate = ErrorRate.of(event.getStubMapping());
    if (rate == null || !rate.hits(random.getAsDouble())) return original;
    ResponseDefinitionBuilder failure = new ResponseDefinitionBuilder()
        .withFixedDelay(original.getFixedDelayMilliseconds())
        .withRandomDelay(original.getDelayDistribution());
    if (rate.fault() != null) return failure.withFault(rate.fault()).build();
    boolean soap = StubMetadata.isSoap(event.getStubMapping());
    String body = rate.body() != null ? rate.body() : soap ? soapFault(rate.status()) : jsonError(rate.status());
    return failure.withStatus(rate.status())
        .withHeader("Content-Type", soap ? "text/xml; charset=utf-8" : body.stripLeading().startsWith("<") ? "application/xml" : "application/json")
        .withHeader(HEADER, "true")
        .withBody(body)
        .build();
  }

  private static String jsonError(int status) {
    return "{\"error\":\"Simulated failure\",\"status\":" + status + "}";
  }

  private static String soapFault(int status) {
    return "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body><soap:Fault>"
        + "<faultcode>soap:Server</faultcode><faultstring>Simulated failure (" + status + ")</faultstring>"
        + "</soap:Fault></soap:Body></soap:Envelope>";
  }
}
