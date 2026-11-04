package io.mockhub.dataset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.mockhub.api.ApiException;
import java.util.List;
import org.junit.jupiter.api.Test;

class DatasetParserTest {

  @Test
  void typesCsvColumnsFromTheHeaderOrTheirValues() {
    // Given
    String csv = "﻿id,zip,price,active,tags:json,note:string\n1,01234,9.50,TRUE,\"[\"\"a\"\"]\",7\r\n2,99999,10,false,[],\n";

    // When
    DatasetParser.Parsed parsed = DatasetParser.parse(DatasetFormat.CSV, csv);

    // Then
    assertThat(parsed.columns()).containsExactly(new Column("id", ColumnType.NUMBER), new Column("zip", ColumnType.STRING),
        new Column("price", ColumnType.NUMBER), new Column("active", ColumnType.BOOLEAN), new Column("tags", ColumnType.JSON),
        new Column("note", ColumnType.STRING));
    assertThat(parsed.records().get(0).toString()).isEqualTo("{\"id\":1,\"zip\":\"01234\",\"price\":9.5,\"active\":true,\"tags\":[\"a\"],\"note\":\"7\"}");
    assertThat(parsed.records().get(1).toString()).isEqualTo("{\"id\":2,\"zip\":\"99999\",\"price\":10,\"active\":false,\"tags\":[],\"note\":\"\"}");
  }

  @Test
  void keepsCommasQuotesAndLineBreaksInsideQuotedCsvValues() {
    // When
    DatasetParser.Parsed parsed = DatasetParser.parse(DatasetFormat.CSV, "name,address\n\"Lovelace, Ada\",\"12 St James's Square\nLondon\"\n");

    // Then
    assertThat(parsed.records()).hasSize(1);
    assertThat(parsed.records().get(0).get("name").asText()).isEqualTo("Lovelace, Ada");
    assertThat(parsed.records().get(0).get("address").asText()).isEqualTo("12 St James's Square\nLondon");
  }

  @Test
  void explainsCsvMistakesWithTheLine() {
    // Then
    assertThatThrownBy(() -> DatasetParser.parse(DatasetFormat.CSV, "a,b\n1,2,3\n")).isInstanceOf(ApiException.class)
        .hasMessage("Line 2 has 3 values but the header has 2 columns");
    assertThatThrownBy(() -> DatasetParser.parse(DatasetFormat.CSV, "a:number\n1\nx\n")).hasMessage("Line 3, column a: x isn't a number");
    assertThatThrownBy(() -> DatasetParser.parse(DatasetFormat.CSV, "a,a\n")).hasMessage("Two columns are called a");
    assertThatThrownBy(() -> DatasetParser.parse(DatasetFormat.CSV, "a\n\"open\n")).hasMessage("Line 2: a quoted value is never closed");
    assertThatThrownBy(() -> DatasetParser.parse(DatasetFormat.CSV, "")).hasMessageContaining("empty");
  }

  @Test
  void readsJsonArraysAndNdjsonLines() {
    // When
    DatasetParser.Parsed json = DatasetParser.parse(DatasetFormat.JSON, "[{\"id\":1,\"name\":\"Ada\"},{\"id\":2,\"name\":null,\"meta\":{}}]");
    DatasetParser.Parsed ndjson = DatasetParser.parse(DatasetFormat.NDJSON, "{\"id\":1}\n\n{\"id\":\"x\"}\n");

    // Then
    assertThat(json.records()).hasSize(2);
    assertThat(json.columns()).containsExactly(new Column("id", ColumnType.NUMBER), new Column("name", ColumnType.STRING),
        new Column("meta", ColumnType.JSON));
    assertThat(ndjson.records()).hasSize(2);
    assertThat(ndjson.columns()).isEqualTo(List.of(new Column("id", ColumnType.JSON)));
    assertThatThrownBy(() -> DatasetParser.parse(DatasetFormat.JSON, "{\"id\":1}")).hasMessageContaining("JSON array of objects");
    assertThatThrownBy(() -> DatasetParser.parse(DatasetFormat.JSON, "[1]")).hasMessage("Item 1 isn't an object");
    assertThatThrownBy(() -> DatasetParser.parse(DatasetFormat.NDJSON, "{}\n{oops\n")).hasMessage("Line 2 isn't valid JSON");
  }
}
