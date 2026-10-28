package io.mockhub.sso;

import static org.assertj.core.api.Assertions.assertThat;

import io.mockhub.auth.Role;
import io.mockhub.user.Membership;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RoleMapperTest {

  private final RoleMapper mapper = new RoleMapper(Set.of("o1", "o2")::contains);

  private static SsoSettings settings(List<String> domains, List<RoleMapping> mappings) {
    return new SsoSettings(true, "azure", "t", "c", "s", "global", null, domains, Role.VIEWER, "o1", Role.VIEWER, mappings, true);
  }

  private static ExternalIdentity identity(String email, List<String> groups, List<String> roles) {
    return new ExternalIdentity("oid", email, email, "Ana", "t", groups, roles, false);
  }

  @Test
  void appliesDefaultsAndTheHighestMatchingMappings() {
    // Given
    SsoSettings s = settings(List.of(), List.of(
        new RoleMapping("g-dev", "o1", Role.EDITOR),
        new RoleMapping("g-lead", "o1", Role.MANAGER),
        new RoleMapping("MockHub.Admin", "", Role.ADMIN),
        new RoleMapping("g-other", "o2", Role.EDITOR),
        new RoleMapping("g-dev", "gone", Role.EDITOR)));

    // When
    RoleMapper.Result result = mapper.map(s, identity("ana@contoso.com", List.of("G-DEV", "g-lead"), List.of("mockhub.admin")));

    // Then
    assertThat(result.serverRole()).isEqualTo(Role.ADMIN);
    assertThat(result.memberships()).containsExactly(new Membership("o1", Role.MANAGER));
  }

  @Test
  void usersWithoutMatchingGroupsGetOnlyTheDefaults() {
    // When
    RoleMapper.Result result = mapper.map(settings(List.of(), List.of(new RoleMapping("g-dev", "o2", Role.EDITOR))),
        identity("ana@contoso.com", List.of(), List.of()));

    // Then
    assertThat(result.serverRole()).isEqualTo(Role.VIEWER);
    assertThat(result.memberships()).containsExactly(new Membership("o1", Role.VIEWER));
  }

  @Test
  void restrictsSignInToTheAllowedDomains() {
    // Given
    SsoSettings s = settings(List.of("contoso.com"), List.of());

    // Then
    assertThat(RoleMapper.domainAllowed(s, identity("ana@Contoso.com", List.of(), List.of()))).isTrue();
    assertThat(RoleMapper.domainAllowed(s, identity("eve@fabrikam.com", List.of(), List.of()))).isFalse();
    assertThat(RoleMapper.domainAllowed(s, identity("no-at-sign", List.of(), List.of()))).isFalse();
    assertThat(RoleMapper.domainAllowed(settings(List.of(), List.of()), identity("eve@fabrikam.com", List.of(), List.of()))).isTrue();
  }

  @Test
  void mapsGroupsToTeamsInsideKnownOrganisations() {
    // Given
    RoleMapper teamAware = new RoleMapper(Set.of("o1")::contains, team -> team.equals("t1") ? java.util.Optional.of("o1") : java.util.Optional.empty());
    SsoSettings s = settings(List.of(), List.of(
        new RoleMapping("g-checkout", "o1", Role.EDITOR, "t1"),
        new RoleMapping("g-checkout-leads", "o1", Role.MANAGER, "t1"),
        new RoleMapping("g-gone", "o1", Role.EDITOR, "t-gone")));

    // When
    RoleMapper.Result result = teamAware.map(s, identity("ana@contoso.com", List.of("g-checkout", "g-checkout-leads", "g-gone"), List.of()));

    // Then
    assertThat(result.teamMemberships()).containsExactly(new io.mockhub.user.TeamMembership("t1", "o1", Role.MANAGER));
    assertThat(result.memberships()).containsExactly(new Membership("o1", Role.VIEWER));
  }
}
