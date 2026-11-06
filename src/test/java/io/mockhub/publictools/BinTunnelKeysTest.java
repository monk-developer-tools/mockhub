package io.mockhub.publictools;

import static org.assertj.core.api.Assertions.assertThat;

import io.mockhub.auth.Principal;
import io.mockhub.support.MutableClock;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BinTunnelKeysTest {
  @TempDir
  Path data;

  @Test
  void aBinKeyActsAsTheBinItselfAndNothingElse() {
    // Given
    Bins bins = new Bins(data, new MutableClock(Instant.parse("2026-11-06T10:00:00Z")));
    Bins.Created bin = bins.create().orElseThrow();
    BinTunnelKeys keys = new BinTunnelKeys(bins);

    // When
    Optional<Principal> good = keys.principal("Bearer bin_" + bin.id() + "_" + bin.token());
    Optional<Principal> wrongToken = keys.principal("Bearer bin_" + bin.id() + "_" + "a".repeat(32));
    Optional<Principal> userToken = keys.principal("Bearer mh_something");

    // Then
    assertThat(good).hasValueSatisfying(p -> {
      assertThat(p.id()).isEqualTo("bin:" + bin.id());
      assertThat(BinTunnelKeys.isBinOwner(p)).isTrue();
      assertThat(BinTunnelKeys.tunnelFor(p)).isEqualTo("bin-" + bin.id());
    });
    assertThat(wrongToken).isEmpty();
    assertThat(userToken).isEmpty();
    assertThat(keys.principal(null)).isEmpty();
  }
}
