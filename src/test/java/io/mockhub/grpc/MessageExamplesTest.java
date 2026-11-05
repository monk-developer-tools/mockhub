package io.mockhub.grpc;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import org.junit.jupiter.api.Test;

class MessageExamplesTest {

  @Test
  void fillsEveryFieldWithAValueOfItsTypeUnderItsJsonName() {
    // Given
    Descriptor book = ProtoFiles.parse(Descriptors.LIBRARY).get(1).findMessageTypeByName("Book");

    // When
    ObjectNode example = MessageExamples.of(book);

    // Then
    assertThat(example.get("id").asText()).isEqualTo("42");
    assertThat(example.get("title").asText()).isEqualTo("Notes on the Analytical Engine");
    assertThat(example.get("year").asInt()).isEqualTo(1);
    assertThat(example.get("tags").isArray()).isTrue();
    assertThat(example.get("genre").asText()).isEqualTo("FICTION");
    assertThat(example.get("publishedAt").asText()).isEqualTo("2026-01-01T00:00:00Z");
  }

  @Test
  void stopsAtRecursiveMessagesAndWritesMapsAsObjects() throws Exception {
    // Given
    FileDescriptorProto proto = FileDescriptorProto.newBuilder().setName("tree.proto").setPackage("t").setSyntax("proto3")
        .addMessageType(DescriptorProto.newBuilder().setName("Node")
            .addField(field("label", 1, FieldDescriptorProto.Type.TYPE_STRING, null, false))
            .addField(field("children", 2, FieldDescriptorProto.Type.TYPE_MESSAGE, ".t.Node", true))
            .addField(field("parent", 3, FieldDescriptorProto.Type.TYPE_MESSAGE, ".t.Node", false))
            .addField(field("size", 4, FieldDescriptorProto.Type.TYPE_INT64, null, false)))
        .build();
    Descriptor node = FileDescriptor.buildFrom(proto, new FileDescriptor[0]).findMessageTypeByName("Node");

    // When
    ObjectNode example = MessageExamples.of(node);

    // Then
    assertThat(example.get("label").asText()).isEqualTo("example");
    assertThat(example.has("parent")).isFalse();
    assertThat(example.has("children")).isFalse();
    assertThat(example.get("size").asText()).isEqualTo("1");
  }

  private static FieldDescriptorProto field(String name, int number, FieldDescriptorProto.Type type, String typeName, boolean repeated) {
    FieldDescriptorProto.Builder b = FieldDescriptorProto.newBuilder().setName(name).setNumber(number).setType(type)
        .setLabel(repeated ? FieldDescriptorProto.Label.LABEL_REPEATED : FieldDescriptorProto.Label.LABEL_OPTIONAL);
    if (typeName != null) b.setTypeName(typeName);
    return b.build();
  }
}
