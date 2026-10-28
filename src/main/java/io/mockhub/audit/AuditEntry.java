package io.mockhub.audit;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.mockhub.api.Jsons;

public record AuditEntry(
    String id,
    String at,
    String actorId,
    String actor,
    String action,
    String target,
    String targetName,
    String details,
    String orgId,
    String teamId,
    String projectId,
    String projectName) {

  private static final ObjectWriter ONE_LINE = Jsons.MAPPER.writer().without(SerializationFeature.INDENT_OUTPUT);

  public String toJsonLine() throws JsonProcessingException {
    return ONE_LINE.writeValueAsString(this);
  }

  @JsonIgnore
  public String summary() {
    String name = targetName == null || targetName.isBlank() ? "" : " “" + targetName + "”";
    return capitalise(action) + " " + target + name + (details == null || details.isBlank() ? "" : " (" + details + ")");
  }

  private static String capitalise(String s) {
    return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
  }
}
