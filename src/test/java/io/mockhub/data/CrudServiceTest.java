package io.mockhub.data;

import static io.mockhub.support.Fixtures.json;
import static io.mockhub.support.Fixtures.tree;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.mockhub.support.InMemoryDataStore;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CrudServiceTest {

  private final CrudService crud = new CrudService(new InMemoryDataStore());
  private final CrudResource numbered = new CrudResource("customers", "id", CrudResource.IdType.NUMBER);
  private final CrudResource uuids = new CrudResource("v1/orders", "orderId", CrudResource.IdType.UUID);

  @Test
  void generatesIdsAndKeepsTheIdFirst() {
    // When
    CrudService.Result first = crud.create("p", numbered, json("{'name':'Ada'}"));
    CrudService.Result second = crud.create("p", numbered, json("{'name':'Alan'}"));
    CrudService.Result order = crud.create("p", uuids, json("{'total':10}"));

    // Then
    assertThat(((CrudService.Ok) first).record().toString()).isEqualTo("{\"id\":1,\"name\":\"Ada\"}");
    assertThat(((CrudService.Ok) second).record().get("id").asInt()).isEqualTo(2);
    assertThat(((CrudService.Ok) order).record().get("orderId").asText()).hasSize(36);
  }

  @Test
  void generatesShortRandomIds() {
    // Given
    CrudResource tickets = new CrudResource("tickets", "id", CrudResource.IdType.parse("short"));

    // When
    String first = ((CrudService.Ok) crud.create("p", tickets, json("{'title':'a'}"))).record().get("id").asText();
    String second = ((CrudService.Ok) crud.create("p", tickets, json("{'title':'b'}"))).record().get("id").asText();

    // Then
    assertThat(first).matches("[a-z0-9]{10}");
    assertThat(second).matches("[a-z0-9]{10}").isNotEqualTo(first);
    assertThat(CrudResource.IdType.SHORT.id()).isEqualTo("short");
  }

  @Test
  void usesGivenIdsAndRefusesDuplicates() {
    // When
    crud.create("p", numbered, json("{'id':7,'name':'Ada'}"));

    // Then
    assertThat(crud.create("p", numbered, json("{'id':7}"))).isInstanceOf(CrudService.Conflict.class);
    assertThat(((CrudService.Ok) crud.create("p", numbered, json("{'name':'Next'}"))).record().get("id").asInt()).isEqualTo(8);
    assertThat(crud.create("p", numbered, tree("[1]"))).isInstanceOf(CrudService.Invalid.class);
  }

  @Test
  void replacesAndPatchesButKeepsTheId() {
    // Given
    crud.create("p", numbered, json("{'name':'Ada','address':{'city':'London','zip':'N1'},'tag':'x'}"));

    // When
    CrudService.Ok patched = (CrudService.Ok) crud.patch("p", numbered, "1", json("{'id':99,'address':{'city':'Paris'},'tag':null}"));
    CrudService.Ok replaced = (CrudService.Ok) crud.replace("p", numbered, "1", json("{'name':'Ada L.'}"));

    // Then
    assertThat(patched.record().get("id").asInt()).isEqualTo(1);
    assertThat(patched.record().at("/address/city").asText()).isEqualTo("Paris");
    assertThat(patched.record().at("/address/zip").asText()).isEqualTo("N1");
    assertThat(patched.record().has("tag")).isFalse();
    assertThat(replaced.record().toString()).isEqualTo("{\"id\":1,\"name\":\"Ada L.\"}");
    assertThat(crud.patch("p", numbered, "404", json("{}"))).isInstanceOf(CrudService.NotFound.class);
  }

  @Test
  void filtersSortsAndPages() {
    // Given
    crud.seed("p", numbered, tree("[{'name':'Cy','age':30,'team':'a'},{'name':'Ada','age':9,'team':'b'},{'name':'Bo','age':100,'team':'a'}]"));

    // When
    CrudService.Page team = crud.list("p", numbered, query(Map.of("team", "a")));
    CrudService.Page byAge = crud.list("p", numbered, query(Map.of("_sort", "age")));
    CrudService.Page page = crud.list("p", numbered, query(Map.of("_sort", "name", "_order", "desc", "_limit", "1", "_offset", "1")));

    // Then
    assertThat(team.items()).extracting(r -> r.get("name").asText()).containsExactly("Cy", "Bo");
    assertThat(byAge.items()).extracting(r -> r.get("age").asInt()).containsExactly(9, 30, 100);
    assertThat(page.items()).extracting(r -> r.get("name").asText()).containsExactly("Bo");
    assertThat(page.total()).isEqualTo(3);
  }

  @Test
  void validatesResourceNamesAndIdFields() {
    // Then
    assertThat(new CrudResource(" /Customers/ ", null, null).name()).isEqualTo("customers");
    assertThatThrownBy(() -> new CrudResource("bad name", "id", null)).hasMessageContaining("lowercase");
    assertThatThrownBy(() -> new CrudResource("ok", "not valid", null)).hasMessageContaining("plain field name");
  }

  @Test
  void asksThePolicyBeforeStoringARecord() {
    // Given
    RecordPolicy upToTwoSmallRecords = (projectId, records, bytes) ->
        records > 2 ? Optional.of("Two records at most") : bytes > 40 ? Optional.of("Too big") : Optional.empty();
    CrudService limited = new CrudService(new InMemoryDataStore(), upToTwoSmallRecords, CrudService.NO_LIMIT);
    limited.create("p", numbered, json("{'name':'Ada'}"));
    limited.create("p", numbered, json("{'name':'Alan'}"));

    // When
    CrudService.Result third = limited.create("p", numbered, json("{'name':'Grace'}"));
    CrudService.Result bigger = limited.patch("p", numbered, "1", json("{'bio':'A very long biography that will not fit'}"));

    // Then
    assertThat(third).isEqualTo(new CrudService.Refused("Two records at most"));
    assertThat(bigger).isEqualTo(new CrudService.Refused("Too big"));
    assertThat(limited.get("p", numbered, "1").orElseThrow().has("bio")).isFalse();
  }

  @Test
  void dropsTheOldestRecordsPastTheLimit() {
    // Given
    CrudService capped = new CrudService(new InMemoryDataStore(), RecordPolicy.NONE, 2);
    capped.create("p", numbered, json("{'name':'Ada'}"));
    capped.create("p", numbered, json("{'name':'Alan'}"));

    // When
    CrudService.Result third = capped.create("p", numbered, json("{'name':'Grace'}"));

    // Then
    assertThat(third).isInstanceOf(CrudService.Ok.class);
    assertThat(capped.list("p", numbered, CrudQuery.ALL).items()).extracting(r -> r.get("name").asText()).containsExactly("Alan", "Grace");
    assertThat(capped.get("p", numbered, "1")).isEmpty();
  }

  private static CrudQuery query(Map<String, String> params) {
    Map<String, List<String>> multi = new LinkedHashMap<>();
    params.forEach((k, v) -> multi.put(k, List.of(v)));
    return ((CrudQuery.Ok) CrudQuery.parse(multi)).query();
  }
}
