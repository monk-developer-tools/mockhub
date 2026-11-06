package io.mockhub.publictools;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayInputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

final class BinReads {
  private static final Pattern OPERATION = Pattern.compile("^\\s*(?:query|mutation|subscription)\\s+([_A-Za-z][_0-9A-Za-z]*)");
  private static final Pattern FIRST_FIELD = Pattern.compile("^\\s*(?:(?:query|mutation|subscription)\\s*(?:\\([^)]*\\))?\\s*)?\\{\\s*([_A-Za-z][_0-9A-Za-z]*)");
  private static final Pattern ACTION = Pattern.compile("action\\s*=\\s*\"?([^\";]+)\"?", Pattern.CASE_INSENSITIVE);

  private BinReads() {}

  static void checkXPath(String expression) {
    try {
      XPathFactory.newInstance().newXPath().compile(expression);
    } catch (XPathExpressionException e) {
      throw new IllegalArgumentException("The XPath " + expression + " isn't valid, try one like /order/id or //id");
    }
  }

  static Optional<String> form(PublicRequest request, String field) {
    String type = Optional.ofNullable(request.header("Content-Type")).orElse("").toLowerCase(Locale.ROOT);
    String body = request.body();
    if (!type.contains("application/x-www-form-urlencoded") || body == null || body.isEmpty()) return Optional.empty();
    for (String pair : body.split("&")) {
      int eq = pair.indexOf('=');
      String key = decode(eq < 0 ? pair : pair.substring(0, eq));
      if (key.equals(field)) return Optional.of(eq < 0 ? "" : decode(pair.substring(eq + 1)));
    }
    return Optional.empty();
  }

  static Optional<String> xml(String body, String expression) {
    if (body == null || body.isBlank() || !body.stripLeading().startsWith("<")) return Optional.empty();
    try {
      Document doc = parse(body);
      Node node = (Node) XPathFactory.newInstance().newXPath().compile(expression).evaluate(doc, XPathConstants.NODE);
      return node == null ? Optional.empty() : Optional.of(node.getTextContent().trim());
    } catch (Exception e) {
      return Optional.empty();
    }
  }

  static Optional<String> soapAction(PublicRequest request) {
    String header = request.header("SOAPAction");
    if (header != null && !header.isBlank()) {
      String value = header.trim();
      if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) value = value.substring(1, value.length() - 1);
      if (!value.isEmpty()) return Optional.of(value);
    }
    String type = request.header("Content-Type");
    if (type == null) return Optional.empty();
    Matcher m = ACTION.matcher(type);
    return m.find() ? Optional.of(m.group(1).trim()) : Optional.empty();
  }

  static Optional<String> graphqlOperation(PublicRequest request, JsonNode json) {
    String query = null;
    if (json != null && json.isObject()) {
      JsonNode name = json.get("operationName");
      if (name != null && name.isTextual() && !name.asText().isBlank()) return Optional.of(name.asText().trim());
      JsonNode q = json.get("query");
      if (q != null && q.isTextual()) query = q.asText();
    }
    if (query == null) query = request.firstQuery("query");
    if (query == null) return Optional.empty();
    Matcher named = OPERATION.matcher(query);
    if (named.find()) return Optional.of(named.group(1));
    Matcher field = FIRST_FIELD.matcher(query);
    return field.find() ? Optional.of(field.group(1)) : Optional.empty();
  }

  private static Document parse(String xml) throws Exception {
    DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
    f.setNamespaceAware(false);
    f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    f.setFeature("http://xml.org/sax/features/external-general-entities", false);
    f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
    f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
    f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
    f.setXIncludeAware(false);
    f.setExpandEntityReferences(false);
    return f.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
  }

  private static String decode(String s) {
    try {
      return URLDecoder.decode(s, StandardCharsets.UTF_8);
    } catch (IllegalArgumentException e) {
      return s;
    }
  }
}
