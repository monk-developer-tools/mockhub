package io.mockhub.callout;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformerV2;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import io.mockhub.api.Jsons;
import io.mockhub.playground.Header;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class SyncCalloutTransformer implements ResponseDefinitionTransformerV2 {
  public static final String NAME = "mockhub-sync-callout";
  public static final String PARAMETER = "callout";
  public static final String ERROR_HEADER = "X-MockHub-Callout-Error";
  private static final Set<String> NOT_RETURNED = Set.of("content-length", "transfer-encoding", "connection", "content-encoding", "keep-alive");

  private final CalloutService callouts;

  public SyncCalloutTransformer(CalloutService callouts) {
    this.callouts = callouts;
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
    Object raw = original.getTransformerParameters() == null ? null : original.getTransformerParameters().get(PARAMETER);
    ResponseDefinitionBuilder out = new ResponseDefinitionBuilder()
        .withFixedDelay(original.getFixedDelayMilliseconds())
        .withRandomDelay(original.getDelayDistribution());
    if (!(raw instanceof Map<?, ?> parameters)) return failure(out, "This stub waits for a callout but has no transformerParameters.callout");
    @SuppressWarnings("unchecked")
    CalloutService.Outcome outcome = callouts.call(CalloutSpec.from((Map<String, ?>) parameters), event, CalloutService.SYNC);
    CalloutEntry entry = outcome.entry();
    if (entry.response() == null) return failure(out, entry.error());
    out.withStatus(entry.response().status());
    for (Header h : entry.response().headers()) {
      if (!NOT_RETURNED.contains(h.name().toLowerCase(Locale.ROOT))) out.withHeader(h.name(), h.value());
    }
    return out.withBody(outcome.body()).build();
  }

  private static ResponseDefinition failure(ResponseDefinitionBuilder out, String message) {
    return out.withStatus(502)
        .withHeader("Content-Type", "application/json")
        .withHeader(ERROR_HEADER, "true")
        .withBody(Jsons.write(Jsons.object().put("error", "Callout failed: " + message)))
        .build();
  }
}
