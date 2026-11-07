package io.mockhub.publictools;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.mockhub.api.Jsons;
import io.mockhub.fake.FakeData;
import io.mockhub.fake.FakeHelpers;
import io.mockhub.template.JwtHelpers;
import io.mockhub.template.JwtKeys;
import io.mockhub.template.ValidationHelpers;
import io.mockhub.template.PortableHelpers;
import io.mockhub.template.ValueHelpers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BinCatalogTest {
  private static final String JSON = "{\"id\":\"o1\",\"type\":\"gold\",\"items\":[{\"id\":1}],\"customer\":{\"n\":1},\"name\":\"ann\",\"email\":\"a@b.co\","
      + "\"amount\":5,\"age\":30,\"card\":\"4242424242\",\"text\":\"hello world\",\"code\":\"ABC\",\"username\":\"u\",\"password\":\"p\"}";

  private final ExecutorService workers = Executors.newFixedThreadPool(2);

  @TempDir
  Path keys;

  @AfterEach
  void stop() {
    workers.shutdownNow();
  }

  @Test
  void everyValueTheBinOffersRendersInABin() throws Exception {
    // Given
    TemplatePlayground templates = new TemplatePlayground(Stream.concat(Stream.of(new FakeHelpers(new FakeData("en")), new ValueHelpers(), new ValidationHelpers(),
        new JwtHelpers(new JwtKeys(keys), Clock.systemUTC())), PortableHelpers.providers().stream()).toList(), workers, 2000);
    JsonNode catalog = Jsons.MAPPER.readTree(Files.readString(Path.of("ui/src/model/dynamicValues.json")));
    BinState state = new BinState();

    // When
    List<String> broken = new ArrayList<>();
    int offered = 0;
    for (JsonNode group : catalog.get("groups")) {
      if (group.path("scope").asText("").equals("callout") || group.path("bin").asBoolean(true) == false) continue;
      for (JsonNode item : group.get("items")) {
        JsonNode bin = item.get("bin");
        if (bin != null && bin.isBoolean() && !bin.asBoolean()) continue;
        String expression = (bin != null && bin.isTextual() ? bin.asText() : item.get("expression").asText()).replace("$R.", "request.");
        TemplatePlayground.Result result = templates.render(expression, sample(item.path("input").asText("")), state);
        offered++;
        if (result.error() != null && result.status() == null) broken.add(item.get("label").asText() + ": " + result.error());
      }
    }

    // Then
    assertThat(broken).isEmpty();
    assertThat(offered).isGreaterThan(90);
    assertThat(state.list("orders")).containsExactly("o1");
  }

  private static TemplatePlayground.Sample sample(String input) {
    String body = switch (input) {
      case "xml" -> "<order><id>7</id></order>";
      case "soap" -> "<Envelope><Body><GetOrder><id>7</id></GetOrder></Body></Envelope>";
      case "form" -> "email=a%40b.co&status=paid";
      default -> JSON;
    };
    String type = switch (input) {
      case "xml", "soap" -> "application/xml";
      case "form" -> "application/x-www-form-urlencoded";
      default -> "application/json";
    };
    return new TemplatePlayground.Sample("POST", "/orders/42?page=2&code=abc&q=x",
        Map.of("Content-Type", type, "X-Request-Id", "rid-1", "Cookie", "session=s1", "X-Region", "EU"), body).withPathParams(Map.of("id", "42"));
  }
}
