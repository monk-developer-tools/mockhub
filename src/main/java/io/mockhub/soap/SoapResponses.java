package io.mockhub.soap;

public final class SoapResponses {
  private SoapResponses() {}

  public static String answer(SoapMessage m) {
    String ns = m.namespace() == null || m.namespace().isEmpty() ? "" : " xmlns:ns=\"" + escape(m.namespace()) + "\"";
    String prefix = ns.isEmpty() ? "" : "ns:";
    String inner = "    <" + prefix + m.responseElement() + ns + ">\n      <" + prefix + "status>OK</" + prefix + "status>\n    </" + prefix + m.responseElement() + ">";
    return WsdlImporter.envelope(m.envelopeNamespace(), inner);
  }

  public static String fault(String version, String message) {
    String text = escape(message);
    String body = SoapMessage.SOAP12.equals(version)
        ? "    <soap:Fault>\n      <soap:Code><soap:Value>soap:Sender</soap:Value></soap:Code>\n"
            + "      <soap:Reason><soap:Text xml:lang=\"en\">" + text + "</soap:Text></soap:Reason>\n    </soap:Fault>"
        : "    <soap:Fault>\n      <faultcode>soap:Client</faultcode>\n      <faultstring>" + text + "</faultstring>\n    </soap:Fault>";
    return WsdlImporter.envelope(SoapMessage.SOAP12.equals(version) ? WsdlImporter.SOAP12_ENV : WsdlImporter.SOAP11_ENV, body);
  }

  static String escape(String s) {
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
  }
}
