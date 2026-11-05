package io.mockhub.contract;

import static org.assertj.core.api.Assertions.assertThat;

import io.mockhub.history.HistoryEntry;
import io.mockhub.playground.Header;
import java.util.List;
import org.junit.jupiter.api.Test;

class ContractCheckerTest {

  static final String ORDERS = """
      openapi: 3.0.3
      info: { title: Orders, version: "1.2" }
      servers: [ { url: https://api.example.com/v1 } ]
      components:
        securitySchemes:
          key: { type: apiKey, in: header, name: X-Api-Key }
        schemas:
          Order:
            type: object
            required: [id, amount]
            properties:
              id: { type: integer }
              amount: { type: number, minimum: 0 }
              note: { type: string, nullable: true, example: hi }
              lines: { type: array, items: { $ref: '#/components/schemas/Line' } }
          Line:
            type: object
            required: [sku]
            properties:
              sku: { type: string }
      paths:
        /orders:
          get:
            parameters:
              - { name: limit, in: query, schema: { type: integer, maximum: 100 } }
              - { name: X-Tenant, in: header, required: true, schema: { type: string } }
            responses:
              '200':
                description: ok
                content:
                  application/json:
                    schema: { type: array, items: { $ref: '#/components/schemas/Order' } }
          post:
            requestBody:
              required: true
              content:
                application/json:
                  schema: { $ref: '#/components/schemas/Order' }
            responses:
              '201': { description: created, content: { application/json: { schema: { $ref: '#/components/schemas/Order' } } } }
              4XX: { description: bad }
        /orders/{id}:
          parameters:
            - { name: id, in: path, required: true, schema: { type: integer } }
          get:
            responses:
              '200': { description: ok, content: { application/json: { schema: { $ref: '#/components/schemas/Order' } } } }
              '204': { description: empty }
        /orders/latest:
          get:
            responses:
              '200': { description: ok }
      """;

  private final ContractChecker checker = new ContractChecker();
  private final ApiContract contract = ApiContract.of(ContractDocument.parse(ORDERS));

  private static HistoryEntry call(String method, String url, List<Header> headers, String body, int status, List<Header> responseHeaders, String responseBody) {
    return new HistoryEntry("e1", 1L, "p1", method, url, "http://x" + url, "127.0.0.1", headers, body, false, status, responseHeaders,
        responseBody, false, true, "s1", "stub", 3);
  }

  private static final List<Header> JSON = List.of(new Header("Content-Type", "application/json"));
  private static final List<Header> TENANT = List.of(new Header("X-Tenant", "acme"));

  @Test
  void aCallThatFollowsTheContractHasNoDrift() {
    // Given
    HistoryEntry entry = call("GET", "/orders?limit=5", TENANT, "", 200, JSON, "[{\"id\":1,\"amount\":2.5,\"note\":null,\"lines\":[{\"sku\":\"a\"}]}]");

    // When
    List<Drift> drift = checker.check(contract, entry, "/orders");

    // Then
    assertThat(drift).isEmpty();
  }

  @Test
  void theServerPathPrefixIsOptional() {
    // Given
    HistoryEntry entry = call("GET", "/v1/orders/7", List.of(), "", 200, JSON, "{\"id\":7,\"amount\":1}");

    // When
    List<Drift> drift = checker.check(contract, entry, "/v1/orders/7");

    // Then
    assertThat(drift).isEmpty();
  }

  @Test
  void anUnknownPathIsReportedWithIdsGeneralised() {
    // Given
    HistoryEntry entry = call("GET", "/refunds/123", List.of(), "", 200, JSON, "{}");

    // When
    List<Drift> drift = checker.check(contract, entry, "/refunds/123");

    // Then
    assertThat(drift).containsExactly(new Drift(DriftKind.UNDOCUMENTED_PATH, "GET", "/refunds/{id}", ""));
  }

  @Test
  void anUnknownMethodNamesTheDocumentedOnes() {
    // Given
    HistoryEntry entry = call("DELETE", "/orders", List.of(), "", 204, List.of(), "");

    // When
    List<Drift> drift = checker.check(contract, entry, "/orders");

    // Then
    assertThat(drift).containsExactly(new Drift(DriftKind.UNDOCUMENTED_METHOD, "DELETE", "/orders", "documented: GET, POST"));
  }

  @Test
  void aLiteralPathWinsOverATemplate() {
    // Given
    HistoryEntry entry = call("GET", "/orders/latest", List.of(), "", 200, List.of(), "");

    // When
    List<Drift> drift = checker.check(contract, entry, "/orders/latest");

    // Then
    assertThat(drift).isEmpty();
  }

  @Test
  void parametersAreCheckedAgainstTheContract() {
    // Given
    List<Header> headers = List.of(new Header("X-Debug", "1"), new Header("X-Forwarded-For", "1.2.3.4"), new Header("X-Api-Key", "k"));
    HistoryEntry entry = call("GET", "/orders?limit=500&sort=desc", headers, "", 200, JSON, "[]");

    // When
    List<Drift> drift = checker.check(contract, entry, "/orders");

    // Then
    assertThat(drift).extracting(Drift::kind, Drift::detail).containsExactlyInAnyOrder(
        org.assertj.core.groups.Tuple.tuple(DriftKind.UNDOCUMENTED_QUERY_PARAMETER, "sort"),
        org.assertj.core.groups.Tuple.tuple(DriftKind.UNDOCUMENTED_HEADER, "X-Debug"),
        org.assertj.core.groups.Tuple.tuple(DriftKind.MISSING_PARAMETER, "header X-Tenant"),
        org.assertj.core.groups.Tuple.tuple(DriftKind.INVALID_PARAMETER, "query limit: $: must have a maximum value of 100"));
  }

  @Test
  void aPathValueOfTheWrongTypeIsReported() {
    // Given
    HistoryEntry entry = call("GET", "/orders/abc", List.of(), "", 200, JSON, "{\"id\":1,\"amount\":1}");

    // When
    List<Drift> drift = checker.check(contract, entry, "/orders/abc");

    // Then
    assertThat(drift).extracting(Drift::kind).containsExactly(DriftKind.INVALID_PARAMETER);
    assertThat(drift.get(0).detail()).startsWith("path id: ");
  }

  @Test
  void requestAndResponseBodiesAreCheckedAgainstTheirSchemas() {
    // Given
    HistoryEntry entry = call("POST", "/orders", JSON, "{\"id\":\"x\",\"lines\":[{},{}]}", 201, JSON, "{\"id\":1,\"amount\":-3}");

    // When
    List<Drift> drift = checker.check(contract, entry, "/orders");

    // Then
    assertThat(drift).extracting(Drift::kind).containsOnly(DriftKind.REQUEST_BODY_SCHEMA, DriftKind.RESPONSE_BODY_SCHEMA);
    assertThat(drift).extracting(Drift::detail).contains(
        "$.id: string found, integer expected",
        "$: required property 'amount' not found",
        "$.lines[*]: required property 'sku' not found",
        "201 $.amount: must have a minimum value of 0");
  }

  @Test
  void bodiesStatusesAndContentTypesOutsideTheContractAreReported() {
    // Given
    HistoryEntry wrongType = call("POST", "/orders", List.of(new Header("Content-Type", "text/plain")), "hi", 500, List.of(), "");
    HistoryEntry missingBody = call("POST", "/orders", List.of(), "", 422, List.of(new Header("Content-Type", "application/json")), "{}");
    HistoryEntry bodyOnEmpty = call("GET", "/orders/1", List.of(), "", 204, List.of(new Header("Content-Type", "text/plain")), "x");

    // When / Then
    assertThat(checker.check(contract, wrongType, "/orders")).extracting(Drift::kind, Drift::detail).containsExactly(
        org.assertj.core.groups.Tuple.tuple(DriftKind.UNDOCUMENTED_REQUEST_CONTENT_TYPE, "text/plain"),
        org.assertj.core.groups.Tuple.tuple(DriftKind.UNDOCUMENTED_STATUS, "500"));
    assertThat(checker.check(contract, missingBody, "/orders")).extracting(Drift::kind).containsExactly(
        DriftKind.MISSING_REQUEST_BODY, DriftKind.UNDOCUMENTED_RESPONSE_CONTENT_TYPE);
    assertThat(checker.check(contract, bodyOnEmpty, "/orders/1")).extracting(Drift::kind).containsExactly(DriftKind.UNDOCUMENTED_RESPONSE_CONTENT_TYPE);
  }

  @Test
  void responsesMockHubDidNotServeFromAStubAreNotChecked() {
    // Given
    HistoryEntry miss = new HistoryEntry("e1", 1L, "p1", "GET", "/orders/1", "", "", List.of(), "", false, 404, List.of(), "No stub", false,
        false, null, null, 1);

    // When
    List<Drift> drift = checker.check(contract, miss, "/orders/1");

    // Then
    assertThat(drift).isEmpty();
  }

  @Test
  void swaggerTwoContractsAreUnderstood() {
    // Given
    ApiContract swagger = ApiContract.of(ContractDocument.parse("""
        {"swagger":"2.0","info":{"title":"Pets","version":"1"},"basePath":"/api",
         "definitions":{"Pet":{"type":"object","required":["name"],"properties":{"name":{"type":"string"}}}},
         "paths":{"/pets":{"post":{"parameters":[{"in":"body","name":"pet","required":true,"schema":{"$ref":"#/definitions/Pet"}},
           {"in":"query","name":"dry","type":"boolean"}],
           "responses":{"200":{"description":"ok","schema":{"$ref":"#/definitions/Pet"}}}}}}}
        """));
    HistoryEntry entry = call("POST", "/api/pets?dry=maybe", JSON, "{}", 200, JSON, "{\"name\":\"Rex\"}");

    // When
    List<Drift> drift = checker.check(swagger, entry, "/api/pets");

    // Then
    assertThat(drift).extracting(Drift::kind, Drift::detail).containsExactly(
        org.assertj.core.groups.Tuple.tuple(DriftKind.INVALID_PARAMETER, "query dry: $: string found, boolean expected"),
        org.assertj.core.groups.Tuple.tuple(DriftKind.REQUEST_BODY_SCHEMA, "$: required property 'name' not found"));
  }

  @Test
  void openApi31SchemasUseTheNewerDialect() {
    // Given
    ApiContract v31 = ApiContract.of(ContractDocument.parse("""
        {"openapi":"3.1.0","info":{"title":"T","version":"1"},
         "paths":{"/t":{"get":{"responses":{"200":{"description":"ok","content":{"application/json":{"schema":
           {"type":"object","properties":{"n":{"type":["integer","null"]}}}}}}}}}}}
        """));

    // When
    List<Drift> ok = checker.check(v31, call("GET", "/t", List.of(), "", 200, JSON, "{\"n\":null}"), "/t");
    List<Drift> bad = checker.check(v31, call("GET", "/t", List.of(), "", 200, JSON, "{\"n\":\"x\"}"), "/t");

    // Then
    assertThat(ok).isEmpty();
    assertThat(bad).extracting(Drift::kind).containsExactly(DriftKind.RESPONSE_BODY_SCHEMA);
  }

  @Test
  void aPrefixStrippedOnImportIsAddedBackWhenMatching() {
    // Given
    ApiContract prefixed = ApiContract.of(ContractDocument.parse("""
        {"openapi":"3.0.0","info":{"title":"T","version":"1"},
         "paths":{"/api/v1/items":{"get":{"responses":{"200":{"description":"ok"}}}}}}
        """), "/api/v1/");

    // When
    List<Drift> drift = checker.check(prefixed, call("GET", "/items", List.of(), "", 200, List.of(), ""), "/items");

    // Then
    assertThat(drift).isEmpty();
  }
}
