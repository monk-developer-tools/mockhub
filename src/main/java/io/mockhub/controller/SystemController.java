package io.mockhub.controller;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.core.Version;
import io.mockhub.Main;
import io.mockhub.api.ApiException;
import io.mockhub.api.ApiRouter;
import io.mockhub.api.Jsons;
import io.mockhub.ext.AuthFilter;

public final class SystemController implements ApiController {
  private final AuthFilter auth;
  private final BaseUrls urls;
  private final String dataDir;
  private final int journalMaxEntries;
  private final String loginNote;

  public SystemController(AuthFilter auth, BaseUrls urls, String dataDir, int journalMaxEntries, String loginNote) {
    this.auth = auth;
    this.urls = urls;
    this.dataDir = dataDir;
    this.journalMaxEntries = journalMaxEntries;
    this.loginNote = loginNote;
  }

  @Override
  public void register(ApiRouter r) {
    r.get("/info", ctx -> {
      ObjectNode info = Jsons.object();
      info.put("version", Main.VERSION);
      info.put("wiremockVersion", Version.getCurrentVersion());
      info.put("baseUrl", urls.server(ctx));
      info.put("dataDir", dataDir);
      info.put("authEnabled", auth.enabled());
      info.put("journalMaxEntries", journalMaxEntries);
      return info;
    });
    r.get("/auth/info", ctx -> Jsons.object().put("authEnabled", auth.enabled()).put("loginNote", loginNote));
    r.post("/auth/login", ctx -> {
      if (!auth.enabled()) return Jsons.object().put("username", "anonymous").put("role", "admin");
      var user = auth.authenticate(ctx.request.getHeader("Authorization"))
          .orElseThrow(() -> new ApiException(401, "Invalid username or password"));
      return Jsons.object().put("username", user.username()).put("role", user.role());
    });
  }
}
