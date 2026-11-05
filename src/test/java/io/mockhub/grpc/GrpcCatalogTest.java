package io.mockhub.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.protobuf.Descriptors.FileDescriptor;
import io.mockhub.project.Project;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class GrpcCatalogTest {
  private final MemoryDescriptorStore store = new MemoryDescriptorStore();
  private final GrpcCatalog catalog = new GrpcCatalog(store, id -> id.equals("p1") ? "Library" : null);

  @Test
  void storesADescriptorAndServesItsServices() {
    // Given
    AtomicInteger reloads = new AtomicInteger();
    catalog.onChange(reloads::incrementAndGet);

    // When
    catalog.add("p1", "library.dsc", Descriptors.LIBRARY);

    // Then
    assertThat(store.names("p1")).containsExactly("library.dsc");
    assertThat(catalog.ownerOf("library.v1.BookService")).contains("p1");
    assertThat(catalog.fileDescriptors()).extracting(FileDescriptor::getName).containsExactly("google/protobuf/timestamp.proto", "library.proto");
    assertThat(reloads).hasValue(1);
  }

  @Test
  void refusesAServiceAnotherProjectAlreadyServes() {
    // Given
    catalog.add("p1", "library.dsc", Descriptors.LIBRARY);

    // When / Then
    assertThatThrownBy(() -> catalog.add("p2", "copy.dsc", Descriptors.LIBRARY)).isInstanceOf(GrpcException.class)
        .hasMessageContaining("library.v1.BookService is already served by library.dsc in project Library");
    assertThat(store.names("p2")).isEmpty();
  }

  @Test
  void replacesADescriptorUploadedAgainUnderTheSameName() {
    // Given
    catalog.add("p1", "library.dsc", Descriptors.LIBRARY);

    // When
    catalog.add("p1", "library.dsc", Descriptors.LIBRARY_WITHOUT_IMPORTS);

    // Then
    assertThat(catalog.setsOf("p1")).hasSize(1);
    assertThat(catalog.fileDescriptors()).extracting(FileDescriptor::getName).containsExactly("library.proto");
  }

  @Test
  void letsProjectsShareWellKnownImports() {
    // Given
    catalog.add("p1", "library.dsc", Descriptors.LIBRARY);

    // When
    catalog.add("p2", "other.dsc", Descriptors.OTHER);

    // Then
    assertThat(catalog.ownerOf("shop.v1.PingService")).contains("p2");
    assertThat(catalog.fileDescriptors()).hasSize(3);
  }

  @Test
  void forgetsTheDescriptorsOfADeletedProject() {
    // Given
    catalog.add("p1", "library.dsc", Descriptors.LIBRARY);
    Project project = new Project("p1", "Library", "library", "", "", "", "", "o", "t", null, false, null);

    // When
    catalog.onProjectDeleted(project);

    // Then
    assertThat(catalog.ownerOf("library.v1.BookService")).isEmpty();
    assertThat(store.files).doesNotContainKey("p1");
  }

  @Test
  void loadsStoredDescriptorsAndSkipsBrokenOnes() {
    // Given
    store.save("p1", "library.dsc", Descriptors.LIBRARY);
    store.save("p1", "broken.dsc", "nope".getBytes());

    // When
    GrpcCatalog reloaded = new GrpcCatalog(store, id -> null);

    // Then
    assertThat(reloaded.setsOf("p1")).extracting(GrpcCatalog.DescriptorSet::name).containsExactly("library.dsc");
  }

  @Test
  void requiresADescriptorFileNameAndAtLeastOneService() {
    // When / Then
    assertThatThrownBy(() -> catalog.add("p1", "../x.dsc", Descriptors.LIBRARY)).isInstanceOf(GrpcException.class);
    assertThatThrownBy(() -> catalog.add("p1", "library.proto", Descriptors.LIBRARY)).isInstanceOf(GrpcException.class);
    assertThat(List.of(DescriptorNames.isValid("library.dsc"), DescriptorNames.isValid("api.pb"), DescriptorNames.isValid(".dsc")))
        .containsExactly(true, true, false);
  }
}
