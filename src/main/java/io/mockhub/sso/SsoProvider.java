package io.mockhub.sso;

import com.fasterxml.jackson.databind.JsonNode;

public interface SsoProvider {
  String id();

  String authorizationUrl(SsoSettings settings, AuthorizationRequest request);

  ExternalIdentity exchange(SsoSettings settings, String code, AuthorizationRequest request);

  JsonNode testConnection(SsoSettings settings);

  record AuthorizationRequest(String redirectUri, String state, String nonce, String codeVerifier, String codeChallenge) {}
}
