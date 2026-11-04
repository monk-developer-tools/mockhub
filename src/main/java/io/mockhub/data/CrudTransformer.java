package io.mockhub.data;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.common.Urls;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformerV2;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import io.mockhub.api.Jsons;
import io.mockhub.stub.StubMetadata;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CrudTransformer implements ResponseDefinitionTransformerV2 {
  static final String INVALID_JSON_BODY = "invalid_json_body";
  static final String INVALID_BODY = "invalid_body";
  static final String INVALID_QUERY = "invalid_query";
  static final String NOT_FOUND = "not_found";
  static final String ALREADY_EXISTS = "already_exists";
  static final String METHOD_NOT_ALLOWED = "method_not_allowed";
  static final String LIMIT_REACHED = "limit_reached";
  static final String MISCONFIGURED = "crud_misconfigured";

  private final CrudService crud;

  public CrudTransformer(CrudService crud) {
    this.crud = crud;
  }

  @Override
  public String getName() {
    return CrudStubs.TRANSFORMER;
  }

  @Override
  public boolean applyGlobally() {
    return false;
  }

  @Override
  public ResponseDefinition transform(ServeEvent event) {
    Parameters p = event.getResponseDefinition().getTransformerParameters();
    String projectId = StubMetadata.projectId(event.getStubMapping());
    if (projectId == null) return json(500, CrudService.error(MISCONFIGURED, "This CRUD stub doesn't belong to a project"));
    CrudResource resource;
    try {
      resource = new CrudResource(p.getString("resource", ""), p.getString("idField", "id"), CrudResource.IdType.parse(p.getString("idType", "uuid")));
    } catch (RuntimeException e) {
      return json(500, CrudService.error(MISCONFIGURED, e.getMessage()));
    }
    Request request = event.getRequest();
    String path = Urls.getPath(request.getUrl());
    Matcher m = Pattern.compile("^.*/" + Pattern.quote(resource.name()) + "(?:/([^/]+))?/?$").matcher(path);
    String id = m.matches() && m.group(1) != null ? URLDecoder.decode(m.group(1), StandardCharsets.UTF_8) : null;
    String method = request.getMethod().getName();
    JsonNode body;
    try {
      body = parse(request.getBodyAsString());
    } catch (JsonProcessingException e) {
      return json(400, CrudService.error(INVALID_JSON_BODY, "The body isn't valid JSON" + where(e.getLocation())));
    }

    if (id == null) {
      return switch (method) {
        case "GET", "HEAD" -> list(projectId, resource, request);
        case "POST" -> result(crud.create(projectId, resource, body), 201, path, resource, null);
        default -> json(405, CrudService.error(METHOD_NOT_ALLOWED, method + " isn't allowed on the " + resource.name() + " collection"));
      };
    }
    return switch (method) {
      case "GET", "HEAD" -> crud.get(projectId, resource, id)
          .map(r -> json(200, r))
          .orElseGet(() -> notFound(resource, id));
      case "PUT" -> result(crud.replace(projectId, resource, id, body), 200, null, resource, id);
      case "PATCH" -> result(crud.patch(projectId, resource, id, body), 200, null, resource, id);
      case "DELETE" -> crud.delete(projectId, resource, id)
          ? new ResponseDefinitionBuilder().withStatus(204).build()
          : notFound(resource, id);
      default -> json(405, CrudService.error(METHOD_NOT_ALLOWED, method + " isn't allowed on a single " + resource.name() + " record"));
    };
  }

  private ResponseDefinition list(String projectId, CrudResource resource, Request request) {
    Map<String, List<String>> params = new LinkedHashMap<>();
    Urls.splitQueryFromUrl(request.getUrl()).forEach((k, v) -> params.put(k, v.values()));
    CrudQuery.Parsed parsed = CrudQuery.parse(params);
    if (parsed instanceof CrudQuery.Invalid invalid) return json(400, CrudService.error(INVALID_QUERY, invalid.message()));
    CrudService.Page page = crud.list(projectId, resource, ((CrudQuery.Ok) parsed).query());
    ArrayNode items = Jsons.MAPPER.createArrayNode();
    page.items().forEach(items::add);
    return new ResponseDefinitionBuilder().withStatus(200)
        .withHeader("Content-Type", "application/json")
        .withHeader("X-Total-Count", String.valueOf(page.total()))
        .withBody(Jsons.write(items))
        .build();
  }

  private static ResponseDefinition result(CrudService.Result result, int okStatus, String collectionPath, CrudResource resource, String id) {
    if (result instanceof CrudService.Ok ok) return saved(ok.record(), okStatus, collectionPath);
    if (result instanceof CrudService.NotFound) return notFound(resource, id);
    if (result instanceof CrudService.Conflict c) return json(409, CrudService.error(ALREADY_EXISTS, c.message()));
    if (result instanceof CrudService.Refused r) return json(400, CrudService.error(LIMIT_REACHED, r.message()));
    return json(400, CrudService.error(INVALID_BODY, ((CrudService.Invalid) result).message()));
  }

  private static ResponseDefinition saved(ObjectNode record, int status, String collectionPath) {
    ResponseDefinitionBuilder b = new ResponseDefinitionBuilder().withStatus(status)
        .withHeader("Content-Type", "application/json")
        .withBody(Jsons.write(record));
    if (collectionPath != null) {
      String base = collectionPath.endsWith("/") ? collectionPath : collectionPath + "/";
      b.withHeader("Location", base + record.fields().next().getValue().asText());
    }
    return b.build();
  }

  private static ResponseDefinition notFound(CrudResource resource, String id) {
    ObjectNode body = CrudService.error(NOT_FOUND, "No " + resource.name() + " record has " + resource.idField() + " " + id);
    return json(404, body.put("resource", resource.name()).put("id", id));
  }

  private static String where(JsonLocation location) {
    return location == null || location.getLineNr() < 1 ? "" : " (line " + location.getLineNr() + ", column " + location.getColumnNr() + ")";
  }

  private static ResponseDefinition json(int status, JsonNode body) {
    return new ResponseDefinitionBuilder().withStatus(status)
        .withHeader("Content-Type", "application/json")
        .withBody(Jsons.write(body))
        .build();
  }

  private static JsonNode parse(String body) throws JsonProcessingException {
    return body == null || body.isBlank() ? null : Jsons.MAPPER.readTree(body);
  }
}
