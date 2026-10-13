package io.mockhub.export;

import static io.mockhub.support.Fixtures.project;
import static io.mockhub.support.Fixtures.stub;
import static org.assertj.core.api.Assertions.assertThat;

import io.mockhub.project.Project;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SampleRequestTest {

  private final Project pay = project("pay");

  @Test
  void buildsPathVariablesFromAPathTemplate() {
    // Given
    var stub = stub("{'name':'Get user','request':{'method':'GET','urlPathTemplate':'/pay/users/{id}/orders/{orderId}'},'response':{'status':200}}");

    // When
    SampleRequest r = SampleRequest.from(pay, stub);

    // Then
    assertThat(r.name()).isEqualTo("Get user");
    assertThat(r.path()).isEqualTo("/users/:id/orders/:orderId");
    assertThat(r.pathVariables()).containsKeys("id", "orderId");
    assertThat(r.folder()).isEqualTo("users");
    assertThat(r.expectedStatus()).isEqualTo(200);
  }

  @Test
  void takesQueryHeadersAndJsonBodyFromMatchers() {
    // Given
    var stub = stub("{'request':{'method':'POST','urlPath':'/pay/charges',"
        + "'queryParameters':{'dryRun':{'equalTo':'true'},'debug':{'absent':true}},"
        + "'headers':{'X-Tenant':{'equalTo':'acme'}},"
        + "'bodyPatterns':[{'equalToJson':{'amount':10}}]},'response':{'status':201}}");

    // When
    SampleRequest r = SampleRequest.from(pay, stub);

    // Then
    assertThat(r.method()).isEqualTo("POST");
    assertThat(r.query()).containsExactly(java.util.Map.entry("dryRun", "true"));
    assertThat(r.headers()).containsEntry("X-Tenant", "acme").containsEntry("Content-Type", "application/json");
    assertThat(r.bodyType()).isEqualTo("json");
    assertThat(r.body()).contains("\"amount\" : 10");
    assertThat(r.name()).isEqualTo("POST /charges");
  }

  @Test
  void buildsAJsonBodyFromJsonPathMatchers() {
    // Given
    var stub = stub("{'request':{'method':'POST','urlPath':'/pay/x','bodyPatterns':["
        + "{'matchesJsonPath':'$.customer.id'},{'matchesJsonPath':{'expression':'$.type','equalTo':'card'}}]}}");

    // When
    SampleRequest r = SampleRequest.from(pay, stub);

    // Then
    assertThat(r.body()).contains("\"customer\"").contains("\"id\"").contains("\"type\" : \"card\"");
  }

  @Test
  void usesSoapMetadataForHeadersAndTheSampleEnvelope() {
    // Given
    var stub = stub("{'request':{'method':'POST','urlPath':'/pay/ws','bodyPatterns':[{'matchesXPath':'//Add'}]},"
        + "'response':{'status':200},'metadata':{'mockhub':{'type':'soap','soapAction':'urn:Add','folder':'Calc',"
        + "'sampleRequest':{'body':'<soap:Envelope/>'}}}}");

    // When
    SampleRequest r = SampleRequest.from(pay, stub);

    // Then
    assertThat(r.folder()).isEqualTo("Calc");
    assertThat(r.bodyType()).isEqualTo("xml");
    assertThat(r.body()).isEqualTo("<soap:Envelope/>");
    assertThat(r.headers()).containsEntry("SOAPAction", "\"urn:Add\"").containsEntry("Content-Type", "text/xml; charset=utf-8");
  }

  @Test
  void hasNoExpectedStatusForFaultsAndProxies() {
    assertThat(SampleRequest.from(pay, stub("{'request':{'urlPath':'/pay/a'},'response':{'fault':'CONNECTION_RESET_BY_PEER'}}")).expectedStatus()).isNull();
    assertThat(SampleRequest.from(pay, stub("{'request':{'urlPath':'/pay/a'},'response':{'proxyBaseUrl':'http://x'}}")).expectedStatus()).isNull();
  }

  @ParameterizedTest
  @CsvSource({
      "^/users/[0-9]+$, /users/1",
      "/users/\\d+/orders, /users/1/orders",
      "/files/.*, /files/sample",
      "/items/[^/]+, /items/sample",
      "/(v1|v2)/status, /v1/status"
  })
  void turnsRegexesIntoExamplePaths(String regex, String sample) {
    assertThat(SampleRequest.sampleFromRegex(regex)).isEqualTo(sample);
  }
}
