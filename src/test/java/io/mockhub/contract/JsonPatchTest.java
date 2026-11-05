package io.mockhub.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import io.mockhub.api.Jsons;
import org.junit.jupiter.api.Test;

class JsonPatchTest {

  private static JsonNode json(String text) throws Exception {
    return Jsons.MAPPER.readTree(text);
  }

  @Test
  void addsReplacesAndRemovesWithEscapedPointers() throws Exception {
    // Given
    JsonNode doc = json("{\"paths\":{\"/orders\":{\"get\":{}}},\"tags\":[\"a\",\"c\"],\"old\":1}");
    JsonNode patch = json("""
        [{"op":"add","path":"/paths/~1orders~1{id}","value":{"get":{}}},
         {"op":"replace","path":"/paths/~1orders/get","value":{"summary":"List"}},
         {"op":"add","path":"/tags/1","value":"b"},
         {"op":"add","path":"/tags/-","value":"d"},
         {"op":"remove","path":"/old"}]
        """);

    // When
    JsonNode out = JsonPatch.apply(doc, patch);

    // Then
    assertThat(out).isEqualTo(json("{\"paths\":{\"/orders\":{\"get\":{\"summary\":\"List\"}},\"/orders/{id}\":{\"get\":{}}},\"tags\":[\"a\",\"b\",\"c\",\"d\"]}"));
    assertThat(doc.has("old")).isTrue();
  }

  @Test
  void refusesPatchesThatDontFit() throws Exception {
    // Given
    JsonNode doc = json("{\"a\":{}}");

    // When / Then
    assertThatThrownBy(() -> JsonPatch.apply(doc, json("[{\"op\":\"replace\",\"path\":\"/b\",\"value\":1}]"))).hasMessageContaining("nothing at /b");
    assertThatThrownBy(() -> JsonPatch.apply(doc, json("[{\"op\":\"add\",\"path\":\"/x/y/z\",\"value\":1}]"))).hasMessageContaining("Nothing at /x/y/z");
    assertThatThrownBy(() -> JsonPatch.apply(doc, json("[{\"op\":\"move\",\"path\":\"/a\",\"from\":\"/b\"}]"))).hasMessageContaining("only add, replace and remove");
    assertThatThrownBy(() -> JsonPatch.apply(doc, json("[{\"op\":\"add\",\"path\":\"/c\"}]"))).hasMessageContaining("no value");
    assertThatThrownBy(() -> JsonPatch.apply(doc, json("{}"))).hasMessageContaining("list of operations");
  }
}
