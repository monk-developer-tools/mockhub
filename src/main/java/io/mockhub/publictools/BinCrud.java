package io.mockhub.publictools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.api.Jsons;
import io.mockhub.data.CrudQuery;
import io.mockhub.data.CrudResource;
import io.mockhub.data.CrudService;
import io.mockhub.data.DataStore;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

final class BinCrud {
  private final CrudService crud;

  BinCrud(DataStore store) {
    this.crud = new CrudService(store, BinRecords.POLICY, CrudService.NO_LIMIT);
  }

  BinResponder.Answer answer(String binId, BinCrudRoute route, BinCrudRoute.Hit hit, BinMatcher.Call call, String label) {
    CrudResource resource = route.resource();
    JsonNode body;
    try {
      String text = call.request().body();
      body = text == null || text.isBlank() ? null : Jsons.MAPPER.readTree(text);
    } catch (Exception e) {
      return json(400, CrudService.error("invalid_json_body", "The body isn't valid JSON"), label);
    }
    String method = call.method();
    try {
      if (hit.id().isEmpty()) {
        return switch (method) {
          case "GET", "HEAD" -> list(binId, resource, call, label);
          case "POST" -> saved(crud.create(binId, resource, body), 201, location(binId, route), resource, null, label);
          default -> json(405, CrudService.error("method_not_allowed", method + " isn't allowed on " + route.path() + "; use GET or POST"), label);
        };
      }
      String id = hit.id().get();
      return switch (method) {
        case "GET", "HEAD" -> crud.get(binId, resource, id).map(r -> json(200, r, label)).orElseGet(() -> notFound(resource, id, label));
        case "PUT" -> saved(crud.replace(binId, resource, id, body), 200, null, resource, id, label);
        case "PATCH" -> saved(crud.patch(binId, resource, id, body), 200, null, resource, id, label);
        case "DELETE" -> crud.delete(binId, resource, id)
            ? new BinResponder.Answer(204, Map.of(), new byte[0], 0, "crud", label)
            : notFound(resource, id, label);
        default -> json(405, CrudService.error("method_not_allowed", method + " isn't allowed on one record; use GET, PUT, PATCH or DELETE"), label);
      };
    } catch (IllegalStateException e) {
      return json(400, CrudService.error("limit_reached", e.getMessage()), label);
    }
  }

  int seed(String binId, BinCrudRoute route, JsonNode list) {
    if (list == null || !list.isArray()) throw new IllegalArgumentException("Send the records as a list of JSON objects");
    if (list.size() > BinRecords.MAX_RECORDS) throw new IllegalArgumentException("A bin holds up to " + BinRecords.MAX_RECORDS + " records");
    for (JsonNode r : list) if (!r.isObject()) throw new IllegalArgumentException("Each record is a JSON object");
    try {
      return crud.seed(binId, route.resource(), list);
    } catch (IllegalStateException e) {
      throw new IllegalArgumentException(e.getMessage());
    }
  }

  private BinResponder.Answer list(String binId, CrudResource resource, BinMatcher.Call call, String label) {
    CrudQuery.Parsed parsed = CrudQuery.parse(call.request().query());
    if (parsed instanceof CrudQuery.Invalid invalid) return json(400, CrudService.error("invalid_query", invalid.message()), label);
    CrudService.Page page = crud.list(binId, resource, ((CrudQuery.Ok) parsed).query());
    ArrayNode items = Jsons.MAPPER.createArrayNode();
    page.items().forEach(items::add);
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("Content-Type", "application/json");
    headers.put("X-Total-Count", String.valueOf(page.total()));
    return new BinResponder.Answer(200, headers, bytes(items), 0, "crud", label);
  }

  private static BinResponder.Answer saved(CrudService.Result result, int okStatus, String collection, CrudResource resource, String id, String label) {
    if (result instanceof CrudService.Ok ok) {
      Map<String, String> headers = new LinkedHashMap<>();
      headers.put("Content-Type", "application/json");
      if (collection != null) headers.put("Location", collection + "/" + ok.record().path(resource.idField()).asText());
      return new BinResponder.Answer(okStatus, headers, bytes(ok.record()), 0, "crud", label);
    }
    if (result instanceof CrudService.NotFound) return notFound(resource, id, label);
    if (result instanceof CrudService.Conflict c) return json(409, CrudService.error("already_exists", c.message()), label);
    if (result instanceof CrudService.Refused r) return json(400, CrudService.error("limit_reached", r.message()), label);
    return json(400, CrudService.error("invalid_body", ((CrudService.Invalid) result).message()), label);
  }

  private static String location(String binId, BinCrudRoute route) {
    return PublicRequest.PREFIX + "/bins/" + binId + route.path();
  }

  private static BinResponder.Answer notFound(CrudResource resource, String id, String label) {
    ObjectNode body = CrudService.error("not_found", "No " + resource.name() + " record has " + resource.idField() + " " + id);
    return json(404, body.put("id", Optional.ofNullable(id).orElse("")), label);
  }

  private static BinResponder.Answer json(int status, JsonNode body, String label) {
    return new BinResponder.Answer(status, Map.of("Content-Type", "application/json"), bytes(body), 0, "crud", label);
  }

  private static byte[] bytes(JsonNode node) {
    return Jsons.write(node).getBytes(StandardCharsets.UTF_8);
  }
}
