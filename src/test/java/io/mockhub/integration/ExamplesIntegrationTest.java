package io.mockhub.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Map;
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
    assertThat(api.get(API + "/examples").json()).hasSize(6);
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
  void matchingPatternsExampleShowsEachPattern() {
    // Given
    api.post(API + "/examples/patterns/install", "");
    String base = "/example-patterns";

    // When
    ApiClient.Result notFound = api.post(base + "/shipping/v1/labels", "{\"orderId\":\"ORDER-404\",\"returnUrl\":\"https://shop.example.com/x\"}");
    ApiClient.Result created = api.post(base + "/shipping/v1/labels", "{\"orderId\":\"ORDER-7\",\"returnUrl\":\"https://shop.example.com/x\"}");
    ApiClient.Result refused = api.post(base + "/shipping/v1/labels", "{\"orderId\":\"ORDER-7\",\"returnUrl\":\"https://other.example.net\"}");
    ApiClient.Result gift = api.post(base + "/checkout/v1/quote", "{\"items\":[{\"sku\":\"BOOK\"},{\"sku\":\"GIFT-CARD\"}]}");
    ApiClient.Result product = api.get(base + "/catalog/uat/v1/products/BOOK-42");
    ApiClient.Result realCustomer = api.post(base + "/customers/v1/lookup", "{\"customerId\":\"C1000001\"}");
    ApiClient.Result csv = api.send("GET", base + "/reports/v1/daily", null, null, Map.of("Accept", "text/csv"));

    // Then
    assertThat(notFound.status()).isEqualTo(404);
    assertThat(created.status()).isEqualTo(201);
    assertThat(created.json().get("orderId").asText()).isEqualTo("ORDER-7");
    assertThat(refused.status()).isEqualTo(422);
    assertThat(gift.json().get("shipping").asInt()).isZero();
    assertThat(product.json().get("environment").asText()).isEqualTo("uat");
    assertThat(product.json().get("sku").asText()).isEqualTo("BOOK-42");
    assertThat(realCustomer.json().get("lastName").asText()).isEqualTo("Rivera");
    assertThat(csv.body()).startsWith("date,orders");
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
