package io.mockhub.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SoapSamplesIntegrationTest {

  private static final String API = "/__admin/mockhub/api";
  private static final String SAMPLES = """
      POST /ws/crm  SOAPAction: "urn:crm/GetCustomer"
      <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
        <soap:Body><crm:GetCustomer xmlns:crm="urn:crm"><crm:id>7</crm:id></crm:GetCustomer></soap:Body>
      </soap:Envelope>

      <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
        <soap:Body><crm:DeleteCustomer xmlns:crm="urn:crm"><crm:id>7</crm:id></crm:DeleteCustomer></soap:Body>
      </soap:Envelope>
      <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
        <soap:Body><crm:GetCustomer xmlns:crm="urn:crm"><crm:id>8</crm:id></crm:GetCustomer></soap:Body>
      </soap:Envelope>
      """;

  @TempDir
  Path data;
  private MockHubServer server;
  private ApiClient api;
  private String projectId;

  @BeforeEach
  void start() {
    server = MockHubServer.start(data);
    api = server.api();
    projectId = api.post(API + "/projects", "{\"name\":\"CRM\",\"slug\":\"crm\"}").json().get("id").asText();
  }

  @AfterEach
  void stop() {
    server.close();
  }

  private ApiClient.Result call(String operation) {
    return api.send("POST", "/crm/ws/crm", """
        <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body><%s xmlns="urn:crm"><id>1</id></%s></s:Body></s:Envelope>"""
        .formatted(operation, operation), "text/xml; charset=utf-8", Map.of());
  }

  @Test
  void createsOneStubPerOperationFoundInSampleRequests() {
    // When
    ApiClient.Result imported = api.post(API + "/projects/" + projectId + "/import/soap?path=/ws/crm&name=CRM", SAMPLES);
    JsonNode stubs = api.get(API + "/projects/" + projectId + "/stubs").json();

    // Then
    assertThat(imported.status()).as(imported.body()).isEqualTo(200);
    assertThat(imported.json().get("operations").toString()).isEqualTo("[\"GetCustomer\",\"DeleteCustomer\"]");
    assertThat(imported.json().get("imported").asInt()).isEqualTo(3);
    assertThat(stubs.toString()).contains("CRM · GetCustomer", "CRM · DeleteCustomer", "CRM · any other operation");
    assertThat(call("GetCustomer").body()).contains("<ns:GetCustomerResponse xmlns:ns=\"urn:crm\">");
  }

  @Test
  void theEndpointAnswersOperationsWithoutAStubAndRejectsOtherBodies() {
    // Given
    api.post(API + "/projects/" + projectId + "/import/soap?path=/ws/crm", SAMPLES);

    // When
    ApiClient.Result unknown = call("RenameCustomer");
    ApiClient.Result notSoap = api.send("POST", "/crm/ws/crm", "<order/>", "text/xml", Map.of());

    // Then
    assertThat(unknown.status()).isEqualTo(200);
    assertThat(unknown.body()).contains("RenameCustomerResponse");
    assertThat(unknown.headers().firstValue("X-MockHub-Soap-Operation")).contains("RenameCustomer");
    assertThat(unknown.contentType()).startsWith("text/xml");
    assertThat(notSoap.status()).isEqualTo(400);
    assertThat(notSoap.body()).contains("isn't a SOAP 1.1 or 1.2 envelope");
  }

  @Test
  void explainsAPasteWithoutSoapRequests() {
    // When
    ApiClient.Result imported = api.post(API + "/projects/" + projectId + "/import/soap", "<order><id>1</id></order>");

    // Then
    assertThat(imported.status()).isEqualTo(400);
    assertThat(imported.body()).contains("No SOAP request found");
  }
}
