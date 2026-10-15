package io.mockhub.store;

import com.fasterxml.jackson.core.type.TypeReference;
import io.mockhub.api.Jsons;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Supplier;

public final class JsonFile<T> {
  private final Path file;
  private final TypeReference<T> type;

  public JsonFile(Path file, TypeReference<T> type) {
    this.file = file;
    this.type = type;
  }

  public static Path inDataDir(Path rootDir, String name) {
    return rootDir.resolve("mockhub").resolve(name);
  }

  public T read(Supplier<T> fallback) {
    if (!Files.exists(file)) return fallback.get();
    try {
      T value = Jsons.MAPPER.readValue(file.toFile(), type);
      return value == null ? fallback.get() : value;
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot read " + file, e);
    }
  }

  public void write(T value) {
    try {
      Files.createDirectories(file.getParent());
      Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
      Jsons.MAPPER.writeValue(tmp.toFile(), value);
      Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot write " + file, e);
    }
  }
}
