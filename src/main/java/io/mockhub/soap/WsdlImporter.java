package io.mockhub.soap;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.api.ApiException;
import io.mockhub.api.Jsons;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public final class WsdlImporter {
  static final String WSDL = "http://schemas.xmlsoap.org/wsdl/";
  static final String SOAP11_BINDING = "http://schemas.xmlsoap.org/wsdl/soap/";
  static final String SOAP12_BINDING = "http://schemas.xmlsoap.org/wsdl/soap12/";
  static final String SOAP11_ENV = "http://schemas.xmlsoap.org/soap/envelope/";
  static final String SOAP12_ENV = "http://www.w3.org/2003/05/soap-envelope";

  public record Operation(String name, String soapAction, String requestElement, String responseElement) {}

  public record Result(String serviceName, String endpointPath, String soapVersion, List<Operation> operations, List<ObjectNode> stubs) {}

  private WsdlImporter() {}

  public static Result importWsdl(String wsdl, String endpointPathOverride, String projectBasePath) {
    Document doc = parse(wsdl);
    Element defs = doc.getDocumentElement();
    if (!WSDL.equals(defs.getNamespaceURI()) || !"definitions".equals(defs.getLocalName())) {
      throw ApiException.badRequest("Not a WSDL 1.1 document (expected wsdl:definitions root). WSDL 2.0 is not supported.");
    }
    String tns = defs.getAttribute("targetNamespace");

    List<Element> schemas = new ArrayList<>();
    for (Element types : wsdlChildren(defs, "types")) {
      for (Element s : XsdSampler.children(types)) {
        if (XsdSampler.XSD.equals(s.getNamespaceURI()) && "schema".equals(s.getLocalName())) schemas.add(s);
      }
    }
    XsdSampler sampler = new XsdSampler(schemas);

    Map<QName, List<Part>> messages = new HashMap<>();
    for (Element m : wsdlChildren(defs, "message")) {
      List<Part> parts = new ArrayList<>();
      for (Element p : wsdlChildren(m, "part")) {
        boolean isElement = p.hasAttribute("element");
        parts.add(new Part(p.getAttribute("name"), XsdSampler.resolve(p, isElement ? p.getAttribute("element") : p.getAttribute("type")), isElement));
      }
      messages.put(new QName(tns, m.getAttribute("name")), parts);
    }

    Map<QName, Map<String, QName[]>> portTypes = new HashMap<>();
    for (Element pt : wsdlChildren(defs, "portType")) {
      Map<String, QName[]> ops = new LinkedHashMap<>();
      for (Element op : wsdlChildren(pt, "operation")) {
        Element in = first(wsdlChildren(op, "input"));
        Element out = first(wsdlChildren(op, "output"));
        ops.put(op.getAttribute("name"), new QName[] {
            in == null ? null : XsdSampler.resolve(in, in.getAttribute("message")),
            out == null ? null : XsdSampler.resolve(out, out.getAttribute("message"))});
      }
      portTypes.put(new QName(tns, pt.getAttribute("name")), ops);
    }

    Element chosenPort = null;
    Element chosenBinding = null;
    String soapVersion = null;
    String serviceName = "Service";
    for (Element service : wsdlChildren(defs, "service")) {
      for (Element port : wsdlChildren(service, "port")) {
        Element binding = binding(defs, XsdSampler.resolve(port, port.getAttribute("binding")));
        if (binding == null) continue;
        String version = soapVersionOf(binding);
        if (version == null) continue;
        if (chosenPort == null || ("1.1".equals(version) && !"1.1".equals(soapVersion))) {
          chosenPort = port;
          chosenBinding = binding;
          soapVersion = version;
          serviceName = service.getAttribute("name");
        }
      }
    }
    if (chosenBinding == null) {
      throw ApiException.badRequest("No SOAP 1.1 or 1.2 binding with a service port found in the WSDL");
    }

    String endpointPath = endpointPathOverride != null && !endpointPathOverride.isBlank()
        ? normalisePath(endpointPathOverride)
        : addressPath(chosenPort, serviceName);
    boolean rpc = "rpc".equals(bindingStyle(chosenBinding));
    String envNs = "1.2".equals(soapVersion) ? SOAP12_ENV : SOAP11_ENV;
    String contentType = "1.2".equals(soapVersion) ? "application/soap+xml; charset=utf-8" : "text/xml; charset=utf-8";
    Map<String, QName[]> ops = portTypes.getOrDefault(XsdSampler.resolve(chosenBinding, chosenBinding.getAttribute("type")), Map.of());

    List<Operation> operations = new ArrayList<>();
    List<ObjectNode> stubs = new ArrayList<>();
    for (Element bop : wsdlChildren(chosenBinding, "operation")) {
      String opName = bop.getAttribute("name");
      String soapAction = soapAction(bop);
      QName[] io = ops.getOrDefault(opName, new QName[2]);
      String rpcNs = rpcNamespace(bop, tns);

      QName reqElement = rpc ? new QName(rpcNs, opName) : firstElement(messages.get(io[0]), opName);
      QName respElement = rpc ? new QName(rpcNs, opName + "Response") : firstElement(messages.get(io[1]), opName + "Response");
      String reqXml = rpc ? sampler.renderRpcWrapper(reqElement, partTypes(messages.get(io[0])), 2) : sampler.renderElement(reqElement, 2);
      String respXml = rpc ? sampler.renderRpcWrapper(respElement, partTypes(messages.get(io[1])), 2) : sampler.renderElement(respElement, 2);

      operations.add(new Operation(opName, soapAction, reqElement.getLocalPart(), respElement.getLocalPart()));

      ObjectNode stub = Jsons.object();
      stub.put("name", serviceName + " · " + opName);
      stub.put("priority", 5);
      ObjectNode request = stub.putObject("request");
      request.put("method", "POST");
      request.put("urlPath", endpointPath);
      request.putArray("bodyPatterns").addObject()
          .put("matchesXPath", "//*[local-name()='Body']/*[local-name()='" + reqElement.getLocalPart() + "']");
      ObjectNode response = stub.putObject("response");
      response.put("status", 200);
      response.putObject("headers").put("Content-Type", contentType);
      response.put("body", envelope(envNs, respXml));
      ObjectNode meta = stub.putObject("metadata").putObject("mockhub");
      meta.put("type", "soap");
      meta.put("soapVersion", soapVersion);
      meta.put("operation", opName);
      if (soapAction != null && !soapAction.isEmpty()) meta.put("soapAction", soapAction);
      meta.put("folder", serviceName);
      meta.putObject("sampleRequest").put("body", envelope(envNs, reqXml));
      stubs.add(stub);
    }

    stubs.add(wsdlStub(wsdl, serviceName, endpointPath, projectBasePath));
    stubs.add(unknownOperationFault(serviceName, endpointPath, envNs, contentType, soapVersion));
    return new Result(serviceName, endpointPath, soapVersion, operations, stubs);
  }

  private static ObjectNode wsdlStub(String wsdl, String serviceName, String endpointPath, String basePath) {
    Matcher m = Pattern.compile("(<(?:[\\w-]+:)?address\\b[^>]*\\blocation=\")([^\"]*)(\")").matcher(wsdl);
    String served = m.replaceAll(r -> Matcher.quoteReplacement(r.group(1) + "{{request.baseUrl}}" + basePath + endpointPath + r.group(3)));

    ObjectNode stub = Jsons.object();
    stub.put("name", serviceName + " · WSDL");
    stub.put("priority", 4);
    ObjectNode request = stub.putObject("request");
    request.put("method", "GET");
    request.put("urlPath", endpointPath);
    request.putObject("queryParameters").putObject("wsdl").put("matches", ".*");
    ObjectNode response = stub.putObject("response");
    response.put("status", 200);
    response.putObject("headers").put("Content-Type", "text/xml; charset=utf-8");
    response.put("body", served);
    response.putArray("transformers").add("response-template");
    ObjectNode meta = stub.putObject("metadata").putObject("mockhub");
    meta.put("type", "soap");
    meta.put("folder", serviceName);
    meta.putObject("sampleRequest").put("path", endpointPath).putObject("query").put("wsdl", "");
    return stub;
  }

  private static ObjectNode unknownOperationFault(String serviceName, String endpointPath, String envNs, String contentType, String version) {
    String fault = "1.2".equals(version)
        ? "    <soap:Fault>\n      <soap:Code><soap:Value>soap:Sender</soap:Value></soap:Code>\n"
            + "      <soap:Reason><soap:Text xml:lang=\"en\">No mock configured for this operation</soap:Text></soap:Reason>\n    </soap:Fault>"
        : "    <soap:Fault>\n      <faultcode>soap:Client</faultcode>\n      <faultstring>No mock configured for this operation</faultstring>\n    </soap:Fault>";
    ObjectNode stub = Jsons.object();
    stub.put("name", serviceName + " · Unknown operation (fault)");
    stub.put("priority", 9);
    ObjectNode request = stub.putObject("request");
    request.put("method", "POST");
    request.put("urlPath", endpointPath);
    ObjectNode response = stub.putObject("response");
    response.put("status", 500);
    response.putObject("headers").put("Content-Type", contentType);
    response.put("body", "<soap:Envelope xmlns:soap=\"" + envNs + "\">\n  <soap:Body>\n" + fault + "\n  </soap:Body>\n</soap:Envelope>");
    ObjectNode meta = stub.putObject("metadata").putObject("mockhub");
    meta.put("type", "soap");
    meta.put("soapVersion", version);
    meta.put("folder", serviceName);
    return stub;
  }

  static String envelope(String envNs, String bodyXml) {
    return "<soap:Envelope xmlns:soap=\"" + envNs + "\">\n  <soap:Header/>\n  <soap:Body>\n" + bodyXml + "\n  </soap:Body>\n</soap:Envelope>";
  }

  private record Part(String name, QName ref, boolean isElement) {}

  private static QName firstElement(List<Part> parts, String fallbackName) {
    if (parts != null) {
      for (Part p : parts) {
        if (p.isElement()) return p.ref();
      }
    }
    return new QName("", fallbackName);
  }

  private static List<Map.Entry<String, QName>> partTypes(List<Part> parts) {
    List<Map.Entry<String, QName>> out = new ArrayList<>();
    if (parts != null) {
      for (Part p : parts) out.add(new AbstractMap.SimpleEntry<>(p.name(), p.ref()));
    }
    return out;
  }

  private static Element binding(Element defs, QName name) {
    for (Element b : wsdlChildren(defs, "binding")) {
      if (b.getAttribute("name").equals(name.getLocalPart())) return b;
    }
    return null;
  }

  private static String soapVersionOf(Element binding) {
    for (Element c : XsdSampler.children(binding)) {
      if ("binding".equals(c.getLocalName())) {
        if (SOAP11_BINDING.equals(c.getNamespaceURI())) return "1.1";
        if (SOAP12_BINDING.equals(c.getNamespaceURI())) return "1.2";
      }
    }
    return null;
  }

  private static String bindingStyle(Element binding) {
    for (Element c : XsdSampler.children(binding)) {
      if ("binding".equals(c.getLocalName()) && c.hasAttribute("style")) return c.getAttribute("style");
    }
    return "document";
  }

  private static String soapAction(Element bindingOperation) {
    for (Element c : XsdSampler.children(bindingOperation)) {
      if ("operation".equals(c.getLocalName()) && !WSDL.equals(c.getNamespaceURI())) return c.getAttribute("soapAction");
    }
    return null;
  }

  private static String rpcNamespace(Element bindingOperation, String fallback) {
    for (Element input : wsdlChildren(bindingOperation, "input")) {
      for (Element body : XsdSampler.children(input)) {
        if ("body".equals(body.getLocalName()) && body.hasAttribute("namespace")) return body.getAttribute("namespace");
      }
    }
    return fallback;
  }

  private static String addressPath(Element port, String serviceName) {
    for (Element c : XsdSampler.children(port)) {
      if ("address".equals(c.getLocalName()) && c.hasAttribute("location")) {
        try {
          String path = URI.create(c.getAttribute("location").trim()).getPath();
          if (path != null && !path.isEmpty() && !path.equals("/")) return path;
        } catch (IllegalArgumentException ignored) {
        }
      }
    }
    return "/" + serviceName;
  }

  private static String normalisePath(String path) {
    String p = path.trim();
    return p.startsWith("/") ? p : "/" + p;
  }

  private static List<Element> wsdlChildren(Element parent, String localName) {
    List<Element> out = new ArrayList<>();
    for (Element e : XsdSampler.children(parent)) {
      if (WSDL.equals(e.getNamespaceURI()) && localName.equals(e.getLocalName())) out.add(e);
    }
    return out;
  }

  private static Element first(List<Element> list) {
    return list.isEmpty() ? null : list.get(0);
  }

  private static Document parse(String xml) {
    try {
      DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
      f.setNamespaceAware(true);
      f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      f.setFeature("http://xml.org/sax/features/external-general-entities", false);
      f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      f.setXIncludeAware(false);
      f.setExpandEntityReferences(false);
      return f.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw ApiException.badRequest("Cannot parse WSDL: " + e.getMessage());
    }
  }
}
