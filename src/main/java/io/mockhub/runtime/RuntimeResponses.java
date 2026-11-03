package io.mockhub.runtime;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import io.mockhub.api.Jsons;
import java.util.Map;

final class RuntimeResponses {
  private RuntimeResponses() {}

  static ResponseDefinition rateLimited(RateLimiter.Decision decision, String what, Map<String, String> cors) {
    ResponseDefinitionBuilder builder = new ResponseDefinitionBuilder().withStatus(429)
        .withHeader("Content-Type", "application/json")
        .withHeader("Retry-After", String.valueOf(decision.resetSeconds()))
        .withHeader("X-RateLimit-Limit", String.valueOf(decision.limit()))
        .withHeader("X-RateLimit-Remaining", "0")
        .withHeader("X-RateLimit-Reset", String.valueOf(decision.resetSeconds()))
        .withBody(Jsons.write(Jsons.object()
            .put("error", "Rate limit exceeded: " + what + ". Try again in " + decision.resetSeconds() + " s.")
            .put("retryAfter", decision.resetSeconds())));
    cors.forEach(builder::withHeader);
    return builder.build();
  }

  static ResponseDefinition forbiddenIp(String ip, Map<String, String> cors) {
    ResponseDefinitionBuilder builder = new ResponseDefinitionBuilder().withStatus(403)
        .withHeader("Content-Type", "application/json")
        .withBody(Jsons.write(Jsons.object().put("error", "Calls from " + ip + " aren't allowed for this mock").put("ip", ip)));
    cors.forEach(builder::withHeader);
    return builder.build();
  }

  static ResponseDefinition preflight(Map<String, String> headers) {
    ResponseDefinitionBuilder builder = new ResponseDefinitionBuilder().withStatus(204);
    headers.forEach(builder::withHeader);
    return builder.build();
  }
}
