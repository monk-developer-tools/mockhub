package io.mockhub.dataset;

import java.util.List;

public record Dataset(String orgId, String key, String name, String description, List<String> identity, List<String> indexes,
    List<Column> columns, int recordCount, long bytes, String createdAt, String updatedAt) {

  public Dataset {
    identity = identity == null ? List.of() : List.copyOf(identity);
    indexes = indexes == null ? List.of() : List.copyOf(indexes);
    columns = columns == null ? List.of() : List.copyOf(columns);
    description = description == null ? "" : description;
  }

  public Dataset withSettings(String name, String description, List<String> identity, List<String> indexes, String updatedAt) {
    return new Dataset(orgId, key, name, description, identity, indexes, columns, recordCount, bytes, createdAt, updatedAt);
  }

  public Dataset withRecords(List<Column> columns, int recordCount, long bytes, String updatedAt) {
    return new Dataset(orgId, key, name, description, identity, indexes, columns, recordCount, bytes, createdAt, updatedAt);
  }
}
