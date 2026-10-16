package io.mockhub.team;

import static io.mockhub.support.Fixtures.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.mockhub.org.Organization;
import io.mockhub.support.InMemoryTeamRepository;
import io.mockhub.support.MutableClock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TeamServiceTest {

  private final List<Team> deleted = new ArrayList<>();
  private final TeamService teams = new TeamService(new InMemoryTeamRepository(), List.of(deleted::add),
      new MutableClock(Instant.parse("2026-10-16T10:00:00Z")));

  @Test
  void createsOneDefaultTeamPerOrganisation() {
    // When
    Team first = teams.ensureDefault("o1");
    Team again = teams.ensureDefault("o1");
    Team other = teams.ensureDefault("o2");

    // Then
    assertThat(first.name()).isEqualTo("General");
    assertThat(again.id()).isEqualTo(first.id());
    assertThat(other.id()).isNotEqualTo(first.id());
    assertThat(teams.findByOrg("o1")).hasSize(1);
  }

  @Test
  void keepsSlugsUniqueWithinAnOrganisationOnly() {
    // When
    Team a = teams.create("o1", json("{'name':'Checkout'}"));
    Team b = teams.create("o1", json("{'name':'Checkout'}"));
    Team c = teams.create("o2", json("{'name':'Checkout'}"));

    // Then
    assertThat(a.slug()).isEqualTo("checkout");
    assertThat(b.slug()).isEqualTo("checkout-2");
    assertThat(c.slug()).isEqualTo("checkout");
    assertThatThrownBy(() -> teams.update(b.id(), json("{'slug':'checkout'}"))).hasMessageContaining("already has a team");
    assertThatThrownBy(() -> teams.create("o1", json("{'name':' '}"))).hasMessageContaining("required");
  }

  @Test
  void findsTeamsOnlyInsideTheirOrganisation() {
    // Given
    Team a = teams.create("o1", json("{'name':'Checkout'}"));

    // Then
    assertThat(teams.getInOrg("o1", a.id()).name()).isEqualTo("Checkout");
    assertThatThrownBy(() -> teams.getInOrg("o2", a.id())).hasMessageContaining("not found");
  }

  @Test
  void deletesTeamsOfADeletedOrganisationAndTellsListeners() {
    // Given
    Team a = teams.create("o1", json("{'name':'Checkout'}"));
    teams.create("o2", json("{'name':'Search'}"));

    // When
    teams.onOrganizationDeleted(new Organization("o1", "One", "one", "", "", ""));

    // Then
    assertThat(teams.findByOrg("o1")).isEmpty();
    assertThat(teams.findByOrg("o2")).hasSize(1);
    assertThat(deleted).extracting(Team::id).containsExactly(a.id());
  }

  @Test
  void restoresTeamsWithAFreeSlug() {
    // Given
    teams.create("o1", json("{'name':'Checkout'}"));

    // When
    Team restored = teams.restore(new Team("t9", "o1", "Checkout", "checkout", "", "", ""));

    // Then
    assertThat(restored.slug()).isEqualTo("checkout-2");
    assertThat(teams.get("t9").name()).isEqualTo("Checkout");
  }
}
