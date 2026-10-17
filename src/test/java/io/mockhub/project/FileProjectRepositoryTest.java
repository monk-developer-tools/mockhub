package io.mockhub.project;

import static io.mockhub.support.Fixtures.project;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileProjectRepositoryTest {

  @TempDir
  Path root;

  @Test
  void persistsProjectsAcrossInstances() {
    // Given
    new FileProjectRepository(root).save(project("orders"));

    // When
    FileProjectRepository reopened = new FileProjectRepository(root);

    // Then
    assertThat(reopened.findBySlug("orders")).isPresent();
    assertThat(Files.exists(root.resolve("mockhub/projects.json"))).isTrue();
    assertThat(Files.exists(root.resolve("mockhub/projects.json.tmp"))).isFalse();
  }

  @Test
  void saveReplacesTheProjectWithTheSameId() {
    // Given
    FileProjectRepository repository = new FileProjectRepository(root);
    Project original = project("orders");
    repository.save(original);

    // When
    repository.save(new Project(original.id(), "Renamed", "orders", "", "#fff", original.createdAt(), original.updatedAt(), "org1", "team1", null));

    // Then
    assertThat(repository.findAll()).singleElement().extracting(Project::name).isEqualTo("Renamed");
  }

  @Test
  void deleteRemovesTheProjectFromDisk() {
    // Given
    FileProjectRepository repository = new FileProjectRepository(root);
    repository.save(project("orders"));

    // When
    repository.deleteById("p-orders");

    // Then
    assertThat(new FileProjectRepository(root).findAll()).isEmpty();
  }

  @Test
  void findAllIsSortedByName() {
    // Given
    FileProjectRepository repository = new FileProjectRepository(root);
    repository.save(project("zeta"));
    repository.save(project("alpha"));

    // When / Then
    assertThat(repository.findAll()).extracting(Project::slug).containsExactly("alpha", "zeta");
  }
}
