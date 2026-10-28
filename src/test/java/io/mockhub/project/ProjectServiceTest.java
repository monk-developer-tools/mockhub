package io.mockhub.project;

import static io.mockhub.support.Fixtures.tree;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.mockhub.api.ApiException;
import io.mockhub.support.InMemoryProjectRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProjectServiceTest {

  private final InMemoryProjectRepository repository = new InMemoryProjectRepository();
  private final List<String> events = new ArrayList<>();
  private ProjectService service;

  @BeforeEach
  void setUp() {
    ProjectListener recorder = new ProjectListener() {
      @Override
      public void onBasePathChanged(Project before, Project after) {
        events.add("moved " + before.slug() + "->" + after.slug());
      }

      @Override
      public void onProjectDeleted(Project project) {
        events.add("deleted " + project.slug());
      }
    };
    Clock clock = Clock.fixed(Instant.parse("2024-05-01T10:00:00Z"), ZoneOffset.UTC);
    service = new ProjectService(repository, List.of(recorder), clock);
  }

  @Test
  void derivesTheBasePathFromTheNameWhenNoneIsGiven() {
    // When
    Project project = service.create(tree("{'orgId':'org1','name':'Payment Service v2!'}"));

    // Then
    assertThat(project.slug()).isEqualTo("payment-service-v2");
    assertThat(project.basePath()).isEqualTo("/payment-service-v2");
    assertThat(project.createdAt()).isEqualTo("2024-05-01T10:00:00Z");
    assertThat(repository.findById(project.id())).contains(project);
  }

  @Test
  void rejectsADuplicateBasePath() {
    // Given
    service.create(tree("{'orgId':'org1','name':'Orders','slug':'orders'}"));

    // When / Then
    assertThatThrownBy(() -> service.create(tree("{'orgId':'org1','name':'Orders 2','slug':'orders'}")))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("already used")
        .extracting("status").isEqualTo(409);
  }

  @Test
  void rejectsInvalidAndReservedBasePaths() {
    assertThatThrownBy(() -> service.create(tree("{'orgId':'org1','name':'x','slug':'Has Spaces'}"))).hasMessageContaining("lowercase");
    assertThatThrownBy(() -> service.create(tree("{'orgId':'org1','name':'x','slug':'__admin'}"))).hasMessageContaining("lowercase");
    assertThatThrownBy(() -> service.create(tree("{'orgId':'org1','name':'x','slug':'mappings'}"))).hasMessageContaining("reserved");
  }

  @Test
  void requiresAName() {
    assertThatThrownBy(() -> service.create(tree("{'slug':'abc'}"))).hasMessageContaining("'name' is required");
  }

  @Test
  void notifiesListenersWhenTheBasePathChanges() {
    // Given
    Project project = service.create(tree("{'orgId':'org1','name':'Orders','slug':'orders'}"));

    // When
    Project updated = service.update(project.id(), tree("{'slug':'orders-v2'}"));

    // Then
    assertThat(updated.slug()).isEqualTo("orders-v2");
    assertThat(updated.name()).isEqualTo("Orders");
    assertThat(events).containsExactly("moved orders->orders-v2");
  }

  @Test
  void doesNotNotifyWhenOnlyTheNameChanges() {
    // Given
    Project project = service.create(tree("{'orgId':'org1','name':'Orders','slug':'orders'}"));

    // When
    service.update(project.id(), tree("{'name':'Order Service'}"));

    // Then
    assertThat(events).isEmpty();
  }

  @Test
  void deletingNotifiesListenersAndRemovesTheProject() {
    // Given
    Project project = service.create(tree("{'orgId':'org1','name':'Orders','slug':'orders'}"));

    // When
    service.delete(project.id());

    // Then
    assertThat(events).containsExactly("deleted orders");
    assertThat(repository.findById(project.id())).isEmpty();
  }

  @Test
  void unknownProjectIsNotFound() {
    assertThatThrownBy(() -> service.get("nope")).extracting("status").isEqualTo(404);
  }

  @Test
  void aProjectCanAnswerAtTheRoot() {
    // When
    Project legacy = service.create(tree("{'orgId':'org1','name':'Legacy','slug':'legacy','root':true}"));

    // Then
    assertThat(legacy.basePath()).isEmpty();
    assertThat(service.findRoot()).contains(legacy);
  }

  @Test
  void onlyOneProjectCanAnswerAtTheRoot() {
    // Given
    service.create(tree("{'orgId':'org1','name':'Legacy','root':true}"));
    Project other = service.create(tree("{'orgId':'org1','name':'Other'}"));

    // When / Then
    assertThatThrownBy(() -> service.update(other.id(), tree("{'root':true}")))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("already answers at the root");
  }

  @Test
  void movingAProjectToTheRootTellsListenersTheBasePathChanged() {
    // Given
    Project orders = service.create(tree("{'orgId':'org1','name':'Orders','slug':'orders'}"));

    // When
    Project moved = service.update(orders.id(), tree("{'root':true}"));

    // Then
    assertThat(moved.basePath()).isEmpty();
    assertThat(events).containsExactly("moved orders->orders");
  }

  @Test
  void pathsThatNoOtherProjectOwnsBelongToTheRootProject() {
    // Given
    Project legacy = service.create(tree("{'orgId':'org1','name':'Legacy','slug':'legacy','root':true}"));
    Project orders = service.create(tree("{'orgId':'org1','name':'Orders','slug':'orders'}"));

    // When / Then
    assertThat(service.findForPath("orders")).contains(orders);
    assertThat(service.findForPath("customers")).contains(legacy);
    assertThat(service.findForPath("legacy")).contains(legacy);
  }
}
