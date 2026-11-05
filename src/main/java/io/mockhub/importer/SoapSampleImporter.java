package io.mockhub.importer;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.api.ApiException;
import io.mockhub.api.Jsons;
import io.mockhub.soap.SoapEndpointTransformer;
import io.mockhub.soap.SoapMessage;
import io.mockhub.soap.SoapResponses;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SoapSampleImporter implements StubImporter {
  public static final String DEFAULT_PATH = "/ws";
  static final int MAX_SAMPLES = 200;
  private static final Pattern ENVELOPE = Pattern.compile("<([\\w.-]+:)?Envelope\\b[\\s\\S]*?</\\1?Envelope\\s*>");

  @Override
  public String format() {
    return "soap";
  }

  @Override
  public Result parse(Request request) {
    String path = request.option("path") == null ? DEFAULT_PATH : request.option("path").trim();
    if (!path.startsWith("/")) path = "/" + path;
    String service = request.option("name") == null ? "SOAP service" : request.option("name").trim();
    Map<String, Sample> operations = new LinkedHashMap<>();
    Matcher m = ENVELOPE.matcher(request.body());
    int seen = 0;
    while (m.find() && seen++ < MAX_SAMPLES) {
      String xml = m.group();
      SoapMessage.detect(null, null, xml).filter(s -> s.operation() != null).ifPresent(s -> operations.putIfAbsent(s.operation(), new Sample(s, xml)));
    }
    if (operations.isEmpty()) {
      throw ApiException.badRequest("No SOAP request found. Paste one or more SOAP 1.1 or 1.2 envelopes with an operation inside soap:Body");
    }
    List<ObjectNode> stubs = new ArrayList<>();
    ArrayNode names = Jsons.MAPPER.createArrayNode();
    String version = operations.values().iterator().next().message().version();
    for (Sample s : operations.values()) {
      stubs.add(operationStub(service, path, s));
      names.add(s.message().operation());
    }
    stubs.add(endpoint(service, path, version));
    ObjectNode details = Jsons.object().put("service", service).put("endpointPath", path).put("soapVersion", version);
    details.set("operations", names);
    return new Result(stubs, details);
  }

  private record Sample(SoapMessage message, String xml) {}

  private static ObjectNode operationStub(String service, String path, Sample sample) {
    SoapMessage m = sample.message();
    ObjectNode stub = Jsons.object();
    stub.put("name", service + " · " + m.operation());
    ObjectNode request = stub.putObject("request").put("method", "POST").put("urlPath", path);
    request.putArray("bodyPatterns").addObject()
        .put("matchesXPath", "//*[local-name()='Body']/*[local-name()='" + m.operation() + "']");
    ObjectNode response = stub.putObject("response").put("status", 200);
    response.putObject("headers").put("Content-Type", m.contentType());
    response.put("body", SoapResponses.answer(m));
    ObjectNode meta = stub.putObject("metadata").putObject("mockhub");
    meta.put("type", "soap").put("folder", service).put("operation", m.operation()).put("soapVersion", m.version());
    if (!m.action().isEmpty()) meta.put("soapAction", m.action());
    meta.putObject("sampleRequest").put("body", sample.xml());
    return stub;
  }

  public static ObjectNode endpoint(String service, String path, String version) {
    ObjectNode stub = Jsons.object();
    stub.put("name", service + " · any other operation");
    stub.put("priority", 10);
    stub.putObject("request").put("method", "POST").put("urlPath", path);
    ObjectNode response = stub.putObject("response").put("status", 200);
    response.putArray("transformers").add(SoapEndpointTransformer.NAME);
    ObjectNode meta = stub.putObject("metadata").putObject("mockhub");
    meta.put("type", "soap").put("folder", service).put("soapVersion", version)
        .put("description", "Finds the operation in any SOAP request and answers with an <Operation>Response envelope. Set transformerParameters.unknown to \"fault\" to answer with a SOAP fault instead.");
    return stub;
  }
}
