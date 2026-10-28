package io.mockhub.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.http.RequestMethod;
import io.mockhub.auth.Principal;
import java.util.Map;

public interface ChangeAudit {
  Pending begin(Change change);

  default boolean covers(RequestMethod method, String route) {
    return true;
  }

  record Change(Principal principal, RequestMethod method, String route, Map<String, String> params, JsonNode body) {
    public String param(String name) {
      return params.get(name);
    }
  }

  interface Pending {
    Pending NONE = result -> {};

    void succeeded(Object result);
  }
}
