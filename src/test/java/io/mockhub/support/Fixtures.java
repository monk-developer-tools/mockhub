package io.mockhub.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.common.Json;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import io.mockhub.api.Jsons;
import io.mockhub.project.Project;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

public final class Fixtures {

  private Fixtures() {}

  public static Project project(String slug) {
    return new Project("p-" + slug, slug, slug, "", "#000000", "2024-01-01T00:00:00Z", "2024-01-01T00:00:00Z");
  }

  public static ObjectNode json(String json) {
    try {
      return (ObjectNode) Jsons.MAPPER.readTree(json.replace('\'', '"'));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static JsonNode tree(String json) {
    return json(json);
  }

  public static StubMapping stub(String json) {
    return Json.read(json.replace('\'', '"'), StubMapping.class);
  }

  public static String resource(String name) {
    try (InputStream in = Fixtures.class.getResourceAsStream("/" + name)) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
