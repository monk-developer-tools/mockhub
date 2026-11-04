package io.mockhub.template;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformerV2;
import com.github.tomakehurst.wiremock.http.HttpHeader;
import com.github.tomakehurst.wiremock.http.HttpHeaders;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import io.mockhub.api.Jsons;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleSupplier;

public final class WeightedTransformer implements ResponseDefinitionTransformerV2 {
  public static final String NAME = "mockhub-weighted";
  public static final String HEADER = "X-MockHub-Weighted-Choice";

  private final DoubleSupplier random;

  public WeightedTransformer(DoubleSupplier random) {
    this.random = random;
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
    List<WeightedResponse> choices = WeightedResponse.from(parameters(original));
    if (choices.isEmpty()) return original;
    int index = WeightedResponse.pick(choices, random.getAsDouble());
    WeightedResponse choice = choices.get(index);
    ResponseDefinitionBuilder builder = ResponseDefinitionBuilder.like(original).but().withStatus(choice.status());
    if (choice.body() != null) builder.withBody(choice.body());
    List<HttpHeader> headers = new ArrayList<>();
    if (original.getHeaders() != null) original.getHeaders().all().forEach(headers::add);
    choice.headers().forEach((name, value) -> {
      headers.removeIf(h -> h.keyEquals(name));
      headers.add(new HttpHeader(name, value));
    });
    headers.add(new HttpHeader(HEADER, String.valueOf(index + 1)));
    return builder.withHeaders(new HttpHeaders(headers)).build();
  }

  private static JsonNode parameters(ResponseDefinition definition) {
    Parameters params = definition.getTransformerParameters();
    return params == null ? Jsons.object() : Jsons.MAPPER.valueToTree(params);
  }
}
