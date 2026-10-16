package io.mockhub.backup;

public record RestoreOptions(BackupSelection selection, String targetOrgId, boolean asCopy) {

  public static RestoreOptions everything() {
    return new RestoreOptions(BackupSelection.all(), null, false);
  }
}
