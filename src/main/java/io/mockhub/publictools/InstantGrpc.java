package io.mockhub.publictools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.protobuf.Descriptors.MethodDescriptor;
import com.google.protobuf.Descriptors.ServiceDescriptor;
import io.mockhub.api.Jsons;
import io.mockhub.grpc.GrpcCatalog;
import io.mockhub.grpc.GrpcException;
import io.mockhub.grpc.GrpcViews;
import io.mockhub.grpc.MessageExamples;
import io.mockhub.grpc.ProtoFiles;
import io.mockhub.project.Project;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class InstantGrpc {
  public static final String HEADER = "x-mockhub-mock";
  public static final String NAMESPACE_PREFIX = "m_";
  public static final int MAX_PROTO = 128 * 1024;
  static final String DESCRIPTOR = "service.dsc";
  private static final Pattern SLUG = Pattern.compile("m-[a-z0-9]{10}");
  private static final Pattern FILE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.-]{0,80}\\.proto");

  private InstantGrpc() {}

  public static boolean isSlug(String value) {
    return value != null && SLUG.matcher(value).matches();
  }

  public static String namespace(String slug) {
    return slug.replace('-', '_');
  }

  static InstantSource source(GrpcCatalog catalog, String text, String fileName) {
    if (text == null || text.isBlank()) throw new InstantStubRules.Refused("Paste or upload a .proto file");
    if (text.length() > MAX_PROTO) throw new InstantStubRules.Refused("A .proto file for an instant mock can be at most 128 KB");
    String name = fileName != null && FILE.matcher(fileName).matches() ? fileName : "service.proto";
    return project -> {
      GrpcCatalog.DescriptorSet set;
      try {
        set = catalog.add(project.id(), DESCRIPTOR, ProtoFiles.compile(text, name, namespace(project.slug())));
      } catch (GrpcException e) {
        throw new InstantStubRules.Refused(e.getMessage());
      }
      if (set.services().isEmpty()) throw new InstantStubRules.Refused("The .proto file defines no service");
      List<JsonNode> stubs = new ArrayList<>();
      int methods = 0;
      for (ServiceDescriptor service : set.services()) {
        for (MethodDescriptor method : service.getMethods()) {
          methods++;
          if (stubs.size() < InstantMocks.MAX_STUBS) stubs.add(starter(service, method, project));
        }
      }
      return new InstantSeed(stubs, List.of(), false, methods - stubs.size());
    };
  }

  private static ObjectNode starter(ServiceDescriptor service, MethodDescriptor method, Project project) {
    ObjectNode stub = Jsons.object().put("name", original(service.getFullName(), project.slug()) + "/" + method.getName());
    stub.putObject("request").put("method", "POST").put("urlPath", "/" + service.getFullName() + "/" + method.getName());
    ObjectNode response = stub.putObject("response").put("status", 200);
    response.set("jsonBody", MessageExamples.of(method.getOutputType()));
    return stub;
  }

  public static ObjectNode tag(ObjectNode stub, String slug) {
    String path = stub.path("request").path("urlPath").asText(stub.path("request").path("url").asText(""));
    if (path.startsWith("/" + namespace(slug) + ".")) {
      ObjectNode meta = stub.has("metadata") && stub.get("metadata").isObject() ? (ObjectNode) stub.get("metadata") : stub.putObject("metadata");
      ObjectNode mockhub = meta.has("mockhub") && meta.get("mockhub").isObject() ? (ObjectNode) meta.get("mockhub") : meta.putObject("mockhub");
      mockhub.put("type", "grpc");
    }
    return stub;
  }

  static String original(String fullName, String slug) {
    String prefix = namespace(slug) + ".";
    return fullName.startsWith(prefix) ? fullName.substring(prefix.length()) : fullName;
  }

  static ObjectNode describe(GrpcCatalog catalog, InstantMocks.Entry entry) {
    ObjectNode view = GrpcViews.project(catalog.setsOf(entry.projectId()));
    ObjectNode out = Jsons.object().put("header", HEADER).put("namespace", namespace(entry.slug()));
    ArrayNode services = out.putArray("services");
    for (JsonNode s : view.path("services")) {
      ObjectNode service = ((ObjectNode) s.deepCopy()).put("original", original(s.path("name").asText(), entry.slug()));
      service.remove("descriptor");
      for (JsonNode m : service.path("methods")) {
        ((ObjectNode) m).put("originalPath", "/" + service.get("original").asText() + "/" + m.path("name").asText());
      }
      services.add(service);
    }
    return out;
  }
}
