package io.mockhub.data;

import static io.mockhub.support.Fixtures.tree;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CrudQueryTest {

  private final List<ObjectNode> products = records("""
      [{'id':1,'name':'Pen','price':2.5,'stock':100,'active':true,'brand':{'country':'UK'},'tags':['office','cheap']},
       {'id':2,'name':'desk','price':180,'stock':4,'active':false,'brand':{'country':'SE'},'tags':['office']},
       {'id':3,'name':'Lamp','price':35,'stock':0,'active':true,'brand':{'country':'UK'},'tags':['home']},
       {'id':4,'name':'Chair','price':95,'active':true,'brand':{'country':'DE'},'tags':[]}]""");

  @Test
  void plainValuesMatchExactlyAndRepeatedValuesMeanOr() {
    // Given
    Map<String, List<String>> params = Map.of("brand.country", List.of("UK", "DE"));

    // When
    List<ObjectNode> found = ok(params).apply(products);

    // Then
    assertThat(ids(found)).containsExactly(1, 3, 4);
    assertThat(ids(ok(Map.of("name", List.of("pen"))).apply(products))).isEmpty();
  }

  @Test
  void comparisonOperatorsCompareNumbersAsNumbers() {
    // Given
    Map<String, List<String>> range = params("price[gte]", "35", "price[lt]", "180");

    // When
    List<ObjectNode> found = ok(range).apply(products);

    // Then
    assertThat(ids(found)).containsExactly(3, 4);
    assertThat(ids(ok(params("stock[gt]", "4")).apply(products))).containsExactly(1);
    assertThat(ids(ok(params("stock[lte]", "4")).apply(products))).containsExactly(2, 3);
  }

  @Test
  void notEqualKeepsRecordsWithoutTheField() {
    // Given
    Map<String, List<String>> params = params("stock[ne]", "0");

    // When
    List<ObjectNode> found = ok(params).apply(products);

    // Then
    assertThat(ids(found)).containsExactly(1, 2, 4);
  }

  @Test
  void booleansNumbersAndArraysMatchTheirText() {
    // Given
    Map<String, List<String>> inactive = params("active", "false");

    // When
    List<ObjectNode> found = ok(inactive).apply(products);

    // Then
    assertThat(ids(found)).containsExactly(2);
    assertThat(ids(ok(params("price", "180.0")).apply(products))).containsExactly(2);
    assertThat(ids(ok(params("tags", "office")).apply(products))).containsExactly(1, 2);
  }

  @Test
  void sortsByManyFieldsWithAMinusForDescending() {
    // Given
    Map<String, List<String>> params = params("sort", "-active,name");

    // When
    List<ObjectNode> found = ok(params).apply(products);

    // Then
    assertThat(ids(found)).containsExactly(4, 3, 1, 2);
    assertThat(ids(ok(params("_sort", "price", "_order", "desc")).apply(products))).containsExactly(2, 4, 3, 1);
  }

  @Test
  void pagesWithPageAndSizeOrLimitAndOffset() {
    // Given
    Map<String, List<String>> pages = params("page", "2", "size", "3");

    // When
    CrudQuery query = ok(pages);

    // Then
    assertThat(query.offset()).isEqualTo(3);
    assertThat(query.limit()).isEqualTo(3);
    assertThat(ok(params("limit", "2", "offset", "1")).offset()).isEqualTo(1);
    assertThat(ok(params("_limit", "5")).limit()).isEqualTo(5);
    assertThat(ok(params("page", "1")).limit()).isEqualTo(20);
  }

  @Test
  void refusesUnknownOperatorsAndBadNumbers() {
    // Given
    Map<String, List<String>> unknown = params("price[between]", "1");

    // When
    CrudQuery.Parsed parsed = CrudQuery.parse(unknown);

    // Then
    assertThat(parsed).isInstanceOf(CrudQuery.Invalid.class);
    assertThat(((CrudQuery.Invalid) parsed).message()).contains("price[between]").contains("gte");
    assertThat(CrudQuery.parse(params("page", "0"))).isInstanceOf(CrudQuery.Invalid.class);
    assertThat(CrudQuery.parse(params("limit", "ten"))).isInstanceOf(CrudQuery.Invalid.class);
  }

  private static CrudQuery ok(Map<String, List<String>> params) {
    return ((CrudQuery.Ok) CrudQuery.parse(params)).query();
  }

  private static Map<String, List<String>> params(String... pairs) {
    Map<String, List<String>> map = new LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) map.put(pairs[i], List.of(pairs[i + 1]));
    return map;
  }

  private static List<ObjectNode> records(String json) {
    List<ObjectNode> list = new ArrayList<>();
    tree(json).forEach(n -> list.add((ObjectNode) n));
    return list;
  }

  private static List<Integer> ids(List<ObjectNode> records) {
    return records.stream().map(r -> r.get("id").asInt()).toList();
  }
}
