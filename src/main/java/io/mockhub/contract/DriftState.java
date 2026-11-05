package io.mockhub.contract;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public record DriftState(long checkedUpTo, String lastCheckedAt, long scanned, boolean capped, List<Finding> findings, Set<String> expected,
                         Notice lastNotice) {

  public static final int MAX_FINDINGS = 500;

  public record Finding(String key, DriftKind kind, String method, String path, String detail, long count, String firstSeen, String lastSeen,
                        String exampleId, String exampleUrl, int exampleStatus) {}

  public record Notice(String at, int findings, int status, String error) {}

  public DriftState {
    findings = findings == null ? List.of() : List.copyOf(findings);
    expected = expected == null ? Set.of() : Set.copyOf(expected);
  }

  public static DriftState empty() {
    return new DriftState(0, null, 0, false, List.of(), Set.of(), null);
  }

  public DriftState restarted() {
    return new DriftState(0, null, 0, false, List.of(), expected, lastNotice);
  }

  public DriftState withExpected(String key, boolean value) {
    Set<String> next = new LinkedHashSet<>(expected);
    if (value) next.add(key);
    else next.remove(key);
    return new DriftState(checkedUpTo, lastCheckedAt, scanned, capped, findings, next, lastNotice);
  }

  public DriftState withNotice(Notice notice) {
    return new DriftState(checkedUpTo, lastCheckedAt, scanned, capped, findings, expected, notice);
  }

  public DriftState withFindings(long upTo, String at, long newlyScanned, boolean nowCapped, List<Finding> next) {
    return new DriftState(upTo, at, scanned + newlyScanned, capped || nowCapped, new ArrayList<>(next), expected, lastNotice);
  }
}
