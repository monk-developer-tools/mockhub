package io.mockhub.git;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.api.ApiException;
import io.mockhub.api.Jsons;
import io.mockhub.connection.SecretBox;
import io.mockhub.playground.PlaygroundException;
import io.mockhub.playground.TargetGuard;
import io.mockhub.project.Project;
import io.mockhub.project.ProjectService;
import io.mockhub.snapshot.Snapshot;
import io.mockhub.snapshot.SnapshotService;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.regex.Pattern;

public final class GitSyncService {
  private static final Pattern BRANCH = Pattern.compile("[A-Za-z0-9._][A-Za-z0-9._/-]{0,99}");
  private static final Pattern FOLDER = Pattern.compile("[A-Za-z0-9._-]+(/[A-Za-z0-9._-]+)*");

  public record Person(String name, String email) {}

  private final GitLinkStore links;
  private final GitRemote remote;
  private final SnapshotService snapshots;
  private final ProjectService projects;
  private final SecretBox secrets;
  private final TargetGuard guard;
  private final boolean allowPrivate;
  private final Clock clock;

  public GitSyncService(GitLinkStore links, GitRemote remote, SnapshotService snapshots, ProjectService projects, SecretBox secrets,
      TargetGuard guard, boolean allowPrivate, Clock clock) {
    this.links = links;
    this.remote = remote;
    this.snapshots = snapshots;
    this.projects = projects;
    this.secrets = secrets;
    this.guard = guard;
    this.allowPrivate = allowPrivate;
    this.clock = clock;
  }

  public ObjectNode view(String projectId) {
    Project p = projects.get(projectId);
    return links.find(p.id()).map(GitSyncService::view).orElseGet(() -> Jsons.object().put("linked", false)
        .put("suggestedFolder", "mockhub/" + p.slug()));
  }

  public synchronized ObjectNode link(String projectId, JsonNode body) {
    Project p = projects.get(projectId);
    if (body == null || !body.isObject()) throw ApiException.badRequest("Send {url, branch, folder, token}");
    String url = checkUrl(body.path("url").asText("").trim());
    String branch = body.path("branch").asText("").trim();
    if (branch.isEmpty()) branch = "main";
    if (!BRANCH.matcher(branch).matches() || branch.contains("..") || branch.endsWith("/") || branch.endsWith(".lock")) {
      throw ApiException.badRequest("'" + branch + "' isn't a valid branch name");
    }
    String folder = folder(body.has("folder") ? body.path("folder").asText("") : "mockhub/" + p.slug());
    String username = body.path("username").asText("").trim();
    GitLink existing = links.find(p.id()).orElse(null);
    String token = body.path("token").asText("").trim();
    String sealed = !token.isEmpty() ? secrets.seal(token) : existing != null && existing.url().equals(url) ? existing.sealedToken() : null;
    GitLink link = new GitLink(url, branch, folder, username, sealed,
        existing == null ? null : existing.lastPush(), existing == null ? null : existing.lastPull());
    links.save(p.id(), link);
    return view(link);
  }

  public synchronized void unlink(String projectId) {
    if (!links.delete(projects.get(projectId).id())) throw ApiException.notFound("This project has no Git repository");
  }

  public synchronized ObjectNode push(String projectId, String message, Person by) {
    Project p = projects.get(projectId);
    GitLink link = linked(p);
    Snapshot current = snapshots.current(p.id());
    String text = message == null || message.isBlank() ? "Update " + p.name() + " mocks from MockHub" : message.trim();
    GitRemote.Pushed pushed = remote.write(target(link), link.folder(), GitLayout.write(current), text, by.name(), by.email());
    if (pushed.changed()) links.save(p.id(), link.pushed(new GitLink.Event(now(), by.name(), pushed.commit())));
    return Jsons.object().put("changed", pushed.changed()).put("commit", pushed.commit()).put("stubs", current.stubs().size())
        .put("files", current.files().size());
  }

  public ObjectNode preview(String projectId) {
    Project p = projects.get(projectId);
    GitLink link = linked(p);
    GitRemote.Contents contents = remote.read(target(link), link.folder());
    ObjectNode diff = snapshots.compareWith(p.id(), incoming(link, contents));
    diff.put("commit", contents.commit());
    return diff;
  }

  public synchronized ObjectNode pull(String projectId, String by) {
    Project p = projects.get(projectId);
    GitLink link = linked(p);
    GitRemote.Contents contents = remote.read(target(link), link.folder());
    SnapshotService.Restored restored = snapshots.replace(p.id(), incoming(link, contents), by, "pulling from Git");
    links.save(p.id(), link.pulled(new GitLink.Event(now(), by, contents.commit())));
    ObjectNode out = Jsons.object().put("commit", contents.commit()).put("stubs", restored.stubs()).put("files", restored.files());
    out.set("safety", Jsons.MAPPER.valueToTree(restored.safety()));
    return out;
  }

  private Snapshot incoming(GitLink link, GitRemote.Contents contents) {
    if (contents.files().isEmpty()) {
      throw ApiException.badRequest("Folder " + (link.folder().isEmpty() ? "/" : link.folder()) + " on " + link.branch()
          + " has no mockhub.json, mappings or __files. Push first, or check the folder.");
    }
    String commit = contents.commit() == null ? "" : " " + contents.commit().substring(0, Math.min(7, contents.commit().length()));
    return GitLayout.read(contents.files(), link.branch() + commit);
  }

  private GitLink linked(Project p) {
    return links.find(p.id()).orElseThrow(() -> new ApiException(409, "Link a Git repository to this project first"));
  }

  private GitRemote.Target target(GitLink link) {
    String token = link.sealedToken() == null ? null : secrets.open(link.sealedToken());
    return new GitRemote.Target(link.url(), link.branch(), link.username(), token);
  }

  private String checkUrl(String url) {
    if (url.isEmpty()) throw ApiException.badRequest("Give the repository's HTTPS URL, such as https://github.com/acme/mocks.git");
    if (url.matches("[A-Za-z0-9._-]+@[A-Za-z0-9._-]+:.*") || url.toLowerCase(Locale.ROOT).startsWith("ssh:")) {
      throw ApiException.badRequest("SSH URLs aren't supported; use the repository's https:// URL and an access token");
    }
    URI uri;
    try {
      uri = new URI(url);
    } catch (URISyntaxException e) {
      throw ApiException.badRequest("'" + url + "' isn't a valid URL");
    }
    if (uri.getUserInfo() != null) throw ApiException.badRequest("Put the token in the token field, not in the URL");
    String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
    if (scheme.equals("file") && allowPrivate) return url;
    if (!scheme.equals("https") && !(scheme.equals("http") && allowPrivate)) {
      throw ApiException.badRequest("Use the repository's https:// URL; SSH URLs aren't supported");
    }
    try {
      guard.check(uri);
    } catch (PlaygroundException e) {
      throw ApiException.badRequest(e.getMessage());
    }
    return url;
  }

  static String folder(String raw) {
    String folder = raw.trim().replaceAll("^/+|/+$", "");
    if (folder.equals(".")) folder = "";
    if (!folder.isEmpty() && (!FOLDER.matcher(folder).matches() || folder.contains(".."))) {
      throw ApiException.badRequest("The folder must be a path inside the repository, such as mockhub/orders");
    }
    return folder;
  }

  private static ObjectNode view(GitLink link) {
    ObjectNode out = Jsons.object().put("linked", true).put("url", link.url()).put("branch", link.branch()).put("folder", link.folder())
        .put("username", link.username()).put("tokenSet", link.sealedToken() != null);
    if (link.lastPush() != null) out.set("lastPush", Jsons.MAPPER.valueToTree(link.lastPush()));
    if (link.lastPull() != null) out.set("lastPull", Jsons.MAPPER.valueToTree(link.lastPull()));
    return out;
  }

  private String now() {
    return Instant.now(clock).toString();
  }
}
