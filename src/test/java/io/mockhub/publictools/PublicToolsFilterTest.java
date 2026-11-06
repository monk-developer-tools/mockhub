package io.mockhub.publictools;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.extension.requestfilter.RequestFilterAction;
import com.github.tomakehurst.wiremock.extension.requestfilter.StopAction;
import com.github.tomakehurst.wiremock.http.ImmutableRequest;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import io.mockhub.api.Jsons;
import io.mockhub.fake.DataGenerator;
import io.mockhub.fake.FakeData;
import io.mockhub.runtime.RateLimiter;
import io.mockhub.support.MutableClock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PublicToolsFilterTest {

  private final MutableClock clock = new MutableClock(Instant.parse("2026-11-04T10:00:00Z"));
  private final PublicLimits limits = new PublicLimits(new RateLimiter(clock));
  @TempDir
  static Path data;
  private Bins bins;
  private List<PublicTool> tools;

  @BeforeEach
  void setUp() {
    bins = new Bins(data.resolve(String.valueOf(System.nanoTime())), clock);
    BinTool binTool = new BinTool(bins, limits, new BinResponder(null, null, null, null, null, bound -> 0, System::currentTimeMillis),
        new BinDrafter(() -> false, (system, user) -> ""), name -> Optional.empty(), header -> Optional.empty(), System::currentTimeMillis);
    tools = List.of(new EchoTool(), binTool, new FakeTool(new DataGenerator(new FakeData("en")), limits));
  }

  private static Request request(String method, String path, String body) {
    ImmutableRequest.Builder b = ImmutableRequest.create()
        .withAbsoluteUrl("http://localhost" + path)
        .withMethod(RequestMethod.fromString(method))
        .withClientIp("198.51.100.7")
        .withBody(body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8));
    if (body != null) b.withHeader("Content-Type", "application/json");
    return b.build();
  }

  private static ResponseDefinition stopped(RequestFilterAction action) {
    assertThat(action).isInstanceOf(StopAction.class);
    return ((StopAction) action).getResponseDefinition();
  }

  private static JsonNode json(ResponseDefinition response) {
    try {
      return Jsons.MAPPER.readTree(response.getBody());
    } catch (Exception e) {
      throw new AssertionError(e);
    }
  }

  @Test
  void letsEveryRequestThroughWhenTurnedOff() {
    // Given
    PublicToolsFilter filter = new PublicToolsFilter(false, limits, tools);

    // When
    RequestFilterAction action = filter.filter(request("GET", "/__public/echo", null), null);

    // Then
    assertThat(action).isNotInstanceOf(StopAction.class);
  }

  @Test
  void leavesOtherPathsToTheMocks() {
    // Given
    PublicToolsFilter filter = new PublicToolsFilter(true, limits, tools);

    // When
    RequestFilterAction action = filter.filter(request("GET", "/__publicity/echo", null), null);

    // Then
    assertThat(action).isNotInstanceOf(StopAction.class);
  }

  @Test
  void echoesTheRequest() {
    // Given
    PublicToolsFilter filter = new PublicToolsFilter(true, limits, tools);

    // When
    ResponseDefinition response = stopped(filter.filter(request("POST", "/__public/echo/orders?id=7", "{\"a\":1}"), null));

    // Then
    JsonNode body = json(response);
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(body.get("method").asText()).isEqualTo("POST");
    assertThat(body.get("path").asText()).isEqualTo("/orders");
    assertThat(body.at("/query/id").asText()).isEqualTo("7");
    assertThat(body.at("/json/a").asInt()).isEqualTo(1);
    assertThat(response.getHeaders().getHeader("Access-Control-Allow-Origin").firstValue()).isEqualTo("*");
  }

  @Test
  void answersUnknownToolsWithNotFound() {
    // Given
    PublicToolsFilter filter = new PublicToolsFilter(true, limits, tools);

    // When
    ResponseDefinition response = stopped(filter.filter(request("GET", "/__public/nothing", null), null));

    // Then
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  void limitsHowOftenOneAddressCalls() {
    // Given
    PublicToolsFilter filter = new PublicToolsFilter(true, limits, tools);
    for (int i = 0; i < 120; i++) filter.filter(request("GET", "/__public/headers", null), null);

    // When
    ResponseDefinition response = stopped(filter.filter(request("GET", "/__public/headers", null), null));

    // Then
    assertThat(response.getStatus()).isEqualTo(429);
    assertThat(response.getHeaders().getHeader("Retry-After").isPresent()).isTrue();
  }

  @Test
  void limitsNewBinsPerAddress() {
    // Given
    PublicToolsFilter filter = new PublicToolsFilter(true, limits, tools);
    for (int i = 0; i < 10; i++) assertThat(stopped(filter.filter(request("POST", "/__public/bins", ""), null)).getStatus()).isEqualTo(201);

    // When
    ResponseDefinition response = stopped(filter.filter(request("POST", "/__public/bins", ""), null));

    // Then
    assertThat(response.getStatus()).isEqualTo(429);
  }

  @Test
  void generatesFakeRecordsWithinLimits() {
    // Given
    PublicToolsFilter filter = new PublicToolsFilter(true, limits, tools);
    String fields = "\"fields\":[{\"name\":\"id\",\"expression\":\"sequence\"},{\"name\":\"name\",\"expression\":\"Name.firstName\"}]";

    // When
    ResponseDefinition ok = stopped(filter.filter(request("POST", "/__public/fake", "{\"count\":3," + fields + "}"), null));
    ResponseDefinition tooMany = stopped(filter.filter(request("POST", "/__public/fake", "{\"count\":101," + fields + "}"), null));
    ResponseDefinition tooBig = stopped(filter.filter(request("POST", "/__public/fake",
        "{\"count\":1,\"fields\":[{\"name\":\"t\",\"expression\":\"Lorem.characters '5000000'\"}]}"), null));

    // Then
    assertThat(json(ok).get("records")).hasSize(3);
    assertThat(tooMany.getStatus()).isEqualTo(400);
    assertThat(tooBig.getStatus()).isEqualTo(400);
    assertThat(json(tooBig).get("error").asText()).contains("up to 1000");
  }

  @Test
  void aBinOwnerWatchingTheirBinIsNotHeldToTheGeneralLimit() {
    // Given
    PublicToolsFilter filter = new PublicToolsFilter(true, limits, tools);
    String id = json(stopped(filter.filter(request("POST", "/__public/bins", ""), null))).get("id").asText();

    // When
    int refused = 0;
    for (int i = 0; i < 300; i++) {
      if (stopped(filter.filter(request("GET", "/__public/inspect/" + id + "?after=0", null), null)).getStatus() == 429) refused++;
    }

    // Then
    assertThat(refused).isZero();
    assertThat(stopped(filter.filter(request("GET", "/__public/echo", null), null)).getStatus()).isEqualTo(200);
  }

  @Test
  void webhooksSentToABinNeitherHitNorUseUpTheGeneralLimit() {
    // Given
    PublicToolsFilter filter = new PublicToolsFilter(true, limits, tools);
    String id = json(stopped(filter.filter(request("POST", "/__public/bins", ""), null))).get("id").asText();

    // When
    int refused = 0;
    for (int i = 0; i < 300; i++) {
      if (stopped(filter.filter(request("POST", "/__public/bins/" + id + "/hook", "{}"), null)).getStatus() == 429) refused++;
    }

    // Then
    assertThat(refused).isZero();
    assertThat(stopped(filter.filter(request("GET", "/__public/echo", null), null)).getStatus()).isEqualTo(200);
  }
}
