package io.mockhub.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import io.mockhub.auth.Principal;

public interface Stager {
  JsonNode stage(Principal principal, String projectId, String tool, JsonNode args);
}
