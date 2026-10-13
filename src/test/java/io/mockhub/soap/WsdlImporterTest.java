package io.mockhub.soap;

import static io.mockhub.support.Fixtures.resource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

class WsdlImporterTest {

  private final String wsdl = resource("calculator.wsdl");

  @Test
  void readsServiceEndpointAndOperations() {
    // When
    WsdlImporter.Result result = WsdlImporter.importWsdl(wsdl, null, "/calc");

    // Then
    assertThat(result.serviceName()).isEqualTo("CalculatorService");
    assertThat(result.endpointPath()).isEqualTo("/ws/calculator");
    assertThat(result.soapVersion()).isEqualTo("1.1");
    assertThat(result.operations()).extracting(WsdlImporter.Operation::name).containsExactly("Add", "GetHistory");
    assertThat(result.operations().get(0).soapAction()).isEqualTo("http://example.com/calculator/Add");
  }

  @Test
  void generatesAnOperationStubWithSampleEnvelopes() {
    // When
    ObjectNode add = WsdlImporter.importWsdl(wsdl, null, "/calc").stubs().get(0);

    // Then
    assertThat(add.at("/request/method").asText()).isEqualTo("POST");
    assertThat(add.at("/request/urlPath").asText()).isEqualTo("/ws/calculator");
    assertThat(add.at("/request/bodyPatterns/0/matchesXPath").asText()).isEqualTo("//*[local-name()='Body']/*[local-name()='Add']");
    assertThat(add.at("/response/headers/Content-Type").asText()).startsWith("text/xml");
    assertThat(add.at("/response/body").asText())
        .contains("<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">")
        .contains("<ns1:AddResponse xmlns:ns1=\"http://example.com/calculator\">")
        .contains("<ns1:result>1</ns1:result>");
    assertThat(add.at("/metadata/mockhub/sampleRequest/body").asText()).contains("<ns1:a>1</ns1:a>").contains("<ns1:b>1</ns1:b>");
    assertThat(add.at("/metadata/mockhub/soapAction").asText()).isEqualTo("http://example.com/calculator/Add");
  }

  @Test
  void samplesNamedTypesEnumerationsAndAttributes() {
    // When
    String request = WsdlImporter.importWsdl(wsdl, null, "/calc").stubs().get(1).at("/metadata/mockhub/sampleRequest/body").asText();

    // Then
    assertThat(request).contains("<ns1:user region=\"string\">").contains("<ns1:id>1</ns1:id>").contains("<ns1:status>ACTIVE</ns1:status>");
  }

  @Test
  void servesTheWsdlPointingAtTheMockAndAddsAFallbackFault() {
    // When
    var stubs = WsdlImporter.importWsdl(wsdl, "/soap/calc", "/calc").stubs();

    // Then
    ObjectNode wsdlStub = stubs.get(2);
    assertThat(wsdlStub.at("/request/method").asText()).isEqualTo("GET");
    assertThat(wsdlStub.at("/request/queryParameters/wsdl/matches").asText()).isEqualTo(".*");
    assertThat(wsdlStub.at("/response/body").asText()).contains("location=\"{{request.baseUrl}}/calc/soap/calc\"");
    assertThat(wsdlStub.at("/response/transformers/0").asText()).isEqualTo("response-template");

    ObjectNode fault = stubs.get(3);
    assertThat(fault.at("/priority").asInt()).isEqualTo(9);
    assertThat(fault.at("/response/status").asInt()).isEqualTo(500);
    assertThat(fault.at("/response/body").asText()).contains("<faultcode>soap:Client</faultcode>");
  }

  @Test
  void rejectsNonWsdlAndXxe() {
    assertThatThrownBy(() -> WsdlImporter.importWsdl("<a/>", null, "/x")).hasMessageContaining("Not a WSDL 1.1");
    String xxe = "<?xml version=\"1.0\"?><!DOCTYPE d [<!ENTITY x SYSTEM \"file:///etc/passwd\">]><d>&x;</d>";
    assertThatThrownBy(() -> WsdlImporter.importWsdl(xxe, null, "/x")).hasMessageContaining("Cannot parse WSDL");
  }
}
