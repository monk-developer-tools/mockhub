package io.mockhub.ai;

import com.fasterxml.jackson.databind.JsonNode;

public record ProposedChange(String id, String action, String stubId, JsonNode stub, JsonNode before, String summary) {
  public static final String CREATE = "create";
  public static final String UPDATE = "update";
  public static final String DELETE = "delete";
}
