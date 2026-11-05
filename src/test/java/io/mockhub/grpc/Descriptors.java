package io.mockhub.grpc;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

final class Descriptors {
  static final byte[] LIBRARY = read("library.dsc");
  static final byte[] LIBRARY_WITHOUT_IMPORTS = read("library-no-imports.dsc");
  static final byte[] OTHER = read("other.dsc");

  private Descriptors() {}

  private static byte[] read(String name) {
    try (InputStream in = Descriptors.class.getResourceAsStream("/grpc/" + name)) {
      return in.readAllBytes();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
