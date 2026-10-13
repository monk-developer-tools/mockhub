package io.mockhub.soap;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.namespace.QName;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

final class XsdSampler {
  static final String XSD = "http://www.w3.org/2001/XMLSchema";
  private static final int MAX_DEPTH = 10;

  private final Map<QName, Element> elements = new HashMap<>();
  private final Map<QName, Element> complexTypes = new HashMap<>();
  private final Map<QName, Element> simpleTypes = new HashMap<>();
  private final Map<String, String> prefixes = new LinkedHashMap<>();
  private final Deque<QName> typeStack = new ArrayDeque<>();

  XsdSampler(List<Element> schemas) {
    for (Element schema : schemas) {
      String tns = schema.getAttribute("targetNamespace");
      for (Element child : children(schema)) {
        String name = child.getAttribute("name");
        if (name.isEmpty()) continue;
        QName q = new QName(tns, name);
        switch (child.getLocalName()) {
          case "element" -> elements.put(q, child);
          case "complexType" -> complexTypes.put(q, child);
          case "simpleType" -> simpleTypes.put(q, child);
          default -> { }
        }
      }
    }
  }

  boolean hasElement(QName name) {
    return elements.containsKey(name);
  }

  String renderElement(QName name, int indent) {
    prefixes.clear();
    Element decl = elements.get(name);
    String body;
    String qualified = qname(name);
    if (decl == null) {
      body = pad(indent) + "<" + qualified + "><!-- schema for " + name.getLocalPart() + " not found in WSDL --></" + qualified + ">";
    } else {
      body = renderDeclared(decl, name, indent, true);
    }
    return addNamespaceDeclarations(body, qualified);
  }

  String renderRpcWrapper(QName wrapper, List<Map.Entry<String, QName>> parts, int indent) {
    prefixes.clear();
    String qualified = qname(wrapper);
    StringBuilder b = new StringBuilder(pad(indent)).append('<').append(qualified).append(">\n");
    for (Map.Entry<String, QName> part : parts) {
      b.append(renderTyped(part.getKey(), part.getValue(), indent + 1)).append('\n');
    }
    b.append(pad(indent)).append("</").append(qualified).append('>');
    return addNamespaceDeclarations(b.toString(), qualified);
  }

  private String renderDeclared(Element decl, QName name, int indent, boolean qualified) {
    if (decl.hasAttribute("ref")) {
      QName ref = resolve(decl, decl.getAttribute("ref"));
      Element target = elements.get(ref);
      return target == null
          ? pad(indent) + "<" + qname(ref) + "/>"
          : renderDeclared(target, ref, indent, true);
    }
    String tag = qualified ? qname(name) : name.getLocalPart();
    if (decl.hasAttribute("type")) {
      return renderWithType(tag, resolve(decl, decl.getAttribute("type")), indent);
    }
    Element inlineComplex = child(decl, "complexType");
    if (inlineComplex != null) return renderComplex(tag, inlineComplex, indent);
    Element inlineSimple = child(decl, "simpleType");
    if (inlineSimple != null) return pad(indent) + "<" + tag + ">" + simpleValue(inlineSimple) + "</" + tag + ">";
    return pad(indent) + "<" + tag + ">string</" + tag + ">";
  }

  private String renderTyped(String localName, QName type, int indent) {
    return renderWithType(localName, type, indent);
  }

  private String renderWithType(String tag, QName type, int indent) {
    if (XSD.equals(type.getNamespaceURI())) {
      return pad(indent) + "<" + tag + ">" + builtin(type.getLocalPart()) + "</" + tag + ">";
    }
    Element simple = simpleTypes.get(type);
    if (simple != null) return pad(indent) + "<" + tag + ">" + simpleValue(simple) + "</" + tag + ">";
    Element complex = complexTypes.get(type);
    if (complex == null) return pad(indent) + "<" + tag + ">?</" + tag + ">";
    if (typeStack.contains(type) || typeStack.size() > MAX_DEPTH) {
      return pad(indent) + "<" + tag + "/><!-- recursive " + type.getLocalPart() + " -->";
    }
    typeStack.push(type);
    try {
      return renderComplex(tag, complex, indent);
    } finally {
      typeStack.pop();
    }
  }

  private String renderComplex(String tag, Element complexType, int indent) {
    List<String> attributes = new ArrayList<>();
    List<String> childXml = new ArrayList<>();
    String text = collect(complexType, attributes, childXml, indent + 1);
    StringBuilder b = new StringBuilder(pad(indent)).append('<').append(tag);
    for (String a : attributes) b.append(' ').append(a);
    if (text != null) {
      return b.append('>').append(text).append("</").append(tag).append('>').toString();
    }
    if (childXml.isEmpty()) return b.append("/>").toString();
    b.append(">\n");
    for (String c : childXml) b.append(c).append('\n');
    return b.append(pad(indent)).append("</").append(tag).append('>').toString();
  }

  private String collect(Element type, List<String> attributes, List<String> out, int indent) {
    String text = null;
    for (Element c : children(type)) {
      switch (c.getLocalName()) {
        case "sequence", "all" -> particles(c, out, indent, false);
        case "choice" -> particles(c, out, indent, true);
        case "attribute" -> attribute(c, attributes);
        case "complexContent", "simpleContent" -> {
          Element ext = child(c, "extension") != null ? child(c, "extension") : child(c, "restriction");
          if (ext == null) break;
          QName base = resolve(ext, ext.getAttribute("base"));
          if (c.getLocalName().equals("simpleContent")) {
            text = XSD.equals(base.getNamespaceURI()) ? builtin(base.getLocalPart())
                : simpleTypes.containsKey(base) ? simpleValue(simpleTypes.get(base)) : "value";
          } else {
            Element baseType = complexTypes.get(base);
            if (baseType != null && !typeStack.contains(base)) {
              typeStack.push(base);
              try {
                collect(baseType, attributes, out, indent);
              } finally {
                typeStack.pop();
              }
            }
          }
          String own = collect(ext, attributes, out, indent);
          if (own != null) text = own;
        }
        default -> { }
      }
    }
    return text;
  }

  private void particles(Element group, List<String> out, int indent, boolean choice) {
    for (Element p : children(group)) {
      switch (p.getLocalName()) {
        case "element" -> {
          String name = p.getAttribute("name");
          String tns = schemaOf(p).getAttribute("targetNamespace");
          boolean qualified = p.hasAttribute("form")
              ? "qualified".equals(p.getAttribute("form"))
              : "qualified".equals(schemaOf(p).getAttribute("elementFormDefault"));
          out.add(renderDeclared(p, new QName(tns, name), indent, qualified));
        }
        case "sequence", "all" -> particles(p, out, indent, false);
        case "choice" -> particles(p, out, indent, true);
        case "any" -> out.add(pad(indent) + "<!-- any element -->");
        default -> { }
      }
      if (choice && !out.isEmpty()) return;
    }
  }

  private void attribute(Element attr, List<String> attributes) {
    String name = attr.getAttribute("name");
    if (name.isEmpty() || "prohibited".equals(attr.getAttribute("use"))) return;
    String value = "value";
    if (attr.hasAttribute("fixed")) value = attr.getAttribute("fixed");
    else if (attr.hasAttribute("default")) value = attr.getAttribute("default");
    else if (attr.hasAttribute("type")) {
      QName t = resolve(attr, attr.getAttribute("type"));
      value = XSD.equals(t.getNamespaceURI()) ? builtin(t.getLocalPart())
          : simpleTypes.containsKey(t) ? simpleValue(simpleTypes.get(t)) : "value";
    }
    attributes.add(name + "=\"" + value + "\"");
  }

  private String simpleValue(Element simpleType) {
    Element restriction = child(simpleType, "restriction");
    if (restriction != null) {
      Element enumeration = child(restriction, "enumeration");
      if (enumeration != null) return enumeration.getAttribute("value");
      if (restriction.hasAttribute("base")) {
        QName base = resolve(restriction, restriction.getAttribute("base"));
        if (XSD.equals(base.getNamespaceURI())) return builtin(base.getLocalPart());
        Element next = simpleTypes.get(base);
        if (next != null && next != simpleType) return simpleValue(next);
      }
    }
    return "value";
  }

  static String builtin(String type) {
    return switch (type) {
      case "int", "integer", "long", "short", "byte", "nonNegativeInteger", "positiveInteger",
          "unsignedInt", "unsignedLong", "unsignedShort", "unsignedByte" -> "1";
      case "decimal", "double", "float" -> "1.0";
      case "boolean" -> "true";
      case "date" -> "2024-01-01";
      case "dateTime" -> "2024-01-01T00:00:00Z";
      case "time" -> "12:00:00";
      case "anyURI" -> "http://example.com";
      case "base64Binary" -> "U2FtcGxl";
      case "hexBinary" -> "00";
      case "duration" -> "P1D";
      default -> "string";
    };
  }

  private String qname(QName q) {
    if (q.getNamespaceURI().isEmpty()) return q.getLocalPart();
    String prefix = prefixes.computeIfAbsent(q.getNamespaceURI(), ns -> "ns" + (prefixes.size() + 1));
    return prefix + ":" + q.getLocalPart();
  }

  private String addNamespaceDeclarations(String xml, String rootTag) {
    StringBuilder decls = new StringBuilder();
    prefixes.forEach((ns, prefix) -> decls.append(" xmlns:").append(prefix).append("=\"").append(ns).append('"'));
    int at = xml.indexOf("<" + rootTag) + rootTag.length() + 1;
    return xml.substring(0, at) + decls + xml.substring(at);
  }

  static QName resolve(Element context, String prefixed) {
    int colon = prefixed.indexOf(':');
    String prefix = colon < 0 ? null : prefixed.substring(0, colon);
    String local = colon < 0 ? prefixed : prefixed.substring(colon + 1);
    String ns = context.lookupNamespaceURI(prefix);
    return new QName(ns == null ? "" : ns, local);
  }

  private static Element schemaOf(Element e) {
    Node n = e;
    while (n != null && !(n instanceof Element el && "schema".equals(el.getLocalName()) && XSD.equals(el.getNamespaceURI()))) {
      n = n.getParentNode();
    }
    return n == null ? e.getOwnerDocument().getDocumentElement() : (Element) n;
  }

  static List<Element> children(Element parent) {
    List<Element> out = new ArrayList<>();
    for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
      if (n instanceof Element e) {
        if (XSD.equals(e.getNamespaceURI()) && ("annotation".equals(e.getLocalName()))) continue;
        out.add(e);
      }
    }
    return out;
  }

  private static Element child(Element parent, String localName) {
    for (Element e : children(parent)) {
      if (localName.equals(e.getLocalName())) return e;
    }
    return null;
  }

  private static String pad(int indent) {
    return "  ".repeat(indent);
  }
}
