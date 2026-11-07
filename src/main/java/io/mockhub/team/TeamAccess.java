package io.mockhub.team;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class TeamAccess {
  private static final Set<String> RESTRICTED = ConcurrentHashMap.newKeySet();

  private TeamAccess() {}

  public static boolean restricted(String teamId) {
    return teamId != null && RESTRICTED.contains(teamId);
  }

  static void track(Team team) {
    if (team.restricted()) RESTRICTED.add(team.id());
    else RESTRICTED.remove(team.id());
  }

  static void forget(String teamId) {
    RESTRICTED.remove(teamId);
  }

  static void trackAll(Collection<Team> teams) {
    teams.forEach(TeamAccess::track);
  }
}
