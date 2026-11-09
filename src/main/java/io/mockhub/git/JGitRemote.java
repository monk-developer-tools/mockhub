package io.mockhub.git;

import io.mockhub.api.ApiException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Stream;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.TransportCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

public final class JGitRemote implements GitRemote {
  private static final int TIMEOUT_SECONDS = 30;
  private static final List<String> OWNED = List.of(GitLayout.MANIFEST, GitLayout.MAPPINGS, GitLayout.FILES);

  private final Path workRoot;

  public JGitRemote(Path workRoot) {
    this.workRoot = workRoot;
  }

  @Override
  public Contents read(Target target, String folder) {
    Path dir = scratch();
    try {
      if (!branchExists(target)) throw new ApiException(404, "The repository has no branch " + target.branch() + " yet. Push first to create it.");
      try (Git git = clone(target, dir)) {
        return new Contents(collect(dir.resolve(folder)), git.getRepository().resolve("HEAD").name());
      }
    } catch (GitAPIException | IOException e) {
      throw failure(e);
    } finally {
      delete(dir);
    }
  }

  @Override
  public Pushed write(Target target, String folder, Map<String, byte[]> files, String message, String author, String email) {
    Path dir = scratch();
    try (Git git = branchExists(target) ? clone(target, dir) : init(target, dir)) {
      Path base = dir.resolve(folder);
      for (String owned : OWNED) delete(base.resolve(owned));
      for (Map.Entry<String, byte[]> file : files.entrySet()) {
        Path path = base.resolve(file.getKey()).normalize();
        if (!path.startsWith(base)) throw ApiException.badRequest("File " + file.getKey() + " would be written outside the folder");
        Files.createDirectories(path.getParent());
        Files.write(path, file.getValue());
      }
      String pattern = folder.isEmpty() ? "." : folder;
      git.add().addFilepattern(pattern).call();
      git.add().setUpdate(true).addFilepattern(pattern).call();
      if (git.status().call().isClean()) {
        Ref head = git.getRepository().exactRef("HEAD");
        return new Pushed(head == null || head.getObjectId() == null ? null : head.getObjectId().name(), false);
      }
      PersonIdent who = new PersonIdent(author, email);
      RevCommit commit = git.commit().setMessage(message).setAuthor(who).setCommitter(who).setSign(false).call();
      PushCommand push = git.push().setRemote("origin").setRefSpecs(new org.eclipse.jgit.transport.RefSpec("HEAD:refs/heads/" + target.branch()));
      for (PushResult result : auth(push, target).call()) {
        for (RemoteRefUpdate update : result.getRemoteUpdates()) {
          RemoteRefUpdate.Status status = update.getStatus();
          if (status == RemoteRefUpdate.Status.REJECTED_NONFASTFORWARD || status == RemoteRefUpdate.Status.REJECTED_REMOTE_CHANGED) {
            throw new ApiException(409, "Someone pushed to " + target.branch() + " at the same time. Push again.");
          }
          if (status != RemoteRefUpdate.Status.OK && status != RemoteRefUpdate.Status.UP_TO_DATE) {
            throw new ApiException(502, "The repository refused the push: " + status + (update.getMessage() == null ? "" : " " + update.getMessage()));
          }
        }
      }
      return new Pushed(commit.getId().name(), true);
    } catch (GitAPIException | IOException | URISyntaxException e) {
      throw failure(e);
    } finally {
      delete(dir);
    }
  }

  private boolean branchExists(Target target) throws GitAPIException {
    return auth(Git.lsRemoteRepository().setRemote(target.url()).setHeads(true), target).call().stream()
        .anyMatch(ref -> ref.getName().equals("refs/heads/" + target.branch()));
  }

  private Git clone(Target target, Path dir) throws GitAPIException {
    return auth(Git.cloneRepository().setURI(target.url()).setDirectory(dir.toFile()).setBranch(target.branch())
        .setCloneAllBranches(false).setDepth(1), target).call();
  }

  private Git init(Target target, Path dir) throws GitAPIException, URISyntaxException {
    Git git = Git.init().setDirectory(dir.toFile()).setInitialBranch(target.branch()).call();
    git.remoteAdd().setName("origin").setUri(new URIish(target.url())).call();
    return git;
  }

  private static <C extends TransportCommand<C, ?>> C auth(C command, Target target) {
    command.setTimeout(TIMEOUT_SECONDS);
    if (target.token() != null && !target.token().isBlank()) {
      String user = target.username() == null || target.username().isBlank() ? "mockhub" : target.username();
      command.setCredentialsProvider(new UsernamePasswordCredentialsProvider(user, target.token()));
    }
    return command;
  }

  private static Map<String, byte[]> collect(Path base) throws IOException {
    Map<String, byte[]> out = new TreeMap<>();
    for (String owned : OWNED) {
      Path path = base.resolve(owned);
      if (Files.isRegularFile(path)) {
        out.put(owned, Files.readAllBytes(path));
      } else if (Files.isDirectory(path)) {
        try (Stream<Path> walk = Files.walk(path)) {
          for (Path file : walk.filter(Files::isRegularFile).toList()) {
            out.put(base.relativize(file).toString().replace('\\', '/'), Files.readAllBytes(file));
          }
        }
      }
    }
    return out;
  }

  private static ApiException failure(Exception e) {
    if (e instanceof TransportException) {
      String message = e.getMessage() == null ? "" : e.getMessage();
      if (message.contains("not authorized") || message.contains("Authentication") || message.contains("401") || message.contains("403")) {
        return new ApiException(502, "The repository refused the token. Check that it can read and write this repository.");
      }
      return new ApiException(502, "Can't reach the repository: " + message);
    }
    return new ApiException(502, "Git failed: " + e.getMessage());
  }

  private Path scratch() {
    try {
      Files.createDirectories(workRoot);
      return Files.createDirectory(workRoot.resolve(UUID.randomUUID().toString()));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static void delete(Path path) {
    if (!Files.exists(path)) return;
    try (Stream<Path> walk = Files.walk(path)) {
      walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
