package io.mockhub.publictools;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import io.mockhub.api.Jsons;

final class PublicResponses {
  static final String ALLOW_HEADERS = "Content-Type, Authorization, X-Bin-Token";

  private PublicResponses() {}

  static ResponseDefinitionBuilder base(int status) {
    return new ResponseDefinitionBuilder().withStatus(status)
        .withHeader("Access-Control-Allow-Origin", "*")
        .withHeader("Access-Control-Expose-Headers", "Retry-After, X-MockHub-Bin")
        .withHeader("Cache-Control", "no-store")
        .withHeader("X-Robots-Tag", "noindex");
  }

  static ResponseDefinition json(int status, JsonNode body) {
    return base(status).withHeader("Content-Type", "application/json").withBody(Jsons.write(body)).build();
  }

  static ResponseDefinition error(int status, String message) {
    return json(status, Jsons.object().put("error", message).put("status", status));
  }

  static ResponseDefinition preflight() {
    return base(204)
        .withHeader("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, HEAD, OPTIONS")
        .withHeader("Access-Control-Allow-Headers", ALLOW_HEADERS)
        .withHeader("Access-Control-Max-Age", "1800")
        .build();
  }

  static ResponseDefinition tooMany(long retryAfterSeconds, String what) {
    return refused(retryAfterSeconds, "Too many " + what + " from your address. Try again in " + inTime(retryAfterSeconds) + ".");
  }

  static ResponseDefinition refused(long retryAfterSeconds, String message) {
    return base(429).withHeader("Content-Type", "application/json").withHeader("Retry-After", String.valueOf(retryAfterSeconds))
        .withBody(Jsons.write(Jsons.object().put("error", message).put("status", 429).put("retryAfter", retryAfterSeconds)))
        .build();
  }

  static String inTime(long seconds) {
    if (seconds < 90) return seconds + (seconds == 1 ? " second" : " seconds");
    long minutes = (seconds + 59) / 60;
    return minutes + " minutes";
  }
}
