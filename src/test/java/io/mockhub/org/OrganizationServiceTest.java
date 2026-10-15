package io.mockhub.org;

import static io.mockhub.support.Fixtures.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.mockhub.support.InMemoryOrganizationRepository;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrganizationServiceTest {

  private final List<String> deleted = new ArrayList<>();
  private final OrganizationService orgs = new OrganizationService(
      new InMemoryOrganizationRepository(), List.of(o -> deleted.add(o.id())), Clock.systemUTC());

  @Test
  void createsTheDefaultOrganisationOnce() {
    // When
    Organization first = orgs.ensureDefault();
    Organization second = orgs.ensureDefault();

    // Then
    assertThat(first.slug()).isEqualTo("default");
    assertThat(second.id()).isEqualTo(first.id());
    assertThat(orgs.findAll()).hasSize(1);
  }

  @Test
  void derivesUniqueSlugsFromNames() {
    // When
    Organization a = orgs.create(json("{'name':'Payments Team'}"));
    Organization b = orgs.create(json("{'name':'Payments team!'}"));

    // Then
    assertThat(a.slug()).isEqualTo("payments-team");
    assertThat(b.slug()).isEqualTo("payments-team-2");
    assertThatThrownBy(() -> orgs.update(b.id(), json("{'slug':'payments-team'}"))).hasMessageContaining("already used");
    assertThatThrownBy(() -> orgs.create(json("{'name':' '}"))).hasMessageContaining("'name' is required");
  }

  @Test
  void deletingNotifiesListenersButKeepsTheLastOrganisation() {
    // Given
    Organization a = orgs.create(json("{'name':'A'}"));
    Organization b = orgs.create(json("{'name':'B'}"));

    // When
    orgs.delete(a.id());

    // Then
    assertThat(deleted).containsExactly(a.id());
    assertThat(orgs.exists(a.id())).isFalse();
    assertThatThrownBy(() -> orgs.delete(b.id())).hasMessageContaining("at least one");
  }
}
