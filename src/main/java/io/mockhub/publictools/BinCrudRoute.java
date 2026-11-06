package io.mockhub.publictools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.api.ApiException;
import io.mockhub.api.Jsons;
import io.mockhub.data.CrudResource;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public record BinCrudRoute(String path, CrudResource resource) {

  public record Hit(Optional<String> id) {}

  public static BinCrudRoute from(JsonNode node) {
    if (!node.isObject()) throw new IllegalArgumentException("A CRUD route is a JSON object");
    String path = node.path("path").asText("").trim();
    while (path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 1);
    if (!path.matches("/[a-z0-9][a-z0-9-]*(/[a-z0-9][a-z0-9-]*){0,4}")) {
      throw new IllegalArgumentException("A CRUD route's path is lowercase letters, digits and dashes, like /users or /api/v1/orders");
    }
    String idType = node.path("idType").asText("number");
    if (!idType.equals("number") && !idType.equals("uuid") && !idType.equals("short")) throw new IllegalArgumentException("New ids are a number, a UUID or a short id");
    try {
      return new BinCrudRoute(path, new CrudResource(path.substring(1), node.path("idField").asText("id"), CrudResource.IdType.parse(idType)));
    } catch (ApiException e) {
      throw new IllegalArgumentException(e.getMessage());
    }
  }

  public Optional<Hit> hit(String callPath) {
    if (callPath.equals(path) || callPath.equals(path + "/")) return Optional.of(new Hit(Optional.empty()));
    if (!callPath.startsWith(path + "/")) return Optional.empty();
    String rest = callPath.substring(path.length() + 1);
    if (rest.endsWith("/")) rest = rest.substring(0, rest.length() - 1);
    if (rest.isEmpty() || rest.contains("/")) return Optional.empty();
    try {
      return Optional.of(new Hit(Optional.of(URLDecoder.decode(rest, StandardCharsets.UTF_8))));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  public ObjectNode toJson() {
    return Jsons.object().put("path", path).put("idField", resource.idField()).put("idType", resource.idType().id());
  }
}
