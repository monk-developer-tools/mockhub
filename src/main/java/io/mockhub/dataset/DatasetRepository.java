package io.mockhub.dataset;

import java.util.List;
import java.util.Optional;

public interface DatasetRepository {
  List<StoredDataset> findAll(String orgId);

  Optional<StoredDataset> find(String orgId, String key);

  void save(StoredDataset dataset);

  boolean delete(String orgId, String key);

  void deleteOrg(String orgId);
}
