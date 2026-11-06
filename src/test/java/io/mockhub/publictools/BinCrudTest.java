package io.mockhub.publictools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import io.mockhub.api.Jsons;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BinCrudTest {

  private static final String BIN = "abcdefghijkm";

  @TempDir
  Path data;

  private BinResponder responder(BinRecords records) {
    return new BinResponder(null, null, null, null, null, bound -> 0, System::currentTimeMillis, records);
  }

  private static BinMatcher.Call call(String method, String path, String query, String body) {
    PublicRequest request = new PublicRequest(method, "/bins/" + BIN + path, query, PublicRequest.parseQuery(query),
        Map.of("Content-Type", "application/json"), body, body.length(), "203.0.113.9");
    return new BinMatcher.Call(method, path, request);
  }

  private static Bins.Settings withRules(String rules) throws Exception {
    return Bins.Settings.DEFAULT.withRules(true, BinRule.listFrom(Jsons.MAPPER.readTree(rules)));
  }

  private static JsonNode json(BinResponder.Answer answer) throws Exception {
    return Jsons.MAPPER.readTree(new String(answer.body(), StandardCharsets.UTF_8));
  }

  @Test
  void aCrudRouteCreatesListsReadsReplacesPatchesAndDeletesRecordsThatSurviveARestart() throws Exception {
    // Given
    Bins.Settings settings = withRules("""
        [{"name": "Users", "crud": {"path": "/api/users", "idField": "id", "idType": "number"}}]
        """);
    BinResponder responder = responder(new BinRecords(Optional.of(data)));

    // When
    BinResponder.Answer ada = responder.answer(BIN, settings, call("POST", "/api/users", "", "{\"name\":\"Ada\",\"role\":\"admin\"}"));
    BinResponder.Answer bob = responder.answer(BIN, settings, call("POST", "/api/users/", "", "{\"name\":\"Bob\",\"role\":\"user\"}"));
    BinResponder.Answer admins = responder.answer(BIN, settings, call("GET", "/api/users", "role=admin", ""));
    BinResponder.Answer replaced = responder.answer(BIN, settings, call("PUT", "/api/users/2", "", "{\"name\":\"Robert\"}"));
    BinResponder.Answer patched = responder.answer(BIN, settings, call("PATCH", "/api/users/1", "", "{\"team\":\"core\"}"));
    BinResponder.Answer deleted = responder.answer(BIN, settings, call("DELETE", "/api/users/2", "", ""));
    BinResponder.Answer gone = responder.answer(BIN, settings, call("GET", "/api/users/2", "", ""));
    BinResponder after = responder(new BinRecords(Optional.of(data)));
    BinResponder.Answer reread = after.answer(BIN, settings, call("GET", "/api/users/1", "", ""));

    // Then
    assertThat(ada.status()).isEqualTo(201);
    assertThat(ada.by()).isEqualTo("crud");
    assertThat(ada.rule()).isEqualTo("Users");
    assertThat(ada.headers()).containsEntry("Location", "/__public/bins/" + BIN + "/api/users/1");
    assertThat(json(bob).get("id").asInt()).isEqualTo(2);
    assertThat(admins.headers()).containsEntry("X-Total-Count", "1");
    assertThat(json(admins).get(0).get("name").asText()).isEqualTo("Ada");
    assertThat(json(replaced)).isEqualTo(Jsons.MAPPER.readTree("{\"id\":2,\"name\":\"Robert\"}"));
    assertThat(json(patched)).isEqualTo(Jsons.MAPPER.readTree("{\"id\":1,\"name\":\"Ada\",\"role\":\"admin\",\"team\":\"core\"}"));
    assertThat(deleted.status()).isEqualTo(204);
    assertThat(gone.status()).isEqualTo(404);
    assertThat(json(gone).get("code").asText()).isEqualTo("not_found");
    assertThat(json(reread).get("team").asText()).isEqualTo("core");
    assertThat(Files.exists(data.resolve(BIN).resolve(BinRecords.FILE))).isTrue();
  }

  @Test
  void otherPathsFallThroughAndWrongMethodsAndBodiesAreExplained() throws Exception {
    // Given
    Bins.Settings settings = withRules("""
        [{"crud": {"path": "/orders", "idType": "uuid"}},
         {"name": "Everything else", "answers": [{"status": 418, "body": "teapot"}]}]
        """);
    BinResponder responder = responder(new BinRecords(Optional.empty()));

    // When
    BinResponder.Answer deeper = responder.answer(BIN, settings, call("GET", "/orders/1/items", "", ""));
    BinResponder.Answer similar = responder.answer(BIN, settings, call("GET", "/orders-archive", "", ""));
    BinResponder.Answer patchAll = responder.answer(BIN, settings, call("PATCH", "/orders", "", "{}"));
    BinResponder.Answer broken = responder.answer(BIN, settings, call("POST", "/orders", "", "{nope"));
    BinResponder.Answer array = responder.answer(BIN, settings, call("POST", "/orders", "", "[1]"));
    BinResponder.Answer created = responder.answer(BIN, settings, call("POST", "/orders", "", "{\"total\": 5}"));
    BinResponder.Answer badQuery = responder.answer(BIN, settings, call("GET", "/orders", "limit=-1", ""));

    // Then
    assertThat(deeper.status()).isEqualTo(418);
    assertThat(similar.status()).isEqualTo(418);
    assertThat(patchAll.status()).isEqualTo(405);
    assertThat(broken.status()).isEqualTo(400);
    assertThat(json(broken).get("code").asText()).isEqualTo("invalid_json_body");
    assertThat(json(array).get("code").asText()).isEqualTo("invalid_body");
    assertThat(json(created).get("id").asText()).matches("[0-9a-f-]{36}");
    assertThat(badQuery.status()).isEqualTo(400);
  }

  @Test
  void aBinRefusesOversizedRecordsAndTooManyOfThem() throws Exception {
    // Given
    Bins.Settings settings = withRules("""
        [{"crud": {"path": "/things"}}]
        """);
    BinRecords records = new BinRecords(Optional.empty());
    BinResponder responder = responder(records);
    BinCrud crud = new BinCrud(records);
    BinCrudRoute route = settings.rules().get(0).crud().orElseThrow();
    String big = "{\"blob\":\"" + "x".repeat(BinRecords.MAX_RECORD_BYTES) + "\"}";

    // When
    BinResponder.Answer tooBig = responder.answer(BIN, settings, call("POST", "/things", "", big));
    int seeded = crud.seed(BIN, route, Jsons.MAPPER.readTree("[" + String.join(",", java.util.Collections.nCopies(BinRecords.MAX_RECORDS, "{}")) + "]"));
    BinResponder.Answer full = responder.answer(BIN, settings, call("POST", "/things", "", "{}"));

    // Then
    assertThat(tooBig.status()).isEqualTo(400);
    assertThat(json(tooBig).get("error").asText()).isEqualTo("A record can be up to 8 KB");
    assertThat(seeded).isEqualTo(BinRecords.MAX_RECORDS);
    assertThat(full.status()).isEqualTo(400);
    assertThat(json(full).get("code").asText()).isEqualTo("limit_reached");
    assertThatThrownBy(() -> crud.seed(BIN, route, Jsons.MAPPER.readTree("[1]"))).hasMessage("Each record is a JSON object");
  }

  @Test
  void crudRoutesAreCheckedWhenSavedAndRoundTripThroughJson() throws Exception {
    // Given
    String rules = """
        [{"name": "Orders", "method": "ANY", "crud": {"path": "/api/v1/orders/", "idField": "orderId", "idType": "short"}}]
        """;

    // When
    List<BinRule> parsed = BinRule.listFrom(Jsons.MAPPER.readTree(rules));
    List<BinRule> again = BinRule.listFrom(BinRule.toJson(parsed));

    // Then
    assertThat(again).isEqualTo(parsed);
    assertThat(again.get(0).crud().orElseThrow().toJson().toString()).isEqualTo("{\"path\":\"/api/v1/orders\",\"idField\":\"orderId\",\"idType\":\"short\"}");
    assertThatThrownBy(() -> BinRule.listFrom(Jsons.MAPPER.readTree("[{\"crud\": {\"path\": \"/Users\"}}]")))
        .hasMessageStartingWith("Rule 1: CRUD route: A CRUD route's path is lowercase");
    assertThatThrownBy(() -> BinRule.listFrom(Jsons.MAPPER.readTree("[{\"crud\": {\"path\": \"/u\", \"idField\": \"a.b\"}}]")))
        .hasMessage("Rule 1: CRUD route: The id field must be a plain field name, e.g. id");
    assertThatThrownBy(() -> BinRule.listFrom(Jsons.MAPPER.readTree("[{\"crud\": {\"path\": \"/u\", \"idType\": \"guid\"}}]")))
        .hasMessage("Rule 1: CRUD route: New ids are a number, a UUID or a short id");
    assertThatThrownBy(() -> BinRule.listFrom(Jsons.MAPPER.readTree("[{\"crud\": {\"path\": \"/u\"}, \"callout\": {\"url\": \"https://x.example.com\"}}]")))
        .hasMessage("Rule 1: A CRUD route answers from its records, so it can't also call out");
  }
}
