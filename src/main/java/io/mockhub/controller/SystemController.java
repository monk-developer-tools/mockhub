package io.mockhub.controller;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.core.Version;
import io.mockhub.Main;
import io.mockhub.api.ApiRouter;
import io.mockhub.api.Jsons;
import io.mockhub.auth.AuthService;

public final class SystemController implements ApiController {
  private final AuthService auth;
  private final BaseUrls urls;
  private final String dataDir;
  private final int journalMaxEntries;

  public SystemController(AuthService auth, BaseUrls urls, String dataDir, int journalMaxEntries) {
    this.auth = auth;
    this.urls = urls;
    this.dataDir = dataDir;
    this.journalMaxEntries = journalMaxEntries;
  }

  @Override
  public void register(ApiRouter r) {
    r.get("/info", ctx -> {
      ObjectNode info = Jsons.object();
      info.put("version", Main.VERSION);
      info.put("wiremockVersion", Version.getCurrentVersion());
      info.put("baseUrl", urls.server(ctx));
      info.put("dataDir", ctx.principal().isAdmin() ? dataDir : "");
      info.put("authEnabled", auth.enabled());
      info.put("journalMaxEntries", journalMaxEntries);
      return info;
    });
  }
}
