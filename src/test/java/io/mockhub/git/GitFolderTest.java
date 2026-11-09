package io.mockhub.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class GitFolderTest {

  @Test
  void acceptsPathsInsideTheRepositoryAndTheRoot() {
    // When / Then
    assertThat(GitSyncService.folder("/mocks/orders/")).isEqualTo("mocks/orders");
    assertThat(GitSyncService.folder(".")).isEmpty();
    assertThat(GitSyncService.folder("")).isEmpty();
  }

  @Test
  void refusesPathsThatLeaveTheRepository() {
    // When / Then
    assertThatThrownBy(() -> GitSyncService.folder("../other")).hasMessageContaining("inside the repository");
    assertThatThrownBy(() -> GitSyncService.folder("mocks\\orders")).hasMessageContaining("inside the repository");
    assertThatThrownBy(() -> GitSyncService.folder("a//b")).hasMessageContaining("inside the repository");
  }
}
