package io.mockhub.tls;

import io.mockhub.connection.SecretBox;
import io.mockhub.store.JsonFile;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

public final class TlsStore {
  private static final String SERVER = "server.pem.sealed";
  private static final String CLIENTS = "client-ca.pem";

  private final Path dir;
  private final SecretBox secrets;

  public TlsStore(Path rootDir, SecretBox secrets) {
    this.dir = JsonFile.inDataDir(rootDir, "tls");
    this.secrets = secrets;
  }

  public synchronized Optional<String> server() {
    return read(SERVER).map(secrets::open);
  }

  public synchronized void saveServer(String pem) {
    write(SERVER, secrets.seal(pem));
  }

  public synchronized void deleteServer() {
    delete(SERVER);
  }

  public synchronized Optional<String> clients() {
    return read(CLIENTS);
  }

  public synchronized void saveClients(String pem) {
    write(CLIENTS, pem);
  }

  public synchronized void deleteClients() {
    delete(CLIENTS);
  }

  private Optional<String> read(String name) {
    Path file = dir.resolve(name);
    if (!Files.exists(file)) return Optional.empty();
    try {
      return Optional.of(Files.readString(file, StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private void write(String name, String content) {
    try {
      Files.createDirectories(dir);
      Path tmp = dir.resolve(name + ".tmp");
      Files.writeString(tmp, content, StandardCharsets.UTF_8);
      Files.move(tmp, dir.resolve(name), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private void delete(String name) {
    try {
      Files.deleteIfExists(dir.resolve(name));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
