package io.mockhub.dataset;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;

public record StoredDataset(Dataset dataset, List<ObjectNode> records) {
  public StoredDataset {
    records = records == null ? List.of() : List.copyOf(records);
  }
}
