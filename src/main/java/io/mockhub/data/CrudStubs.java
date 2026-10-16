package io.mockhub.data;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import io.mockhub.api.Jsons;
import io.mockhub.stub.StubMetadata;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class CrudStubs {
  public static final String TRANSFORMER = "mockhub-crud";
  public static final String KEY = "crud";

  private CrudStubs() {}

  public static ObjectNode stub(CrudResource resource) {
    ObjectNode stub = Jsons.object();
    stub.put("name", "CRUD: " + resource.name());
    ObjectNode request = stub.putObject("request");
    request.put("method", "ANY");
    request.put("urlPathPattern", "/" + resource.name() + "(/[^/]+)?/?");
    ObjectNode response = stub.putObject("response");
    response.put("status", 200);
    response.putArray("transformers").add(TRANSFORMER);
    response.set("transformerParameters", parameters(resource));
    ObjectNode mockhub = stub.putObject("metadata").putObject(StubMetadata.KEY);
    mockhub.set(KEY, parameters(resource));
    mockhub.put("description", "List, read, create, replace, patch and delete " + resource.name()
        + ". Records are kept in the project's Data tab.");
    return stub;
  }

  public static ObjectNode parameters(CrudResource resource) {
    return Jsons.object().put("resource", resource.name()).put("idField", resource.idField()).put("idType", resource.idType().id());
  }

  public static Optional<CrudResource> of(StubMapping stub) {
    Object raw = StubMetadata.of(stub).get(KEY);
    if (!(raw instanceof Map<?, ?> map)) return Optional.empty();
    try {
      return Optional.of(new CrudResource(String.valueOf(map.get("resource")), str(map.get("idField")),
          CrudResource.IdType.parse(str(map.get("idType")))));
    } catch (RuntimeException e) {
      return Optional.empty();
    }
  }

  public static Map<String, CrudResource> resources(List<StubMapping> stubs) {
    Map<String, CrudResource> out = new LinkedHashMap<>();
    stubs.forEach(s -> of(s).ifPresent(r -> out.putIfAbsent(r.name(), r)));
    return out;
  }

  private static String str(Object o) {
    return o == null ? null : o.toString();
  }
}
