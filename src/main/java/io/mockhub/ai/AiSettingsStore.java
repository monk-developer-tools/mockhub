package io.mockhub.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import io.mockhub.store.JsonFile;
import java.nio.file.Path;

public final class AiSettingsStore {
  private final JsonFile<AiSettings> file;
  private final String keyOverride;
  private volatile AiSettings current;

  public AiSettingsStore(Path rootDir, String keyOverride) {
    this.file = new JsonFile<>(JsonFile.inDataDir(rootDir, "ai.json"), new TypeReference<>() {});
    this.keyOverride = keyOverride == null ? "" : keyOverride;
    this.current = file.read(AiSettings::disabled);
  }

  public AiSettings get() {
    return keyOverride.isEmpty() ? current : current.withApiKey(keyOverride);
  }

  public boolean keyFromEnvironment() {
    return !keyOverride.isEmpty();
  }

  public synchronized void save(AiSettings settings) {
    AiSettings stored = keyOverride.isEmpty() ? settings : settings.withApiKey("");
    file.write(stored);
    current = stored;
  }
}
