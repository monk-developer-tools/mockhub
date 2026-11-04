package io.mockhub.publictools;

import com.fasterxml.jackson.databind.JsonNode;
import io.mockhub.api.Jsons;
import java.util.List;

final class InstantStarter {
  private InstantStarter() {}

  static List<JsonNode> stubs() {
    return List.of(
        parse("""
            {"name": "Say hello",
             "request": {"method": "GET", "urlPath": "/hello"},
             "response": {"status": 200, "headers": {"Content-Type": "application/json"},
                          "jsonBody": {"message": "Hello from your instant mock", "tip": "Edit this stub or add your own"}}}"""),
        parse("""
            {"name": "Get a user",
             "request": {"method": "GET", "urlPathTemplate": "/users/{id}"},
             "response": {"status": 200, "headers": {"Content-Type": "application/json"},
                          "body": "{\\"id\\": \\"{{request.path.id}}\\", \\"name\\": \\"{{fake 'Name.fullName'}}\\", \\"email\\": \\"{{fake 'Internet.emailAddress'}}\\"}",
                          "transformers": ["response-template"]}}"""));
  }

  private static JsonNode parse(String json) {
    try {
      return Jsons.MAPPER.readTree(json);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
