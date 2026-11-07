package io.mockhub.publictools;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import io.mockhub.api.Jsons;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TotpToolTest {

  private static final String SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";
  private final TotpTool tool = new TotpTool(() -> 1_111_111_111_000L);

  private ResponseDefinition get(String query) {
    return tool.handle(new PublicRequest("GET", "/totp", query, PublicRequest.parseQuery(query), Map.of(), "", 0, "203.0.113.1")).orElseThrow();
  }

  private ResponseDefinition post(String json) {
    return tool.handle(new PublicRequest("POST", "/totp", "", Map.of(), Map.of("Content-Type", "application/json"), json, json.length(), "203.0.113.1")).orElseThrow();
  }

  private static JsonNode json(ResponseDefinition response) throws Exception {
    return Jsons.MAPPER.readTree(response.getBody());
  }

  @Test
  void answersTheCurrentCodeWithItsWindowAndNeighbours() throws Exception {
    // Given
    String query = "secret=" + SECRET + "&digits=8";

    // When
    JsonNode answer = json(get(query));

    // Then
    assertThat(answer.get("code").asText()).isEqualTo("14050471");
    assertThat(answer.get("previous").asText()).isEqualTo("07081804");
    assertThat(answer.get("secondsRemaining").asInt()).isEqualTo(29);
    assertThat(answer.get("validFrom").asText()).isEqualTo("2005-03-18T01:58:30Z");
    assertThat(answer.get("validUntil").asText()).isEqualTo("2005-03-18T01:59:00Z");
    assertThat(answer.get("next").asText()).hasSize(8);
    assertThat(answer.get("algorithm").asText()).isEqualTo("SHA1");
  }

  @Test
  void takesALinkInJsonAPastTimeAndCanAnswerWithJustTheCode() throws Exception {
    // Given
    String link = "otpauth://totp/Demo:ada?secret=" + SECRET + "&issuer=Demo&digits=8";

    // When
    JsonNode answer = json(post("{\"uri\": \"" + link + "\", \"time\": 59}"));
    ResponseDefinition text = get("secret=" + SECRET + "&digits=8&time=59&format=text");

    // Then
    assertThat(answer.get("code").asText()).isEqualTo("94287082");
    assertThat(answer.get("issuer").asText()).isEqualTo("Demo");
    assertThat(answer.get("account").asText()).isEqualTo("ada");
    assertThat(text.getBody()).isEqualTo("94287082");
    assertThat(text.getHeaders().getHeader("Content-Type").firstValue()).startsWith("text/plain");
  }

  @Test
  void explainsBadInputAndIgnoresOtherPaths() throws Exception {
    // Given
    // When
    ResponseDefinition missing = get("");
    ResponseDefinition badTime = get("secret=" + SECRET + "&time=soon");
    ResponseDefinition notJson = post("secret");
    ResponseDefinition put = tool.handle(new PublicRequest("PUT", "/totp", "", Map.of(), Map.of(), "", 0, "203.0.113.1")).orElseThrow();
    boolean other = tool.handle(new PublicRequest("GET", "/totp/x", "", Map.of(), Map.of(), "", 0, "203.0.113.1")).isEmpty();

    // Then
    assertThat(missing.getStatus()).isEqualTo(400);
    assertThat(json(missing).get("error").asText()).isEqualTo("Send a Base32 secret or an otpauth:// link");
    assertThat(json(badTime).get("error").asText()).contains("Unix seconds");
    assertThat(notJson.getStatus()).isEqualTo(400);
    assertThat(put.getStatus()).isEqualTo(405);
    assertThat(other).isTrue();
  }
}
