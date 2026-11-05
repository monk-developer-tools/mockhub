package io.mockhub.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.Descriptors.FileDescriptor;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProtoFilesTest {

  @Test
  void buildsEveryFileOfADescriptorSetIncludingItsImports() {
    // When
    List<FileDescriptor> files = ProtoFiles.parse(Descriptors.LIBRARY);

    // Then
    assertThat(files).extracting(FileDescriptor::getName).containsExactly("google/protobuf/timestamp.proto", "library.proto");
    assertThat(files.get(1).getServices().get(0).getFullName()).isEqualTo("library.v1.BookService");
  }

  @Test
  void resolvesWellKnownTypesThatWereNotIncluded() {
    // When
    List<FileDescriptor> files = ProtoFiles.parse(Descriptors.LIBRARY_WITHOUT_IMPORTS);

    // Then
    assertThat(files).extracting(FileDescriptor::getName).containsExactly("library.proto");
    assertThat(files.get(0).findMessageTypeByName("Book").findFieldByName("published_at").getMessageType().getFullName())
        .isEqualTo("google.protobuf.Timestamp");
  }

  @Test
  void explainsAMissingImport() {
    // Given
    byte[] set = FileDescriptorSet.newBuilder()
        .addFile(FileDescriptorProto.newBuilder().setName("a.proto").addDependency("common/money.proto"))
        .build().toByteArray();

    // When / Then
    assertThatThrownBy(() -> ProtoFiles.parse(set)).isInstanceOf(GrpcException.class)
        .hasMessageContaining("missing common/money.proto").hasMessageContaining("--include_imports");
  }

  @Test
  void rejectsBytesThatAreNotADescriptorSet() {
    // Given
    byte[] text = "syntax = \"proto3\";".getBytes();

    // When / Then
    assertThatThrownBy(() -> ProtoFiles.parse(text)).isInstanceOf(GrpcException.class).hasMessageContaining("not a protobuf descriptor set");
    assertThatThrownBy(() -> ProtoFiles.parse(new byte[0])).isInstanceOf(GrpcException.class).hasMessageContaining("empty");
  }

  @Test
  void rejectsCircularImports() {
    // Given
    byte[] set = FileDescriptorSet.newBuilder()
        .addFile(FileDescriptorProto.newBuilder().setName("a.proto").addDependency("b.proto"))
        .addFile(FileDescriptorProto.newBuilder().setName("b.proto").addDependency("a.proto"))
        .build().toByteArray();

    // When / Then
    assertThatThrownBy(() -> ProtoFiles.parse(set)).isInstanceOf(GrpcException.class).hasMessageContaining("Circular import");
  }
}
