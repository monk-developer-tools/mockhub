package io.mockhub.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.EnumDescriptorProto;
import com.google.protobuf.DescriptorProtos.EnumOptions;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.DescriptorProtos.MessageOptions;
import com.google.protobuf.DescriptorProtos.ServiceDescriptorProto;
import com.google.protobuf.Descriptors.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

class ProtoParserTest {

  private static byte[] resource(String name) throws IOException {
    try (InputStream in = ProtoParserTest.class.getResourceAsStream("/" + name)) {
      return in.readAllBytes();
    }
  }

  private static FileDescriptorProto fromProtoc(String dsc, String protoName) throws IOException {
    return FileDescriptorSet.parseFrom(resource(dsc)).getFileList().stream()
        .filter(f -> f.getName().endsWith(protoName)).findFirst().orElseThrow();
  }

  private static FileDescriptorProto normal(FileDescriptorProto file) {
    FileDescriptorProto.Builder b = file.toBuilder().clearName().clearOptions().clearSourceCodeInfo();
    List<DescriptorProto> messages = b.getMessageTypeList().stream().map(ProtoParserTest::normal).toList();
    List<EnumDescriptorProto> enums = b.getEnumTypeList().stream().map(ProtoParserTest::normal).toList();
    List<ServiceDescriptorProto> services = b.getServiceList().stream().map(s -> {
      ServiceDescriptorProto.Builder sb = s.toBuilder().clearOptions();
      sb.getMethodBuilderList().forEach(m -> m.clearOptions());
      return sb.build();
    }).toList();
    return b.clearMessageType().addAllMessageType(messages).clearEnumType().addAllEnumType(enums)
        .clearService().addAllService(services).build();
  }

  private static DescriptorProto normal(DescriptorProto message) {
    DescriptorProto.Builder b = message.toBuilder().clearReservedName().clearReservedRange();
    if (!message.getOptions().getMapEntry()) b.clearOptions();
    else b.setOptions(MessageOptions.newBuilder().setMapEntry(true));
    b.getFieldBuilderList().forEach(f -> f.clearJsonName().clearOptions());
    List<DescriptorProto> nested = b.getNestedTypeList().stream().map(ProtoParserTest::normal).toList();
    List<EnumDescriptorProto> enums = b.getEnumTypeList().stream().map(ProtoParserTest::normal).toList();
    return b.clearNestedType().addAllNestedType(nested).clearEnumType().addAllEnumType(enums).build();
  }

  private static EnumDescriptorProto normal(EnumDescriptorProto e) {
    EnumDescriptorProto.Builder b = e.toBuilder().clearReservedName().clearReservedRange();
    if (e.getOptions().getAllowAlias()) b.setOptions(EnumOptions.newBuilder().setAllowAlias(true));
    else b.clearOptions();
    b.getValueBuilderList().forEach(v -> v.clearOptions());
    return b.build();
  }

  @ParameterizedTest
  @ValueSource(strings = {"library", "chat", "other", "rich"})
  void buildsTheSameDescriptorAsProtoc(String name) throws IOException {
    // Given
    String text = new String(resource("grpc/" + name + ".proto"), StandardCharsets.UTF_8);
    FileDescriptorProto expected = fromProtoc("grpc/" + name + ".dsc", name + ".proto");

    // When
    FileDescriptorProto parsed = ProtoParser.parse(text, name + ".proto", "");

    // Then
    assertThat(normal(parsed)).isEqualTo(normal(expected));
  }

  @Test
  void keepsAnExplicitJsonNameAndBuildsAWorkingDescriptor() throws IOException {
    // Given
    String text = new String(resource("grpc/rich.proto"), StandardCharsets.UTF_8);

    // When
    byte[] bytes = ProtoFiles.compile(text, "rich.proto", "");
    FileDescriptor file = ProtoFiles.parse(bytes).get(0);

    // Then
    assertThat(file.findMessageTypeByName("Order").findFieldByName("id").getJsonName()).isEqualTo("orderId");
    assertThat(file.findMessageTypeByName("Order").findFieldByName("labels").isMapField()).isTrue();
    assertThat(file.findMessageTypeByName("Order").findFieldByName("note").hasPresence()).isTrue();
    assertThat(file.findServiceByName("OrderService").findMethodByName("Chat").isClientStreaming()).isTrue();
  }

  @Test
  void putsEverythingUnderAPackagePrefixSoTwoMocksCanServeTheSameService() {
    // Given
    String text = "syntax = \"proto3\";\npackage helloworld;\nmessage HelloRequest { string name = 1; }\n"
        + "message HelloReply { string message = 1; .helloworld.HelloRequest echo = 2; }\n"
        + "service Greeter { rpc SayHello (HelloRequest) returns (HelloReply); }\n";

    // When
    FileDescriptor first = ProtoFiles.parse(ProtoFiles.compile(text, "hello.proto", "m_abc")).get(0);
    FileDescriptor second = ProtoFiles.parse(ProtoFiles.compile(text, "hello.proto", "m_xyz")).get(0);

    // Then
    assertThat(first.getName()).isEqualTo("m_abc/hello.proto");
    assertThat(first.getServices().get(0).getFullName()).isEqualTo("m_abc.helloworld.Greeter");
    assertThat(second.getServices().get(0).getFullName()).isEqualTo("m_xyz.helloworld.Greeter");
    assertThat(first.findMessageTypeByName("HelloReply").findFieldByName("echo").getMessageType().getFullName())
        .isEqualTo("m_abc.helloworld.HelloRequest");
  }

  @Test
  void acceptsAFileWithoutAPackageAndImportsItCannotRead() {
    // Given
    String text = "syntax = \"proto3\";\nimport \"google/api/annotations.proto\";\nmessage Ping { string text = 1; }\n"
        + "service PingService { rpc Echo (Ping) returns (Ping) { option (google.api.http) = { post: \"/ping\" body: \"*\" }; } }\n";

    // When
    FileDescriptor file = ProtoFiles.parse(ProtoFiles.compile(text, "ping.proto", "m_q1")).get(0);

    // Then
    assertThat(file.getServices().get(0).getFullName()).isEqualTo("m_q1.PingService");
    assertThat(file.getDependencies()).isEmpty();
  }

  @Test
  void explainsMistakesWithTheLineNumber() {
    assertThatThrownBy(() -> ProtoParser.parse("syntax = \"proto3\";\nmessage A {\n  Missing b = 1;\n}\n", "a.proto", ""))
        .hasMessage("Line 3: Unknown type Missing. Define it in this file; only the google/protobuf types can be imported");
    assertThatThrownBy(() -> ProtoParser.parse("syntax = \"proto3\";\nmessage A {\n  string b = ;\n}\n", "a.proto", ""))
        .hasMessage("Line 3: Expected a number but found ';'");
    assertThatThrownBy(() -> ProtoParser.parse("syntax = \"proto3\";\nmessage A {\n  string b = 1;\n", "a.proto", ""))
        .hasMessageContaining("Message A is never closed");
    assertThatThrownBy(() -> ProtoParser.parse("edition = \"2023\";\n", "a.proto", "")).hasMessageContaining("editions aren't supported");
    assertThatThrownBy(() -> ProtoParser.parse("syntax = \"proto3\";\nenum E { A = 1; }\n", "a.proto", "")).hasMessageContaining("must be 0");
    assertThatThrownBy(() -> ProtoFiles.compile("syntax = \"proto3\";\nmessage A { string b = 1; int32 c = 1; }\n", "a.proto", ""))
        .hasMessageContaining("Invalid a.proto");
  }

  @Test
  void refusesFilesThatAreTooLargeOrTooDeep() {
    // Given
    String deep = "syntax = \"proto3\";\n" + "message A { ".repeat(40) + "}".repeat(40);

    // Then
    assertThatThrownBy(() -> ProtoParser.parse("x".repeat(ProtoParser.MAX_CHARS + 1), "a.proto", "")).hasMessageContaining("at most 256 KB");
    assertThatThrownBy(() -> ProtoParser.parse(deep, "a.proto", "")).hasMessageContaining("nested too deeply");
  }
}
