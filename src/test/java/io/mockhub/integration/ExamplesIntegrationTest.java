package io.mockhub.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExamplesIntegrationTest {

  private static final String API = "/__admin/mockhub/api";

  @TempDir
  Path data;
  private MockHubServer server;
  private ApiClient api;

  @BeforeEach
  void start() {
    server = MockHubServer.start(data);
    api = server.api();
  }

  @AfterEach
  void stop() {
    server.close();
  }

  @Test
  void listsExamples() {
    assertThat(api.get(API + "/examples").json()).hasSize(5);
  }

  @Test
  void restBasicsExampleWorks() {
    // When
    ApiClient.Result installed = api.post(API + "/examples/rest-basics/install", "");

    // Then
    assertThat(installed.status()).isEqualTo(201);
    assertThat(api.get("/example-petstore/pets").json()).hasSize(2);
    assertThat(api.get("/example-petstore/pets?status=sold").json()).hasSize(1);
    assertThat(api.get("/example-petstore/pets/7").json().get("id").asText()).isEqualTo("7");
    assertThat(api.get("/example-petstore/pets/999").status()).isEqualTo(404);
    assertThat(api.post("/example-petstore/pets", "{\"name\":\"Rex\",\"type\":\"dog\"}").status()).isEqualTo(201);
    assertThat(api.post("/example-petstore/pets", "{\"name\":\"Rex\",\"type\":\"lion\"}").status()).isEqualTo(400);
    assertThat(api.delete("/example-petstore/pets/1").status()).isEqualTo(401);
  }

  @Test
  void templatingExampleWorks() {
    // Given
    api.post(API + "/examples/templating/install", "");

    // When
    ApiClient.Result quote = api.post("/example-templating/quotes", "{\"sku\":\"A\",\"quantity\":12,\"tags\":[\"red\",\"xl\"]}");

    // Then
    assertThat(quote.json().get("tier").asText()).isEqualTo("bulk");
    assertThat(quote.json().get("items")).hasSize(2);
    assertThat(api.get("/example-templating/users/random").json().get("id").asText()).hasSize(36);
    assertThat(api.get("/example-templating/echo/hi?q=x").json().get("query").asText()).isEqualTo("x");
  }

  @Test
  void statefulCartExampleWalksThroughItsStates() {
    // Given
    api.post(API + "/examples/stateful-cart/install", "");

    // When / Then
    assertThat(api.get("/example-cart/cart").json().get("total").asInt()).isZero();
    assertThat(api.post("/example-cart/cart/items", "{}").status()).isEqualTo(201);
    assertThat(api.get("/example-cart/cart").json().get("total").asDouble()).isEqualTo(12.5);
    assertThat(api.post("/example-cart/cart/checkout", "{}").status()).isEqualTo(200);
    assertThat(api.get("/example-cart/cart").status()).isEqualTo(410);
  }

  @Test
  void chaosExampleFlakyEndpointRecovers() {
    // Given
    api.post(API + "/examples/chaos/install", "");

    // When / Then
    assertThat(api.get("/example-chaos/flaky").status()).isEqualTo(503);
    assertThat(api.get("/example-chaos/flaky").status()).isEqualTo(200);
    assertThat(api.get("/example-chaos/rate-limited").status()).isEqualTo(429);
  }

  @Test
  void soapExampleMatchesOnXPathValues() {
    // Given
    api.post(API + "/examples/soap-calculator/install", "");
    String add = "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\"><s:Body><c:Add xmlns:c=\"http://example.com/calculator\"><c:a>%s</c:a><c:b>3</c:b></c:Add></s:Body></s:Envelope>";

    // When / Then
    assertThat(api.postXml("/example-soap/ws/calculator", add.formatted("2")).body()).contains("<ns1:result>5</ns1:result>");
    assertThat(api.postXml("/example-soap/ws/calculator", add.formatted("-1")).body()).contains("Negative numbers");
    assertThat(api.postXml("/example-soap/ws/calculator", add.formatted("9")).body()).contains("<ns1:result>1</ns1:result>");
  }
}
