package io.mockhub.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.api.ApiException;
import io.mockhub.api.ApiRouter;
import io.mockhub.api.Ctx;
import io.mockhub.api.Jsons;
import io.mockhub.auth.Role;
import io.mockhub.org.OrganizationService;
import io.mockhub.sso.RoleMapping;
import io.mockhub.sso.SsoException;
import io.mockhub.sso.SsoService;
import io.mockhub.sso.SsoSettings;
import io.mockhub.sso.SsoSettingsStore;
import java.util.ArrayList;
import java.util.List;

public final class SsoController implements ApiController {
  private final SsoSettingsStore store;
  private final SsoService sso;
  private final OrganizationService orgs;
  private final BaseUrls urls;

  public SsoController(SsoSettingsStore store, SsoService sso, OrganizationService orgs, BaseUrls urls) {
    this.store = store;
    this.sso = sso;
    this.orgs = orgs;
    this.urls = urls;
  }

  @Override
  public void register(ApiRouter r) {
    r.get("/sso", this::view);
    r.put("/sso", ctx -> {
      SsoSettings next = parse(ctx.json(), store.get());
      if (next.enabled() && !next.configured()) {
        throw ApiException.badRequest("Enter the tenant ID, client ID and client secret before turning single sign-on on");
      }
      store.save(next);
      return view(ctx);
    });
    r.post("/sso/test", ctx -> {
      SsoSettings candidate = parse(ctx.json(), store.get());
      try {
        ObjectNode out = (ObjectNode) sso.test(candidate);
        out.put("ok", true);
        return out;
      } catch (SsoException e) {
        return Jsons.object().put("ok", false).put("error", e.getMessage());
      }
    });
  }

  private ObjectNode view(Ctx ctx) {
    SsoSettings s = store.get();
    ObjectNode node = Jsons.MAPPER.valueToTree(s);
    node.remove("clientSecret");
    node.put("clientSecretSet", !s.clientSecret().isEmpty());
    node.put("clientSecretFromEnvironment", store.secretFromEnvironment());
    node.put("redirectUri", urls.server(ctx) + AuthController.callbackPath(s.provider()));
    node.put("active", s.active());
    return node;
  }

  SsoSettings parse(JsonNode body, SsoSettings current) {
    String provider = body.path("provider").asText(current.provider());
    if (!sso.supports(provider)) throw ApiException.badRequest("Unknown sign-in provider '" + provider + "'");
    String secret = body.path("clientSecret").asText("");
    List<RoleMapping> mappings = new ArrayList<>();
    for (JsonNode m : body.path("roleMappings")) {
      String claim = m.path("claim").asText("").trim();
      if (claim.isEmpty()) continue;
      String orgId = m.path("orgId").asText("");
      Role role = orgId.isBlank() ? Role.serverRole(m.path("role").asText()) : Role.orgRole(m.path("role").asText());
      if (!orgId.isBlank() && !orgs.exists(orgId)) throw ApiException.badRequest("Unknown organisation in mapping for " + claim);
      mappings.add(new RoleMapping(claim, orgId, role));
    }
    String defaultOrgId = body.path("defaultOrgId").asText(current.defaultOrgId());
    if (!defaultOrgId.isBlank() && !orgs.exists(defaultOrgId)) throw ApiException.badRequest("Unknown default organisation");
    String defaultOrgRole = body.path("defaultOrgRole").asText(current.defaultOrgRole().id());
    List<String> domains = body.has("allowedDomains")
        ? Jsons.MAPPER.convertValue(body.get("allowedDomains"), new TypeReference<List<String>>() {}).stream()
            .map(String::trim).filter(d -> !d.isEmpty()).map(d -> d.startsWith("@") ? d.substring(1) : d).toList()
        : current.allowedDomains();
    return new SsoSettings(
        body.path("enabled").asBoolean(current.enabled()),
        provider,
        body.path("tenantId").asText(current.tenantId()),
        body.path("clientId").asText(current.clientId()),
        secret.isEmpty() ? current.clientSecret() : secret,
        body.path("cloud").asText(current.cloud()),
        body.path("buttonLabel").asText(current.buttonLabel()),
        domains,
        Role.serverRole(body.path("defaultServerRole").asText(current.defaultServerRole().id())),
        defaultOrgId,
        defaultOrgRole.isBlank() || defaultOrgRole.equals("none") ? Role.NONE : Role.orgRole(defaultOrgRole),
        body.has("roleMappings") ? mappings : current.roleMappings(),
        body.path("allowPasswordLogin").asBoolean(current.allowPasswordLogin()));
  }
}
