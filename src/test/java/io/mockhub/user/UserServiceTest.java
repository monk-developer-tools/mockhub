package io.mockhub.user;

import static io.mockhub.support.Fixtures.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.mockhub.Config.AuthUser;
import io.mockhub.auth.Role;
import io.mockhub.org.Organization;
import io.mockhub.support.InMemoryUserRepository;
import io.mockhub.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class UserServiceTest {

  private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
  private final InMemoryUserRepository repository = new InMemoryUserRepository();
  private final UserService users = new UserService(repository, List.of(new AuthUser("root", "pw", "admin")), new PasswordHasher(1000), clock);

  @Test
  void createsLocalUsersWithHashedPasswords() {
    // When
    User ana = users.create(json("{'username':'ana','password':'longenough','serverRole':'viewer','memberships':[{'orgId':'o1','role':'editor'}]}"));

    // Then
    assertThat(ana.source()).isEqualTo(UserSource.LOCAL);
    assertThat(ana.passwordHash()).startsWith("pbkdf2:");
    assertThat(ana.roleIn("o1")).isEqualTo(Role.EDITOR);
    assertThat(ana.roleIn("o2")).isEqualTo(Role.VIEWER);
    assertThat(users.authenticate("ANA", "longenough")).isPresent();
    assertThat(users.authenticate("ana", "wrong")).isEmpty();
  }

  @Test
  void rejectsDuplicatesShortPasswordsAndBadNames() {
    // Given
    users.create(json("{'username':'ana','password':'longenough'}"));

    // Then
    assertThatThrownBy(() -> users.create(json("{'username':'Ana','password':'longenough'}"))).hasMessageContaining("taken");
    assertThatThrownBy(() -> users.create(json("{'username':'root','password':'longenough'}"))).hasMessageContaining("taken");
    assertThatThrownBy(() -> users.create(json("{'username':'bob','password':'short'}"))).hasMessageContaining("8 characters");
    assertThatThrownBy(() -> users.create(json("{'username':'has space','password':'longenough'}"))).hasMessageContaining("Username");
  }

  @Test
  void keepsEnvironmentUsersReadOnly() {
    // Given
    User root = users.findByUsername("root").orElseThrow();

    // Then
    assertThat(root.source()).isEqualTo(UserSource.ENV);
    assertThat(root.effectiveServerRole()).isEqualTo(Role.ADMIN);
    assertThat(users.authenticate("root", "pw")).isPresent();
    assertThatThrownBy(() -> users.delete(root.id())).hasMessageContaining("MOCKHUB_USERS");
    assertThatThrownBy(() -> users.setMembership(root.id(), "o1", Role.EDITOR)).hasMessageContaining("MOCKHUB_USERS");
  }

  @Test
  void passwordChangesAndDisablingEndExistingSessions() {
    // Given
    User ana = users.create(json("{'username':'ana','password':'longenough'}"));
    clock.advance(Duration.ofMinutes(5));

    // When
    User changed = users.setPassword(ana.id(), "evenlonger");
    clock.advance(Duration.ofMinutes(5));
    User disabled = users.update(ana.id(), json("{'disabled':true}"));

    // Then
    assertThat(changed.sessionEpoch()).isEqualTo(clock.millis() - Duration.ofMinutes(5).toMillis());
    assertThat(disabled.sessionEpoch()).isEqualTo(clock.millis());
    assertThat(users.authenticate("ana", "evenlonger")).isEmpty();
  }

  @Test
  void changingYourOwnPasswordNeedsTheCurrentOne() {
    // Given
    User ana = users.create(json("{'username':'ana','password':'longenough'}"));

    // Then
    assertThatThrownBy(() -> users.changeOwnPassword(ana.id(), "nope", "brandnewpw")).hasMessageContaining("current password");
    users.changeOwnPassword(ana.id(), "longenough", "brandnewpw");
    assertThat(users.authenticate("ana", "brandnewpw")).isPresent();
  }

  @Test
  void externalSignInsKeepManualMembershipsAndRefreshGroupRoles() {
    // Given
    User first = users.upsertExternal(new ExternalProfile(UserSource.AZURE, "t:oid", "Ana@Contoso.com", "Ana", "ana@contoso.com",
        Role.NONE, List.of(new Membership("o1", Role.VIEWER))));
    users.setMembership(first.id(), "o2", Role.MANAGER);

    // When
    User second = users.upsertExternal(new ExternalProfile(UserSource.AZURE, "t:oid", "ana@contoso.com", "Ana T", "ana@contoso.com",
        Role.EDITOR, List.of()));

    // Then
    assertThat(second.id()).isEqualTo(first.id());
    assertThat(second.username()).isEqualTo("ana@contoso.com");
    assertThat(second.displayName()).isEqualTo("Ana T");
    assertThat(second.roleIn("o1")).isEqualTo(Role.EDITOR);
    assertThat(second.roleIn("o2")).isEqualTo(Role.MANAGER);
    assertThat(second.passwordHash()).isNull();
    assertThat(users.authenticate("ana@contoso.com", "")).isEmpty();
  }

  @Test
  void externalUsersGetAFreeUsername() {
    // Given
    users.create(json("{'username':'ana@contoso.com','password':'longenough'}"));

    // When
    User external = users.upsertExternal(new ExternalProfile(UserSource.AZURE, "t:oid", "ana@contoso.com", "Ana", "", Role.NONE, List.of()));

    // Then
    assertThat(external.username()).isEqualTo("ana@contoso.com-2");
  }

  @Test
  void deletingAnOrganisationRemovesItsMemberships() {
    // Given
    User ana = users.create(json("{'username':'ana','password':'longenough','memberships':[{'orgId':'o1','role':'owner'},{'orgId':'o2','role':'viewer'}]}"));

    // When
    users.onOrganizationDeleted(new Organization("o1", "One", "one", "", "", ""));

    // Then
    assertThat(users.get(ana.id()).memberships()).extracting(Membership::orgId).containsExactly("o2");
    assertThat(users.membersOf("o1")).isEmpty();
    assertThat(users.membersOf("o2")).extracting(User::username).containsExactly("ana");
  }

  @Test
  void givesPerTeamRolesOnTopOfOrganisationWideRoles() {
    // Given
    User ana = users.create(json("{'username':'ana','password':'longenough','memberships':[{'orgId':'o1','role':'viewer'}],"
        + "'teamMemberships':[{'teamId':'t1','orgId':'o1','role':'editor'}]}"));

    // When
    User owner = users.setTeamMembership(ana.id(), "t2", "o1", Role.MANAGER);

    // Then
    assertThat(owner.roleInTeam("o1", "t1")).isEqualTo(Role.EDITOR);
    assertThat(owner.roleInTeam("o1", "t2")).isEqualTo(Role.MANAGER);
    assertThat(owner.roleInTeam("o1", "t3")).isEqualTo(Role.VIEWER);
    assertThat(users.teamMembersOf("t2")).extracting(User::username).containsExactly("ana");
    assertThat(users.removeTeamMembership(ana.id(), "t2").inTeam("t2")).isFalse();
  }

  @Test
  void teamOnlyMembersBelongToTheOrganisationAndLoseAccessWhenTheTeamGoes() {
    // Given
    User bo = users.create(json("{'username':'bo','password':'longenough','teamMemberships':[{'teamId':'t1','orgId':'o1','role':'viewer'}]}"));
    List<User> before = users.membersOf("o1");

    // When
    users.onTeamDeleted(new io.mockhub.team.Team("t1", "o1", "Checkout", "checkout", "", "", ""));

    // Then
    assertThat(bo.belongsTo("o1")).isTrue();
    assertThat(bo.roleIn("o1")).isEqualTo(Role.NONE);
    assertThat(before).extracting(User::username).containsExactly("bo");
    assertThat(users.membersOf("o1")).isEmpty();
    assertThat(users.get(bo.id()).belongsTo("o1")).isFalse();
  }

  @Test
  void replacesAccessWhenAnAdminEditsAUser() {
    // Given
    User cy = users.create(json("{'username':'cy','password':'longenough','memberships':[{'orgId':'o1','role':'owner'}]}"));

    // When
    User after = users.update(cy.id(), json("{'memberships':[],'teamMemberships':[{'teamId':'t1','orgId':'o1','role':'editor'}]}"));

    // Then
    assertThat(after.memberships()).isEmpty();
    assertThat(after.roleInTeam("o1", "t1")).isEqualTo(Role.EDITOR);
    assertThatThrownBy(() -> users.update(cy.id(), json("{'teamMemberships':[{'teamId':'t1','orgId':'o1','role':'admin'}]}")))
        .hasMessageContaining("Team role");
  }
}
