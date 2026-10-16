package io.mockhub.backup;

public record RestoreOptions(BackupSelection selection, String targetOrgId, String targetTeamId, boolean asCopy) {

  public RestoreOptions(BackupSelection selection, String targetOrgId, boolean asCopy) {
    this(selection, targetOrgId, null, asCopy);
  }

  public static RestoreOptions everything() {
    return new RestoreOptions(BackupSelection.all(), null, null, false);
  }
}
