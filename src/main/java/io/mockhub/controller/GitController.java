package io.mockhub.controller;

import io.mockhub.api.ApiRouter;
import io.mockhub.auth.Principal;
import io.mockhub.git.GitSyncService;

public final class GitController implements ApiController {
  private final GitSyncService git;

  public GitController(GitSyncService git) {
    this.git = git;
  }

  @Override
  public void register(ApiRouter r) {
    r.get("/projects/{id}/git", ctx -> git.view(ctx.path("id")));
    r.put("/projects/{id}/git", ctx -> git.link(ctx.path("id"), ctx.json()));
    r.delete("/projects/{id}/git", ctx -> {
      git.unlink(ctx.path("id"));
      return null;
    });
    r.post("/projects/{id}/git/push", ctx -> {
      String body = ctx.body();
      String message = body == null || body.isBlank() ? null : ctx.json().path("message").asText(null);
      return git.push(ctx.path("id"), message, person(ctx.principal()));
    });
    r.get("/projects/{id}/git/preview", ctx -> git.preview(ctx.path("id")));
    r.post("/projects/{id}/git/pull", ctx -> git.pull(ctx.path("id"), ctx.principal().label()));
  }

  private static GitSyncService.Person person(Principal principal) {
    String name = principal.username() == null || principal.username().isBlank() ? "MockHub" : principal.username();
    String email = principal.user() != null && principal.user().email() != null && !principal.user().email().isBlank()
        ? principal.user().email() : name.replaceAll("[^A-Za-z0-9._-]", "") + "@users.mockhub";
    return new GitSyncService.Person(principal.viaToken() ? principal.label() : name, email);
  }
}
