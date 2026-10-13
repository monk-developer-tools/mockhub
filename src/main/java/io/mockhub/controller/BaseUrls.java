package io.mockhub.controller;

import io.mockhub.api.Ctx;
import io.mockhub.project.Project;

public final class BaseUrls {
  private final String configuredPublicUrl;

  public BaseUrls(String configuredPublicUrl) {
    this.configuredPublicUrl = configuredPublicUrl;
  }

  public String server(Ctx ctx) {
    return ctx.baseUrl(configuredPublicUrl);
  }

  public String mock(Ctx ctx, Project project) {
    return server(ctx) + project.basePath();
  }
}
