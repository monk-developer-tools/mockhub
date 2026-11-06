package io.mockhub.publictools;

import io.mockhub.auth.Principal;
import io.mockhub.auth.Role;
import io.mockhub.user.User;
import io.mockhub.user.UserSource;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BinTunnelKeys {
  private static final Pattern KEY = Pattern.compile("Bearer bin_([a-z0-9]{12})_([a-z0-9]{32})");

  private final Bins bins;

  public BinTunnelKeys(Bins bins) {
    this.bins = bins;
  }

  public Optional<Principal> principal(String authorization) {
    if (authorization == null) return Optional.empty();
    Matcher m = KEY.matcher(authorization.trim());
    if (!m.matches() || !bins.verify(m.group(1), m.group(2))) return Optional.empty();
    String id = m.group(1);
    return Optional.of(Principal.of(new User.Builder().id(BinResponder.projectKey(id)).username(BinResponder.tunnelName(id))
        .displayName("Webhook bin " + id).source(UserSource.LOCAL).serverRole(Role.EDITOR).build()));
  }

  public static boolean isBinOwner(Principal principal) {
    return principal.id().startsWith("bin:");
  }

  public static String tunnelFor(Principal principal) {
    return BinResponder.tunnelName(principal.id().substring("bin:".length()));
  }
}
