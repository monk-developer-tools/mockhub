package io.mockhub.ha;

import io.mockhub.api.Jsons;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class HandoverRequests {
  private final Path file;

  public HandoverRequests(Path file) {
    this.file = file;
  }

  public void request(String by, long atMillis) {
    try {
      Files.createDirectories(file.getParent());
      Path tmp = file.resolveSibling(file.getFileName() + "." + by + ".tmp");
      Files.writeString(tmp, Jsons.write(Jsons.object().put("by", by).put("at", atMillis)));
      Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (IOException e) {
      throw new UncheckedIOException("Can't ask the leader to hand over through " + file, e);
    }
  }

  public boolean requestedAfter(long sinceMillis, String self) {
    if (!Files.exists(file)) return false;
    try {
      var node = Jsons.MAPPER.readTree(Files.readString(file));
      return node.path("at").asLong(0) > sinceMillis && !self.equals(node.path("by").asText(""));
    } catch (IOException e) {
      return false;
    }
  }
}
