package io.mockhub.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;
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
    assertThat(api.get(API + "/examples").json()).hasSize(10);
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
    ApiClient.Result german = api.send("GET", "/example-templating/greeting?name=Ana", null, null, Map.of("Accept-Language", "de"));
    ApiClient.Result english = api.get("/example-templating/greeting?name=Ana");

    // Then
    assertThat(quote.json().get("tier").asText()).isEqualTo("bulk");
    assertThat(german.body()).isEqualTo("Hallo, Ana!");
    assertThat(english.body()).isEqualTo("Hello, Ana!");
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
  void chaosExampleFailsSomeCallsAndHasAMaintenanceSwitch() {
    // Given
    String id = api.post(API + "/examples/chaos/install", "").json().get("id").asText();
    String maintenance = StreamSupport.stream(api.get(API + "/projects/" + id + "/stubs").json().spliterator(), false)
        .filter(s -> s.get("name").asText().startsWith("Maintenance")).findFirst().orElseThrow().get("id").asText();

    // When
    List<Integer> statuses = IntStream.range(0, 60).mapToObj(i -> api.get("/example-chaos/unreliable").status()).toList();
    int beforeSwitch = api.get("/example-chaos/slow-path-that-does-not-exist").status();
    api.put(API + "/projects/" + id + "/stubs/" + maintenance + "/enabled", "{\"enabled\":true}");

    // Then
    assertThat(statuses).contains(200, 503).containsOnly(200, 503);
    assertThat(beforeSwitch).isEqualTo(404);
    assertThat(api.get("/example-chaos/jittery").status()).isEqualTo(503);
  }

  @Test
  void fakeDataExampleGivesNewDataOnEveryCall() {
    // Given
    api.post(API + "/examples/fake-data/install", "");

    // When
    JsonNode first = api.get("/example-fake-data/customers/C-1001").json();
    JsonNode second = api.get("/example-fake-data/customers/C-1001").json();
    JsonNode list = api.get("/example-fake-data/customers").json();
    JsonNode product = api.get("/example-fake-data/products/random").json();

    // Then
    assertThat(first.get("id").asText()).isEqualTo("C-1001");
    assertThat(first.get("email").asText()).contains("@");
    assertThat(first.at("/address/city").asText()).isNotBlank();
    assertThat(List.of(first.get("name").asText(), first.get("email").asText()))
        .isNotEqualTo(List.of(second.get("name").asText(), second.get("email").asText()));
    assertThat(list).hasSize(5);
    assertThat(list.get(4).get("id").asText()).isEqualTo("C-1005");
    assertThat(product.get("price").isNumber()).isTrue();
    assertThat(api.get("/example-fake-data/orders/A-1").json().get("total").asText()).matches("\\d+\\.\\d{2}");
    assertThat(api.get("/example-fake-data/de/customers/random").json().get("name").asText()).isNotBlank();
    assertThat(api.get("/example-fake-data/orders/A-1").json().get("deliveryDate").asText()).matches("\\d{4}-\\d{2}-\\d{2}");
  }

  @Test
  void crudExampleStoresOrders() {
    // Given
    String id = api.post(API + "/examples/crud/install", "").json().get("id").asText();

    // When
    JsonNode london = api.get("/example-orders/orders?city=London").json();
    ApiClient.Result created = api.post("/example-orders/orders", "{\"customer\":\"Ana Silva\",\"status\":\"PAID\",\"total\":12,\"city\":\"York\"}");
    api.send("PATCH", "/example-orders/orders/4", "{\"status\":\"SHIPPED\"}", "application/json");
    api.delete("/example-orders/orders/1");

    // Then
    assertThat(london).hasSize(2);
    assertThat(created.status()).isEqualTo(201);
    assertThat(created.json().get("id").asInt()).isEqualTo(4);
    assertThat(api.get("/example-orders/orders/4").json().get("status").asText()).isEqualTo("SHIPPED");
    assertThat(api.get("/example-orders/orders/1").status()).isEqualTo(404);
    assertThat(api.get("/example-orders/orders/summary").json().get("currency").asText()).isEqualTo("GBP");
    assertThat(api.get("/example-orders/customers").json()).hasSize(1);
    assertThat(api.get(API + "/projects/" + id + "/data").status()).isEqualTo(200);
  }

  @Test
  void webhooksExampleCallsBackToTheGivenUrl() throws InterruptedException {
    // Given
    String id = api.post(API + "/examples/webhooks/install", "").json().get("id").asText();
    String callback = server.baseUrl() + "/example-webhooks/partner/callbacks";

    // When
    ApiClient.Result accepted = api.post("/example-webhooks/payments", "{\"reference\":\"ORDER-7\",\"amount\":25,\"callbackUrl\":\"" + callback + "\"}");
    ApiClient.Result declined = api.post("/example-webhooks/payments", "{\"reference\":\"ORDER-8\",\"amount\":5000,\"callbackUrl\":\"" + callback + "\"}");
    ApiClient.Result missing = api.post("/example-webhooks/payments", "{\"reference\":\"ORDER-9\",\"amount\":5}");
    List<String> callbacks = waitForCallbacks(id, 2);

    // Then
    assertThat(accepted.status()).isEqualTo(202);
    assertThat(accepted.json().get("status").asText()).isEqualTo("PENDING");
    assertThat(declined.status()).isEqualTo(202);
    assertThat(missing.status()).isEqualTo(400);
    assertThat(callbacks).anySatisfy(b -> assertThat(b).contains("ORDER-7").contains("SETTLED"));
    assertThat(callbacks).anySatisfy(b -> assertThat(b).contains("ORDER-8").contains("DECLINED"));
  }

  private List<String> waitForCallbacks(String projectId, int expected) throws InterruptedException {
    Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
    List<String> bodies = List.of();
    while (Instant.now().isBefore(deadline)) {
      bodies = StreamSupport.stream(api.get(API + "/projects/" + projectId + "/requests").json().spliterator(), false)
          .filter(e -> e.at("/request/url").asText().endsWith("/partner/callbacks"))
          .map(e -> e.at("/request/body").asText())
          .toList();
      if (bodies.size() >= expected) return bodies;
      Thread.sleep(200);
    }
    return bodies;
  }

  @Test
  void bookshopExampleImportsOpenApiAndServesFiles() {
    // Given
    api.post(API + "/examples/bookshop/install", "");

    // When
    JsonNode books = api.get("/example-bookshop/books").json();
    ApiClient.Result one = api.get("/example-bookshop/books/978-0-00-000002-8");
    ApiClient.Result csv = api.get("/example-bookshop/price-list.csv");

    // Then
    assertThat(books).hasSize(2);
    assertThat(one.status()).isEqualTo(200);
    assertThat(api.get("/example-bookshop/books/000").status()).isEqualTo(404);
    assertThat(csv.body()).startsWith("isbn,title,price");
    assertThat(csv.contentType()).startsWith("text/csv");
    assertThat(api.get("/example-bookshop/covers/123").body()).contains("<svg");
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
