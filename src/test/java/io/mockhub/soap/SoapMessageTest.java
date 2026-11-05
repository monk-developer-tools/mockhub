package io.mockhub.soap;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class SoapMessageTest {

  static final String ADD_11 = """
      <?xml version="1.0"?>
      <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:cal="http://example.com/calc">
        <soapenv:Header><cal:Trace>1</cal:Trace></soapenv:Header>
        <soapenv:Body>
          <cal:Add><cal:a>2</cal:a><cal:b>3</cal:b></cal:Add>
        </soapenv:Body>
      </soapenv:Envelope>""";

  static final String GET_12 = """
      <env:Envelope xmlns:env="http://www.w3.org/2003/05/soap-envelope">
        <env:Body><GetCustomer xmlns="urn:crm"><id>7</id></GetCustomer></env:Body>
      </env:Envelope>""";

  @Test
  void findsTheVersionOperationNamespaceAndActionHeader() {
    // When
    Optional<SoapMessage> m = SoapMessage.detect("text/xml; charset=utf-8", "\"http://example.com/calc/Add\"", ADD_11);

    // Then
    assertThat(m).contains(new SoapMessage("1.1", "http://example.com/calc/Add", "Add", "http://example.com/calc"));
  }

  @Test
  void readsTheActionOfSoap12FromTheContentType() {
    // When
    Optional<SoapMessage> m = SoapMessage.detect("application/soap+xml; charset=utf-8; action=\"urn:crm/GetCustomer\"", null, GET_12);

    // Then
    assertThat(m).contains(new SoapMessage("1.2", "urn:crm/GetCustomer", "GetCustomer", "urn:crm"));
  }

  @Test
  void ignoresBodiesThatAreNotSoapEnvelopes() {
    // Then
    assertThat(SoapMessage.detect(null, null, "<order><id>1</id></order>")).isEmpty();
    assertThat(SoapMessage.detect(null, null, "{\"id\":1}")).isEmpty();
    assertThat(SoapMessage.detect(null, null, "<broken")).isEmpty();
  }

  @Test
  void refusesDoctypesSoEntitiesAreNeverExpanded() {
    // Given
    String xxe = "<!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]>"
        + "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\"><s:Body><Op>&e;</Op></s:Body></s:Envelope>";

    // When
    Optional<SoapMessage> m = SoapMessage.detect(null, null, xxe);

    // Then
    assertThat(m.map(SoapMessage::operation).orElse("none")).doesNotContain("root");
  }

  @Test
  void answersWithAResponseElementInTheOperationsNamespace() {
    // Given
    SoapMessage m = SoapMessage.detect(null, null, GET_12).orElseThrow();

    // When
    String answer = SoapResponses.answer(m);

    // Then
    assertThat(m.contentType()).startsWith("application/soap+xml");
    assertThat(answer).contains("http://www.w3.org/2003/05/soap-envelope")
        .contains("<ns:GetCustomerResponse xmlns:ns=\"urn:crm\">");
  }
}
