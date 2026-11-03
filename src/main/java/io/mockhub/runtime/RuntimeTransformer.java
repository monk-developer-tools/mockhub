package io.mockhub.runtime;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformerV2;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import io.mockhub.project.Project;
import io.mockhub.stub.StubMetadata;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public final class RuntimeTransformer implements ResponseDefinitionTransformerV2 {
  public static final String STUB_RATE_LIMIT = "rateLimit";

  private final Function<String, Optional<Project>> projectById;
  private final RateLimiter limiter;
  private final LoadMeter load;

  public RuntimeTransformer(Function<String, Optional<Project>> projectById, RateLimiter limiter, LoadMeter load) {
    this.projectById = projectById;
    this.limiter = limiter;
    this.load = load;
  }

  @Override
  public String getName() {
    return "mockhub-runtime-delays";
  }

  @Override
  public boolean applyGlobally() {
    return true;
  }

  @Override
  public ResponseDefinition transform(ServeEvent event) {
    ResponseDefinition original = event.getResponseDefinition();
    StubMapping stub = event.getStubMapping();
    if (stub == null) return original;
    RateLimit stubLimit = RateLimit.fromMetadata(StubMetadata.of(stub).get(STUB_RATE_LIMIT));
    if (stubLimit != null) {
      RateLimiter.Decision decision = limiter.take(StubMetadata.projectId(stub) + ":" + stub.getId(), stubLimit);
      if (!decision.allowed()) return RuntimeResponses.rateLimited(decision, stubLimit.describe() + " for this stub", Map.of());
    }
    String projectId = StubMetadata.projectId(stub);
    if (projectId == null || hasOwnDelay(original)) return original;
    Latency latency = projectById.apply(projectId).map(p -> RuntimeSettings.of(p.runtime()).latency()).orElse(null);
    if (latency == null) return original;
    int fixed = latency.fixed() + (latency.load() == null ? 0 : latency.load().extraMs(load.requestsPerSecond(projectId)));
    if (fixed == 0 && latency.distribution() == null) return original;
    ResponseDefinitionBuilder builder = ResponseDefinitionBuilder.like(original);
    if (fixed > 0) builder.withFixedDelay(fixed);
    if (latency.distribution() != null) builder.withRandomDelay(latency.distribution());
    return builder.build();
  }

  static boolean hasOwnDelay(ResponseDefinition response) {
    return response.getFixedDelayMilliseconds() != null || response.getDelayDistribution() != null || response.getChunkedDribbleDelay() != null;
  }
}
