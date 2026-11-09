package io.mockhub.git;

import java.util.Map;

public interface GitRemote {

  record Target(String url, String branch, String username, String token) {}

  record Contents(Map<String, byte[]> files, String commit) {}

  record Pushed(String commit, boolean changed) {}

  Contents read(Target target, String folder);

  Pushed write(Target target, String folder, Map<String, byte[]> files, String message, String author, String email);
}
