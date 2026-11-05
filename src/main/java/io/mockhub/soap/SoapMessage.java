package io.mockhub.soap;

import java.io.StringReader;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

public record SoapMessage(String version, String action, String operation, String namespace) {
  public static final String SOAP11 = "1.1";
  public static final String SOAP12 = "1.2";
  private static final Pattern ACTION_PARAMETER = Pattern.compile("action\\s*=\\s*\"?([^\";]+)\"?", Pattern.CASE_INSENSITIVE);
  private static final XMLInputFactory FACTORY = factory();

  public static Optional<SoapMessage> detect(String contentType, String soapActionHeader, String body) {
    if (body == null || body.isBlank() || body.indexOf('<') < 0) return Optional.empty();
    try {
      XMLStreamReader r = FACTORY.createXMLStreamReader(new StringReader(body));
      try {
        if (!nextElement(r)) return Optional.empty();
        String version = versionOf(r.getNamespaceURI());
        if (version == null || !"Envelope".equals(r.getLocalName())) return Optional.empty();
        String operation = null;
        String namespace = null;
        int depth = 1;
        boolean inBody = false;
        while (r.hasNext() && depth > 0) {
          int event = r.next();
          if (event == XMLStreamConstants.START_ELEMENT) {
            depth++;
            if (depth == 2 && "Body".equals(r.getLocalName())) inBody = true;
            else if (inBody && depth == 3) {
              operation = r.getLocalName();
              namespace = r.getNamespaceURI() == null ? "" : r.getNamespaceURI();
              break;
            }
          } else if (event == XMLStreamConstants.END_ELEMENT) {
            depth--;
            if (depth == 1) inBody = false;
          }
        }
        return Optional.of(new SoapMessage(version, action(contentType, soapActionHeader), operation, namespace));
      } finally {
        r.close();
      }
    } catch (XMLStreamException e) {
      return Optional.empty();
    }
  }

  public String envelopeNamespace() {
    return SOAP12.equals(version) ? WsdlImporter.SOAP12_ENV : WsdlImporter.SOAP11_ENV;
  }

  public String contentType() {
    return SOAP12.equals(version) ? "application/soap+xml; charset=utf-8" : "text/xml; charset=utf-8";
  }

  public String responseElement() {
    return operation == null ? "Response" : operation + "Response";
  }

  private static String versionOf(String namespace) {
    if (WsdlImporter.SOAP11_ENV.equals(namespace)) return SOAP11;
    if (WsdlImporter.SOAP12_ENV.equals(namespace)) return SOAP12;
    return null;
  }

  private static String action(String contentType, String header) {
    if (header != null && !header.isBlank()) return header.trim().replaceAll("^\"|\"$", "");
    if (contentType != null && contentType.toLowerCase(Locale.ROOT).contains("action")) {
      Matcher m = ACTION_PARAMETER.matcher(contentType);
      if (m.find()) return m.group(1).trim();
    }
    return "";
  }

  private static boolean nextElement(XMLStreamReader r) throws XMLStreamException {
    while (r.hasNext()) {
      if (r.next() == XMLStreamConstants.START_ELEMENT) return true;
    }
    return false;
  }

  private static XMLInputFactory factory() {
    XMLInputFactory f = XMLInputFactory.newFactory();
    f.setProperty(XMLInputFactory.SUPPORT_DTD, false);
    f.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
    f.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);
    return f;
  }
}
