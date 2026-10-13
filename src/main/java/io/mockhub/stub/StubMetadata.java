package io.mockhub.stub;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import java.util.Map;

public final class StubMetadata {
  public static final String KEY = "mockhub";
  public static final String PROJECT_ID = "projectId";
  public static final String TYPE = "type";

  private StubMetadata() {}

  @SuppressWarnings("unchecked")
  public static Map<String, Object> of(StubMapping stub) {
    if (stub.getMetadata() == null) return Map.of();
    Object m = stub.getMetadata().get(KEY);
    return m instanceof Map ? (Map<String, Object>) m : Map.of();
  }

  public static String projectId(StubMapping stub) {
    Object id = of(stub).get(PROJECT_ID);
    return id == null ? null : id.toString();
  }

  public static boolean isSoap(StubMapping stub) {
    return "soap".equals(of(stub).get(TYPE));
  }

  public static ObjectNode node(ObjectNode stub) {
    ObjectNode metadata = stub.get("metadata") instanceof ObjectNode o ? o : stub.putObject("metadata");
    return metadata.get(KEY) instanceof ObjectNode o ? o : metadata.putObject(KEY);
  }
}
