package io.mockhub.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.mockhub.support.GrpcTestClient;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Base64;
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
    assertThat(api.get(API + "/examples").json()).hasSize(18);
  }

  @Test
  void vendorSandboxesAnswerWithThePublishedSpecsOwnExamples() {
    // When
    List<Integer> installed = List.of("sandbox-github", "sandbox-stripe", "sandbox-twilio").stream()
        .map(id -> api.post(API + "/examples/" + id + "/install", "").status()).toList();
    JsonNode repo = api.get("/sandbox-github/repos/octocat/Hello-World").json();
    JsonNode user = api.get("/sandbox-github/users/octocat").json();
    ApiClient.Result issue = api.post("/sandbox-github/repos/octocat/Hello-World/issues", "{\"title\": \"Found a bug\"}");
    JsonNode customer = api.get("/sandbox-stripe/v1/customers/cus_123").json();
    ApiClient.Result deleted = api.send("DELETE", "/sandbox-stripe/v1/customers/cus_123", null, null, Map.of());
    ApiClient.Result message = api.send("POST", "/sandbox-twilio/2010-04-01/Accounts/AC123/Messages.json", "To=%2B15558675310&Body=Hello",
        "application/x-www-form-urlencoded", Map.of());

    // Then
    assertThat(installed).containsOnly(201);
    assertThat(repo.get("full_name").asText()).isEqualTo("octocat/Hello-World");
    assertThat(repo.has("$ref")).isFalse();
    assertThat(user.get("login").asText()).isEqualTo("octocat");
    assertThat(issue.status()).isEqualTo(201);
    assertThat(issue.json().has("number")).isTrue();
    assertThat(customer.get("object").asText()).isEqualTo("customer");
    assertThat(deleted.json().get("deleted").asBoolean()).isTrue();
    assertThat(message.status()).isEqualTo(201);
    assertThat(message.json().get("account_sid").asText()).startsWith("AC");
  }

  @Test
  void upsAndAmadeusSandboxesAnswerEveryOperationFromTheirPublishedSpecs() {
    // When
    List<Integer> installed = List.of("sandbox-ups", "sandbox-amadeus").stream()
        .map(id -> api.post(API + "/examples/" + id + "/install", "").status()).toList();
    ApiClient.Result track = api.get("/sandbox-ups/track/v1/details/1Z12345E0205271688");
    ApiClient.Result rate = api.send("POST", "/sandbox-ups/rating/v2409/Rate", "{\"RateRequest\":{}}", "application/json", Map.of());
    ApiClient.Result address = api.send("POST", "/sandbox-ups/addressvalidation/v2/1", "{\"XAVRequest\":{}}", "application/json", Map.of());
    ApiClient.Result transit = api.send("POST", "/sandbox-ups/shipments/v1/transittimes", "{}", "application/json", Map.of());
    ApiClient.Result cities = api.get("/sandbox-amadeus/v1/reference-data/locations?subType=CITY&keyword=MUC");
    ApiClient.Result offers = api.get("/sandbox-amadeus/v2/shopping/flight-offers?originLocationCode=SYD&destinationLocationCode=BKK&departureDate=2026-12-01&adults=1");
    ApiClient.Result priced = api.send("POST", "/sandbox-amadeus/v1/shopping/flight-offers/pricing", "{}", "application/json", Map.of());
    ApiClient.Result order = api.send("POST", "/sandbox-amadeus/v1/booking/flight-orders", "{}", "application/json", Map.of());
    ApiClient.Result cancelled = api.send("DELETE", "/sandbox-amadeus/v1/booking/flight-orders/eJzTd9f3NjIJdzUGAAp", null, null, Map.of());

    // Then
    assertThat(installed).containsOnly(201);
    for (ApiClient.Result r : List.of(track, rate, address, transit)) {
      assertThat(r.status()).as(r.body()).isEqualTo(200);
      assertThat(r.body()).doesNotContain(": null").doesNotContain("\"$ref\"");
    }
    assertThat(track.json().at("/trackResponse/shipment/0/package/0").isObject()).isTrue();
    assertThat(rate.json().has("RateResponse")).isTrue();
    assertThat(cities.json().at("/data/0/type").asText()).isEqualTo("location");
    assertThat(offers.json().at("/data/0/type").asText()).isEqualTo("flight-offer");
    assertThat(priced.json().at("/data/type").asText()).isEqualTo("flight-offers-pricing");
    assertThat(order.status()).isEqualTo(201);
    assertThat(order.json().at("/data/type").asText()).isEqualTo("flight-order");
    assertThat(cancelled.status()).isEqualTo(204);
  }

  @Test
  void graphqlExampleAnswersFromTheSchemaAndFixedOperations() {
    // When
    ApiClient.Result installed = api.post(API + "/examples/graphql-library/install", "");
    ApiClient.Result books = api.post("/example-graphql/graphql", "{\"query\":\"{ books(first: 2) { title genre author { name } } }\"}");
    ApiClient.Result missing = api.post("/example-graphql/graphql",
        "{\"operationName\":\"Book\",\"query\":\"query Book($id: ID!) { book(id: $id) { title } }\",\"variables\":{\"id\":\"404\"}}");
    ApiClient.Result loan = api.post("/example-graphql/graphql",
        "{\"operationName\":\"Borrow\",\"query\":\"mutation Borrow($input: LoanInput!) { borrow(input: $input) { borrower dueAt } }\",\"variables\":{\"input\":{\"bookId\":\"1\",\"borrower\":\"Ada\"}}}");

    // Then
    assertThat(installed.status()).as(installed.body()).isEqualTo(201);
    assertThat(books.json().at("/data/books")).hasSize(2);
    assertThat(books.json().at("/data/books/0/genre").asText()).isIn("FICTION", "SCIENCE", "HISTORY", "CHILDREN");
    assertThat(missing.json().at("/errors/0/extensions/code").asText()).isEqualTo("NOT_FOUND");
    assertThat(loan.json().at("/data/borrow/borrower").asText()).isEqualTo("Ada");
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
    assertThat(api.get("/example-fake-data/orders/A-1").body()).containsPattern("\"total\": \\d+\\.\\d{2},");
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

  @Test
  void webhooksExampleForwardsWaitsForPartnersAndCatchesEverythingElse() throws InterruptedException {
    // Given
    String id = api.post(API + "/examples/webhooks/install", "").json().get("id").asText();

    // When
    ApiClient.Result check = api.post("/example-webhooks/cards/check", "{\"card\":\"4242\"}");
    ApiClient.Result refund = api.post("/example-webhooks/refunds", "{\"reference\":\"ORDER-11\",\"amount\":25}");
    ApiClient.Result caught = api.post("/example-webhooks/anything/at/all", "{\"event\":\"ping\"}");
    List<String> forwarded = waitForCallbacks(id, 1);

    // Then
    assertThat(check.status()).isEqualTo(201);
    assertThat(check.header("X-Issuer")).isEqualTo("Demo Bank");
    assertThat(check.json().get("pan").asText()).isEqualTo("4242");
    assertThat(refund.status()).isEqualTo(202);
    assertThat(caught.status()).isEqualTo(200);
    assertThat(caught.json().get("received").asBoolean()).isTrue();
    assertThat(forwarded).anySatisfy(b -> assertThat(b).contains("ORDER-11"));
    assertThat(api.get(API + "/projects/" + id + "/callouts").json())
        .extracting(e -> e.get("mode").asText())
        .contains("sync", "async");
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

  @Test
  void grpcExampleAnswersOverGrpcFromItsDescriptorSet() {
    // Given
    ApiClient.Result installed = api.post(API + "/examples/grpc-inventory/install", "");
    String service = "inventory.v1.StockService";

    // When
    try (GrpcTestClient client = new GrpcTestClient(server.port(), exampleDescriptor())) {
      GrpcTestClient.Reply item = client.unary(service, "GetItem", "{\"sku\":\"sku-7\"}");
      GrpcTestClient.Reply missing = client.unary(service, "GetItem", "{\"sku\":\"sku-404\"}");
      GrpcTestClient.Reply blocked = client.unary(service, "GetItem", "{\"sku\":\"sku-7\"}", Map.of("x-tenant", "blocked"));
      List<String> updates = client.serverStream(service, "WatchStock", "{\"warehouse\":\"north\"}");
      GrpcTestClient.Reply reserved = client.clientStream(service, "Reserve", List.of("{\"sku\":\"sku-1\",\"quantity\":2}"));
      GrpcTestClient.Reply empty = client.clientStream(service, "Reserve", List.of("{\"sku\":\"sku-1\"}"));

      // Then
      assertThat(installed.status()).as(installed.body()).isEqualTo(201);
      assertThat(item.status()).as(item.description()).isEqualTo("OK");
      assertThat(item.json()).contains("\"sku\": \"sku-7\"").contains("\"name\": \"Item sku-7\"").contains("IN_STOCK");
      assertThat(missing.status()).isEqualTo("NOT_FOUND");
      assertThat(blocked.status()).isEqualTo("PERMISSION_DENIED");
      assertThat(updates).singleElement().asString().contains("LOW_STOCK");
      assertThat(reserved.status()).as(reserved.description()).isEqualTo("OK");
      assertThat(reserved.json()).contains("\"reserved\": 2");
      assertThat(empty.status()).isEqualTo("INVALID_ARGUMENT");
    }
  }

  @Test
  void grpcExampleCanBeInstalledOnlyOnceBecauseAServiceBelongsToOneProject() {
    // Given
    api.post(API + "/examples/grpc-inventory/install", "");
    int before = api.get(API + "/projects").json().size();

    // When
    ApiClient.Result second = api.post(API + "/examples/grpc-inventory/install", "");

    // Then
    assertThat(second.status()).isEqualTo(409);
    assertThat(second.body()).contains("inventory.v1.StockService");
    assertThat(api.get(API + "/projects").json().size()).isEqualTo(before);
  }

  private static byte[] exampleDescriptor() {
    try (InputStream in = ExamplesIntegrationTest.class.getResourceAsStream("/examples/inventory.dsc.b64")) {
      return Base64.getMimeDecoder().decode(in.readAllBytes());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Test
  void echoExampleAnswersWithTheRequestAtSlashEcho() {
    // Given
    ApiClient.Result installed = api.post(API + "/examples/echo/install", "");

    // When
    ApiClient.Result r = api.send("DELETE", "/echo/carts/3?force=true", null, null, Map.of("X-Trace", "t1"));

    // Then
    assertThat(installed.json().get("slug").asText()).isEqualTo("echo");
    assertThat(r.status()).isEqualTo(200);
    assertThat(r.json().get("method").asText()).isEqualTo("DELETE");
    assertThat(r.json().get("path").asText()).isEqualTo("/carts/3");
    assertThat(r.json().at("/query/force").asText()).isEqualTo("true");
    assertThat(r.json().at("/headers/X-Trace").asText()).isEqualTo("t1");
    assertThat(r.header("Cache-Control")).isEqualTo("no-store");
  }
}
