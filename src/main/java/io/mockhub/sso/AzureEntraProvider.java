package io.mockhub.sso;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.api.Jsons;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class AzureEntraProvider implements SsoProvider {
  private static final Pattern GUID = Pattern.compile("[0-9a-fA-F]{8}-([0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12}");
  private static final Map<String, String> CLOUDS = Map.of(
      "global", "https://login.microsoftonline.com",
      "usgov", "https://login.microsoftonline.us",
      "china", "https://login.chinacloudapi.cn");

  private final HttpTransport http;
  private final JwtVerifier jwt;
  private final String authorityOverride;

  public AzureEntraProvider(HttpTransport http, JwtVerifier jwt, String authorityOverride) {
    this.http = http;
    this.jwt = jwt;
    this.authorityOverride = authorityOverride == null ? "" : authorityOverride.replaceAll("/+$", "");
  }

  @Override
  public String id() {
    return SsoSettings.AZURE;
  }

  String authority(SsoSettings s) {
    if (!authorityOverride.isEmpty()) return authorityOverride;
    String host = CLOUDS.get(s.cloud());
    if (host == null) throw new SsoException("Unknown Microsoft cloud '" + s.cloud() + "'");
    return host;
  }

  @Override
  public String authorizationUrl(SsoSettings s, AuthorizationRequest r) {
    Map<String, String> q = new LinkedHashMap<>();
    q.put("client_id", s.clientId());
    q.put("response_type", "code");
    q.put("redirect_uri", r.redirectUri());
    q.put("response_mode", "query");
    q.put("scope", "openid profile email");
    q.put("state", r.state());
    q.put("nonce", r.nonce());
    q.put("code_challenge", r.codeChallenge());
    q.put("code_challenge_method", "S256");
    q.put("prompt", "select_account");
    return authority(s) + "/" + s.tenantId() + "/oauth2/v2.0/authorize?" + query(q);
  }

  @Override
  public ExternalIdentity exchange(SsoSettings s, String code, AuthorizationRequest r) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("client_id", s.clientId());
    form.put("client_secret", s.clientSecret());
    form.put("grant_type", "authorization_code");
    form.put("code", code);
    form.put("redirect_uri", r.redirectUri());
    form.put("code_verifier", r.codeVerifier());
    form.put("scope", "openid profile email");
    JsonNode tokens = http.postForm(authority(s) + "/" + s.tenantId() + "/oauth2/v2.0/token", form);
    String idToken = tokens.path("id_token").asText("");
    if (idToken.isEmpty()) throw new SsoException("Microsoft did not return an identity token");

    String authority = authority(s);
    boolean singleTenant = GUID.matcher(s.tenantId()).matches();
    JsonNode claims = jwt.verify(idToken, authority + "/" + s.tenantId() + "/discovery/v2.0/keys",
        new JwtVerifier.Expectations(
            iss -> iss.startsWith(authority + "/") && iss.endsWith("/v2.0")
                && (!singleTenant || iss.equals(authority + "/" + s.tenantId() + "/v2.0")),
            s.clientId(),
            r.nonce()));
    String tid = claims.path("tid").asText("");
    if (!claims.path("iss").asText().equals(authority + "/" + tid + "/v2.0")) {
      throw new SsoException("The token issuer does not match its tenant");
    }
    String subject = claims.path("oid").asText(claims.path("sub").asText(""));
    if (subject.isEmpty()) throw new SsoException("The identity token has no user id");
    String username = firstNonBlank(claims.path("preferred_username").asText(""), claims.path("upn").asText(""),
        claims.path("email").asText(""), subject);
    String email = firstNonBlank(claims.path("email").asText(""), username.contains("@") ? username : "");
    return new ExternalIdentity(
        subject,
        username,
        email,
        claims.path("name").asText(username),
        tid,
        strings(claims.path("groups")),
        strings(claims.path("roles")),
        claims.path("_claim_names").has("groups"));
  }

  @Override
  public JsonNode testConnection(SsoSettings s) {
    if (s.tenantId().isEmpty()) throw new SsoException("Enter the directory (tenant) ID first");
    String url = authority(s) + "/" + s.tenantId() + "/v2.0/.well-known/openid-configuration";
    JsonNode discovery = http.getJson(url);
    ObjectNode out = Jsons.object();
    out.put("issuer", discovery.path("issuer").asText());
    out.put("authorizationEndpoint", discovery.path("authorization_endpoint").asText());
    out.put("tokenEndpoint", discovery.path("token_endpoint").asText());
    out.put("tenantRegion", discovery.path("tenant_region_scope").asText(""));
    return out;
  }

  private static List<String> strings(JsonNode node) {
    List<String> out = new ArrayList<>();
    if (node.isArray()) node.forEach(n -> out.add(n.asText()));
    return out;
  }

  private static String firstNonBlank(String... values) {
    for (String v : values) if (v != null && !v.isBlank()) return v;
    return "";
  }

  private static String query(Map<String, String> params) {
    return params.entrySet().stream()
        .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
        .collect(Collectors.joining("&"));
  }
}
