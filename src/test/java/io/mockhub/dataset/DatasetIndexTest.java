package io.mockhub.dataset;

import static io.mockhub.support.Fixtures.json;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DatasetIndexTest {

  private final List<ObjectNode> records = List.of(
      json("{'id':1,'city':'London','zip':'007','active':true,'tags':['vip','eu'],'address':{'country':'UK'}}"),
      json("{'id':2.0,'city':'Paris','zip':7,'active':false,'tags':['eu'],'address':{'country':'FR'}}"),
      json("{'id':'3','city':'London','active':false}"));

  @Test
  void findsByIndexedAndPlainFieldsTheSameWay() {
    // Given
    DatasetIndex indexed = new DatasetIndex(records, List.of("id", "city", "tags"));
    DatasetIndex plain = new DatasetIndex(records, List.of());

    for (DatasetIndex index : List.of(indexed, plain)) {
      // Then
      assertThat(ids(index.find(Map.of("city", "London"), 0, 10))).containsExactly("1", "3");
      assertThat(ids(index.find(Map.of("id", "2"), 0, 10))).containsExactly("2.0");
      assertThat(ids(index.find(Map.of("id", "3"), 0, 10))).containsExactly("3");
      assertThat(ids(index.find(Map.of("tags", "eu", "active", "FALSE"), 0, 10))).containsExactly("2.0");
      assertThat(ids(index.find(Map.of("address.country", "UK"), 0, 10))).containsExactly("1");
      assertThat(ids(index.find(Map.of("zip", "7"), 0, 10))).containsExactly("2.0");
      assertThat(ids(index.find(Map.of("zip", "007"), 0, 10))).containsExactly("1", "2.0");
      assertThat(ids(index.find(Map.of("city", "London"), 1, 10))).containsExactly("3");
      assertThat(index.count(Map.of("city", "London"))).isEqualTo(2);
      assertThat(index.find(Map.of("city", "Rome"), 0, 10)).isEmpty();
    }
  }

  private static List<String> ids(List<ObjectNode> found) {
    return found.stream().map(r -> r.get("id").asText()).toList();
  }
}
