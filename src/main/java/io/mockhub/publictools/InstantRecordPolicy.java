package io.mockhub.publictools;

import io.mockhub.data.RecordPolicy;
import java.util.Optional;
import java.util.function.Predicate;

public final class InstantRecordPolicy implements RecordPolicy {
  public static final int MAX_RECORDS = 200;
  public static final int MAX_RECORD_BYTES = 4 * 1024;

  private final Predicate<String> isInstant;

  public InstantRecordPolicy(Predicate<String> isInstant) {
    this.isInstant = isInstant;
  }

  @Override
  public Optional<String> refuse(String projectId, int recordsInProject, int recordBytes) {
    if (!isInstant.test(projectId)) return Optional.empty();
    if (recordBytes > MAX_RECORD_BYTES) return Optional.of("Records in instant mocks are limited to 4 KB each");
    if (recordsInProject > MAX_RECORDS) return Optional.of("An instant mock holds up to " + MAX_RECORDS + " records. Delete some to add more.");
    return Optional.empty();
  }
}
