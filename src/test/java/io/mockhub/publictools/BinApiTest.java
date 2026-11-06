package io.mockhub.publictools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.mockhub.api.Jsons;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class BinApiTest {

  private static List<BinRule> rules(String json) throws Exception {
    return BinRule.listFrom(Jsons.MAPPER.readTree(json));
  }

  @Test
  void rulesAreCreatedReplacedPatchedReorderedAndDeletedOneByOne() throws Exception {
    // Given
    List<BinRule> start = rules("""
        [{"id": "a1", "name": "First", "answers": [{"status": 200}]},
         {"id": "b2", "name": "Second", "answers": [{"status": 201}]}]
        """);

    // When
    List<BinRule> created = BinRuleEdits.create(start, Jsons.MAPPER.readTree("{\"id\": \"c3\", \"name\": \"Third\", \"answers\": [{\"status\": 202}]}"), -1);
    List<BinRule> replaced = BinRuleEdits.replace(created, "a1", Jsons.MAPPER.readTree("{\"name\": \"New first\", \"answers\": [{\"status\": 204}]}"));
    List<BinRule> patched = BinRuleEdits.patch(replaced, "b2", Jsons.MAPPER.readTree("{\"enabled\": false, \"description\": \"off for now\"}"));
    List<BinRule> reordered = BinRuleEdits.reorder(patched, Jsons.MAPPER.readTree("[\"c3\", \"a1\", \"b2\"]"));
    List<BinRule> deleted = BinRuleEdits.delete(reordered, "a1");

    // Then
    assertThat(created).extracting(BinRule::id).containsExactly("a1", "b2", "c3");
    assertThat(replaced.get(0).name()).isEqualTo("New first");
    assertThat(replaced.get(0).id()).isEqualTo("a1");
    assertThat(replaced.get(0).answers().get(0).status()).isEqualTo(204);
    assertThat(patched.get(1).enabled()).isFalse();
    assertThat(patched.get(1).description()).isEqualTo("off for now");
    assertThat(patched.get(1).answers().get(0).status()).isEqualTo(201);
    assertThat(reordered).extracting(BinRule::id).containsExactly("c3", "a1", "b2");
    assertThat(deleted).extracting(BinRule::id).containsExactly("c3", "b2");
  }

  @Test
  void ruleEditsRefuseUnknownTakenAndIncompleteOrders() throws Exception {
    // Given
    List<BinRule> start = rules("[{\"id\": \"a1\", \"answers\": [{}]}, {\"id\": \"b2\", \"answers\": [{}]}]");

    // When
    List<BinRule> duplicated = rules("[{\"id\": \"a1\", \"answers\": [{}]}, {\"id\": \"a1\", \"answers\": [{}]}, {\"id\": \"order\", \"answers\": [{}]}]");
    List<BinRule> unnamed = BinRuleEdits.create(start, Jsons.MAPPER.readTree("{\"answers\": [{}]}"), -1);

    // Then
    assertThat(duplicated).extracting(BinRule::id).doesNotHaveDuplicates().doesNotContain("order").startsWith("a1");
    assertThat(unnamed.get(2).id()).isNotIn("a1", "b2");
    assertThatThrownBy(() -> BinRuleEdits.create(start, Jsons.MAPPER.readTree("{\"id\": \"a1\", \"answers\": [{}]}"), -1)).isInstanceOf(BinRuleEdits.Taken.class);
    assertThatThrownBy(() -> BinRuleEdits.patch(start, "zz", Jsons.MAPPER.readTree("{}"))).isInstanceOf(BinRuleEdits.Missing.class).hasMessage("No rule zz in this bin");
    assertThatThrownBy(() -> BinRuleEdits.delete(start, "zz")).isInstanceOf(BinRuleEdits.Missing.class);
    assertThatThrownBy(() -> BinRuleEdits.reorder(start, Jsons.MAPPER.readTree("[\"a1\"]"))).hasMessage("List every rule once: the bin has 2, you sent 1");
    assertThatThrownBy(() -> BinRuleEdits.reorder(start, Jsons.MAPPER.readTree("[\"a1\", \"a1\"]"))).hasMessage("Rule a1 is listed twice");
    assertThatThrownBy(() -> BinRuleEdits.patch(start, "a1", Jsons.MAPPER.readTree("{\"answers\": null}"))).hasMessage("Give the rule an answer");
  }

  @Test
  void stateEntriesAreSetAndRemovedOneAtATime() throws Exception {
    // Given
    BinState state = new BinState();
    state.add("seen", "old");

    // When
    state.put(BinState.Kind.COUNTERS, "orders", Jsons.MAPPER.readTree("41"));
    state.put(BinState.Kind.VALUES, "token", Jsons.MAPPER.readTree("\"abc\""));
    state.put(BinState.Kind.LISTS, "seen", Jsons.MAPPER.readTree("[\"a\", \"b\"]"));
    boolean removed = state.remove(BinState.Kind.VALUES, "token");
    boolean again = state.remove(BinState.Kind.VALUES, "token");

    // Then
    assertThat(state.count("orders", 1, false)).isEqualTo(42);
    assertThat(state.list("seen")).containsExactly("a", "b");
    assertThat(removed).isTrue();
    assertThat(again).isFalse();
    assertThatThrownBy(() -> state.put(BinState.Kind.COUNTERS, "x", Jsons.MAPPER.readTree("\"a\""))).hasMessage("A counter is a whole number");
    assertThatThrownBy(() -> state.put(BinState.Kind.LISTS, "seen", Jsons.MAPPER.readTree("[{}]"))).hasMessage("A list is an array of text");
    assertThat(state.list("seen")).containsExactly("a", "b");
    assertThatThrownBy(() -> BinState.Kind.of("maps")).hasMessage("Saved state is counters, values or lists");
  }

  @Test
  void aMultipartBodySplitsIntoItsFieldsAndFilesBytesIntact() {
    // Given
    byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0, (byte) 0xff};
    byte[] head = ("--XyZ\r\nContent-Disposition: form-data; name=\"note\"\r\n\r\nhello\r\n"
        + "--XyZ\r\nContent-Disposition: form-data; name=\"logo\"; filename=\"logo.png\"\r\nContent-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8);
    byte[] tail = "\r\n--XyZ--\r\n".getBytes(StandardCharsets.UTF_8);
    byte[] body = new byte[head.length + png.length + tail.length];
    System.arraycopy(head, 0, body, 0, head.length);
    System.arraycopy(png, 0, body, head.length, png.length);
    System.arraycopy(tail, 0, body, head.length + png.length, tail.length);

    // When
    List<BinMultipart.Part> parts = BinMultipart.parts(body, "multipart/form-data; boundary=XyZ");

    // Then
    assertThat(parts).hasSize(2);
    assertThat(parts.get(0).name()).isEqualTo("note");
    assertThat(new String(parts.get(0).bytes(), StandardCharsets.UTF_8)).isEqualTo("hello");
    assertThat(parts.get(1).filename()).isEqualTo("logo.png");
    assertThat(parts.get(1).type()).isEqualTo("image/png");
    assertThat(parts.get(1).bytes()).containsExactly(png);
    assertThat(BinMultipart.parts(body, "application/json")).isEmpty();
  }
}
