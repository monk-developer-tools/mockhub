package io.mockhub.git;

public record GitLink(String url, String branch, String folder, String username, String sealedToken, Event lastPush, Event lastPull) {

  public record Event(String at, String by, String commit) {}

  public GitLink withToken(String sealed) {
    return new GitLink(url, branch, folder, username, sealed, lastPush, lastPull);
  }

  public GitLink pushed(Event event) {
    return new GitLink(url, branch, folder, username, sealedToken, event, lastPull);
  }

  public GitLink pulled(Event event) {
    return new GitLink(url, branch, folder, username, sealedToken, lastPush, event);
  }
}
