package io.mockhub.bench;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class Scenarios {
  static final String SLUG = "bench";
  static final String MANY_SLUG = "bench-many";
  static final int MANY_STUBS = 1000;
  static final int[] SIZES = {1024, 10 * 1024, 100 * 1024, 1024 * 1024};
  private static final ObjectMapper JSON = new ObjectMapper();

  private final String host;
  private final int connections;

  Scenarios(String host, int connections) {
    this.host = host;
    this.connections = connections;
  }

  static ObjectNode mappings() {
    ObjectNode root = JSON.createObjectNode();
    ArrayNode list = root.putArray("mappings");
    list.add(stub("GET", "/hello", 200, "{\"message\":\"Hello from MockHub\",\"ok\":true}", false));
    for (int size : SIZES) list.add(stub("GET", "/payload/" + label(size), 200, payload(size), false));
    list.add(stub("GET", "/users/{id}", 200,
        "{\"id\":\"{{request.path.id}}\",\"token\":\"{{randomValue length=16 type='ALPHANUMERIC'}}\",\"at\":\"{{now}}\",\"agent\":\"{{request.headers.User-Agent}}\"}", true));
    ObjectNode order = stub("POST", "/orders", 201, "{\"id\":101,\"status\":\"CREATED\"}", false);
    ((ObjectNode) order.get("request")).putArray("bodyPatterns").addObject()
        .put("equalToJson", "{\"sku\":\"A-1\",\"qty\":2}").put("ignoreExtraElements", true);
    list.add(order);
    return root;
  }

  static ObjectNode manyStubs() {
    ObjectNode root = JSON.createObjectNode();
    ArrayNode list = root.putArray("mappings");
    for (int i = 0; i < MANY_STUBS; i++) list.add(stub("GET", "/items/" + i, 200, "{\"id\":" + i + "}", false));
    return root;
  }

  List<Scenario> all() {
    List<Scenario> out = new ArrayList<>();
    for (int c : new int[] {1, 16, 64, 256}) out.add(hello().withConnections(c));
    for (int size : SIZES) {
      byte[] request = get("/payload/" + label(size));
      out.add(new Scenario("payload-" + label(size), "GET, " + label(size) + " JSON body", connections, 200, r -> request));
    }
    byte[] template = get("/users/42");
    out.add(new Scenario("template", "GET, response template (path, random value, date, header)", connections, 200, r -> template));
    byte[] order = RawHttpConnection.request("POST", host, "/" + SLUG + "/orders", "application/json", "{\"sku\":\"A-1\",\"qty\":2,\"note\":\"bench\"}");
    out.add(new Scenario("post-json", "POST, body matched with equalToJson", connections, 201, r -> order));
    byte[][] items = new byte[MANY_STUBS][];
    for (int i = 0; i < MANY_STUBS; i++) items[i] = RawHttpConnection.request("GET", host, "/" + MANY_SLUG + "/items/" + i, null, null);
    out.add(new Scenario("many-stubs", "GET, one of " + MANY_STUBS + " stubs at random", connections, 200, r -> items[r.nextInt(MANY_STUBS)]));
    return out;
  }

  Scenario hello() {
    byte[] request = get("/hello");
    return new Scenario("hello", "GET, small JSON body", connections, 200, r -> request);
  }

  Scenario soak() {
    Scenario base = hello();
    return new Scenario("steady", "GET, small JSON body, held for the whole run", connections, 200, base.source());
  }

  private byte[] get(String path) {
    return RawHttpConnection.request("GET", host, "/" + SLUG + path, null, null);
  }

  static String label(int size) {
    return size >= 1024 * 1024 ? (size / (1024 * 1024)) + "mb" : (size / 1024) + "kb";
  }

  static String payload(int size) {
    StringBuilder body = new StringBuilder("{\"items\":[");
    int i = 0;
    while (body.length() < size - 80) {
      if (i > 0) body.append(',');
      body.append(String.format(Locale.ROOT, "{\"id\":%d,\"name\":\"item-%05d\",\"price\":%d.50,\"tags\":[\"a\",\"b\"]}", i, i, i % 100));
      i++;
    }
    body.append("],\"count\":").append(i).append('}');
    while (body.length() < size) body.insert(body.length() - 1, ' ');
    return body.toString();
  }

  private static ObjectNode stub(String method, String path, int status, String body, boolean template) {
    ObjectNode stub = JSON.createObjectNode().put("name", method + " " + path);
    ObjectNode request = stub.putObject("request").put("method", method);
    request.put(path.contains("{") ? "urlPathTemplate" : "urlPath", path);
    ObjectNode response = stub.putObject("response").put("status", status).put("body", body);
    response.putObject("headers").put("Content-Type", "application/json");
    if (template) response.putArray("transformers").add("response-template");
    return stub;
  }
}
