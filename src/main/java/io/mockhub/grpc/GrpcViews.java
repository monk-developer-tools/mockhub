package io.mockhub.grpc;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.protobuf.Descriptors.MethodDescriptor;
import com.google.protobuf.Descriptors.ServiceDescriptor;
import java.util.List;

public final class GrpcViews {
  private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

  private GrpcViews() {}

  public static ObjectNode project(List<GrpcCatalog.DescriptorSet> sets) {
    ObjectNode out = JSON.objectNode();
    ArrayNode descriptors = out.putArray("descriptors");
    ArrayNode services = out.putArray("services");
    for (GrpcCatalog.DescriptorSet set : sets) {
      ObjectNode d = descriptors.addObject().put("name", set.name()).put("size", set.size());
      ArrayNode files = d.putArray("files");
      set.files().forEach(f -> files.add(f.getName()));
      ArrayNode names = d.putArray("services");
      for (ServiceDescriptor service : set.services()) {
        names.add(service.getFullName());
        services.add(service(service, set.name()));
      }
    }
    return out;
  }

  static ObjectNode service(ServiceDescriptor service, String descriptor) {
    ObjectNode s = JSON.objectNode().put("name", service.getFullName()).put("descriptor", descriptor);
    ArrayNode methods = s.putArray("methods");
    for (MethodDescriptor m : service.getMethods()) {
      ObjectNode method = methods.addObject()
          .put("name", m.getName())
          .put("path", "/" + service.getFullName() + "/" + m.getName())
          .put("kind", kind(m))
          .put("requestType", m.getInputType().getFullName())
          .put("responseType", m.getOutputType().getFullName());
      method.set("requestExample", MessageExamples.of(m.getInputType()));
      method.set("responseExample", MessageExamples.of(m.getOutputType()));
    }
    return s;
  }

  static String kind(MethodDescriptor m) {
    if (m.isClientStreaming() && m.isServerStreaming()) return "bidi-streaming";
    if (m.isClientStreaming()) return "client-streaming";
    if (m.isServerStreaming()) return "server-streaming";
    return "unary";
  }
}
