package io.mockhub.example;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import io.mockhub.api.ApiException;
import io.mockhub.api.Jsons;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Function;

public final class ExampleCatalog {

  private static final String ROOT = "/examples/";

  private final Function<String, String> resources;
  private final List<Example> examples;

  public ExampleCatalog(Function<String, String> resources) {
    this.resources = resources;
    this.examples = parse(resources.apply("index.json"));
  }

  public static ExampleCatalog fromClasspath() {
    return new ExampleCatalog(ExampleCatalog::readClasspath);
  }

  public List<Example> all() {
    return examples;
  }

  public Example get(String id) {
    return examples.stream().filter(e -> e.id().equals(id)).findFirst()
        .orElseThrow(() -> ApiException.notFound("Example " + id));
  }

  public JsonNode content(Example example) {
    try {
      return Jsons.MAPPER.readTree(resources.apply(example.file()));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public String file(String name) {
    return resources.apply(name);
  }

  private static List<Example> parse(String index) {
    try {
      return Jsons.MAPPER.readValue(index, new TypeReference<List<Example>>() {});
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static String readClasspath(String name) {
    try (InputStream in = ExampleCatalog.class.getResourceAsStream(ROOT + name)) {
      if (in == null) throw new IllegalStateException("Missing example resource " + name);
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
