package io.mockhub.sso;

import com.fasterxml.jackson.core.type.TypeReference;
import io.mockhub.store.JsonFile;
import java.nio.file.Path;

public final class SsoSettingsStore {
  private final JsonFile<SsoSettings> file;
  private final String secretOverride;
  private volatile SsoSettings current;

  public SsoSettingsStore(Path rootDir, String secretOverride) {
    this.file = new JsonFile<>(JsonFile.inDataDir(rootDir, "sso.json"), new TypeReference<>() {});
    this.secretOverride = secretOverride == null ? "" : secretOverride;
    this.current = file.read(SsoSettings::disabled);
  }

  public SsoSettings get() {
    return secretOverride.isEmpty() ? current : current.withClientSecret(secretOverride);
  }

  public boolean secretFromEnvironment() {
    return !secretOverride.isEmpty();
  }

  public synchronized void save(SsoSettings settings) {
    SsoSettings stored = secretOverride.isEmpty() ? settings : settings.withClientSecret("");
    file.write(stored);
    current = stored;
  }

  public boolean active() {
    return get().active();
  }
}
