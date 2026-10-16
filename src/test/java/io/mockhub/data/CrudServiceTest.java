package io.mockhub.data;

import static io.mockhub.support.Fixtures.json;
import static io.mockhub.support.Fixtures.tree;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.mockhub.support.InMemoryDataStore;
import java.util.Map;
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
    CrudService.Page team = crud.list("p", numbered, Map.of("team", "a"));
    CrudService.Page byAge = crud.list("p", numbered, Map.of("_sort", "age"));
    CrudService.Page page = crud.list("p", numbered, Map.of("_sort", "name", "_order", "desc", "_limit", "1", "_offset", "1"));

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
}
