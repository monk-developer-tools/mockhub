package io.mockhub.sso;

import io.mockhub.auth.Role;
import java.util.List;

public record SsoSettings(
    boolean enabled,
    String provider,
    String tenantId,
    String clientId,
    String clientSecret,
    String cloud,
    String buttonLabel,
    List<String> allowedDomains,
    Role defaultServerRole,
    String defaultOrgId,
    Role defaultOrgRole,
    List<RoleMapping> roleMappings,
    boolean allowPasswordLogin) {

  public static final String AZURE = "azure";

  public SsoSettings {
    provider = provider == null || provider.isBlank() ? AZURE : provider;
    tenantId = tenantId == null ? "" : tenantId.trim();
    clientId = clientId == null ? "" : clientId.trim();
    clientSecret = clientSecret == null ? "" : clientSecret;
    cloud = cloud == null || cloud.isBlank() ? "global" : cloud;
    buttonLabel = buttonLabel == null || buttonLabel.isBlank() ? "Sign in with Microsoft" : buttonLabel;
    allowedDomains = allowedDomains == null ? List.of() : List.copyOf(allowedDomains);
    defaultServerRole = defaultServerRole == null ? Role.NONE : defaultServerRole;
    defaultOrgId = defaultOrgId == null ? "" : defaultOrgId;
    defaultOrgRole = defaultOrgRole == null ? Role.NONE : defaultOrgRole;
    roleMappings = roleMappings == null ? List.of() : List.copyOf(roleMappings);
  }

  public static SsoSettings disabled() {
    return new SsoSettings(false, AZURE, "", "", "", "global", null, List.of(), Role.NONE, "", Role.NONE, List.of(), true);
  }

  public boolean configured() {
    return !tenantId.isEmpty() && !clientId.isEmpty() && !clientSecret.isEmpty();
  }

  public boolean active() {
    return enabled && configured();
  }

  public SsoSettings withClientSecret(String secret) {
    return new SsoSettings(enabled, provider, tenantId, clientId, secret, cloud, buttonLabel, allowedDomains,
        defaultServerRole, defaultOrgId, defaultOrgRole, roleMappings, allowPasswordLogin);
  }
}
