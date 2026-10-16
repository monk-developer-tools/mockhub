package io.mockhub.data;

import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CrudTransformer implements ResponseDefinitionTransformerV2 {
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
    if (projectId == null) return json(500, CrudService.error("This CRUD stub doesn't belong to a project"));
    CrudResource resource;
    try {
      resource = new CrudResource(p.getString("resource", ""), p.getString("idField", "id"), CrudResource.IdType.parse(p.getString("idType", "uuid")));
    } catch (RuntimeException e) {
      return json(500, CrudService.error(e.getMessage()));
    }
    Request request = event.getRequest();
    String path = Urls.getPath(request.getUrl());
    Matcher m = Pattern.compile("^.*/" + Pattern.quote(resource.name()) + "(?:/([^/]+))?/?$").matcher(path);
    String id = m.matches() && m.group(1) != null ? URLDecoder.decode(m.group(1), StandardCharsets.UTF_8) : null;
    String method = request.getMethod().getName();
    JsonNode body;
    try {
      body = parse(request.getBodyAsString());
    } catch (Exception e) {
      return json(400, CrudService.error("The body isn't valid JSON"));
    }

    if (id == null) {
      return switch (method) {
        case "GET", "HEAD" -> list(projectId, resource, request);
        case "POST" -> result(crud.create(projectId, resource, body), 201, path);
        default -> json(405, CrudService.error(method + " isn't allowed on the " + resource.name() + " collection"));
      };
    }
    return switch (method) {
      case "GET", "HEAD" -> crud.get(projectId, resource, id)
          .map(r -> json(200, r))
          .orElseGet(() -> json(404, CrudService.error(resource.name() + " " + id + " not found")));
      case "PUT" -> result(crud.replace(projectId, resource, id, body), 200, null);
      case "PATCH" -> result(crud.patch(projectId, resource, id, body), 200, null);
      case "DELETE" -> crud.delete(projectId, resource, id)
          ? new ResponseDefinitionBuilder().withStatus(204).build()
          : json(404, CrudService.error(resource.name() + " " + id + " not found"));
      default -> json(405, CrudService.error(method + " isn't allowed on a single " + resource.name() + " record"));
    };
  }

  private ResponseDefinition list(String projectId, CrudResource resource, Request request) {
    Map<String, String> query = new LinkedHashMap<>();
    Urls.splitQueryFromUrl(request.getUrl()).forEach((k, v) -> query.put(k, v.firstValue()));
    CrudService.Page page = crud.list(projectId, resource, query);
    ArrayNode items = Jsons.MAPPER.createArrayNode();
    page.items().forEach(items::add);
    return new ResponseDefinitionBuilder().withStatus(200)
        .withHeader("Content-Type", "application/json")
        .withHeader("X-Total-Count", String.valueOf(page.total()))
        .withBody(Jsons.write(items))
        .build();
  }

  private static ResponseDefinition result(CrudService.Result result, int okStatus, String collectionPath) {
    if (result instanceof CrudService.Ok ok) {
      ResponseDefinitionBuilder b = new ResponseDefinitionBuilder().withStatus(okStatus)
          .withHeader("Content-Type", "application/json")
          .withBody(Jsons.write(ok.record()));
      if (collectionPath != null) {
        String base = collectionPath.endsWith("/") ? collectionPath : collectionPath + "/";
        b.withHeader("Location", base + ok.record().fields().next().getValue().asText());
      }
      return b.build();
    }
    if (result instanceof CrudService.NotFound nf) return json(404, CrudService.error(nf.message()));
    if (result instanceof CrudService.Conflict c) return json(409, CrudService.error(c.message()));
    return json(400, CrudService.error(((CrudService.Invalid) result).message()));
  }

  private static ResponseDefinition json(int status, JsonNode body) {
    return new ResponseDefinitionBuilder().withStatus(status)
        .withHeader("Content-Type", "application/json")
        .withBody(Jsons.write(body))
        .build();
  }

  private static JsonNode parse(String body) throws Exception {
    return body == null || body.isBlank() ? null : Jsons.MAPPER.readTree(body);
  }
}
