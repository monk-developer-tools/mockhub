package io.mockhub.tunnel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.api.Jsons;
import io.mockhub.playground.Header;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public final class TunnelMessages {
  private TunnelMessages() {}

  public record Request(String id, String method, String path, List<Header> headers, byte[] body) {}

  public record Response(int status, List<Header> headers, byte[] body) {}

  public static ObjectNode write(Request request) {
    ObjectNode out = Jsons.object();
    out.put("id", request.id());
    out.put("method", request.method());
    out.put("path", request.path());
    out.set("headers", headers(request.headers()));
    out.put("body", Base64.getEncoder().encodeToString(request.body()));
    return out;
  }

  public static Request readRequest(JsonNode node) {
    return new Request(node.path("id").asText(), node.path("method").asText("GET"), node.path("path").asText("/"),
        headers(node.path("headers")), body(node));
  }

  public static ObjectNode write(Response response) {
    ObjectNode out = Jsons.object();
    out.put("status", response.status());
    out.set("headers", headers(response.headers()));
    out.put("body", Base64.getEncoder().encodeToString(response.body()));
    return out;
  }

  public static Response readResponse(JsonNode node) {
    int status = node.path("status").asInt(0);
    if (status < 100 || status > 599) throw new TunnelException(400, "The tunnel answer needs a status from 100 to 599");
    return new Response(status, headers(node.path("headers")), body(node));
  }

  private static ArrayNode headers(List<Header> headers) {
    ArrayNode array = Jsons.MAPPER.createArrayNode();
    headers.forEach(h -> array.addArray().add(h.name()).add(h.value()));
    return array;
  }

  private static List<Header> headers(JsonNode node) {
    List<Header> out = new ArrayList<>();
    for (JsonNode pair : node) {
      if (pair.isArray() && pair.size() == 2) out.add(new Header(pair.get(0).asText(), pair.get(1).asText()));
    }
    return out;
  }

  private static byte[] body(JsonNode node) {
    String text = node.path("body").asText("");
    try {
      return text.isEmpty() ? new byte[0] : Base64.getDecoder().decode(text);
    } catch (IllegalArgumentException e) {
      throw new TunnelException(400, "The body must be base64");
    }
  }
}
