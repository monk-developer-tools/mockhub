package io.mockhub.web;

import com.github.tomakehurst.wiremock.admin.AdminTask;
import com.github.tomakehurst.wiremock.common.url.PathParams;
import com.github.tomakehurst.wiremock.core.Admin;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;
import io.mockhub.api.AdminPaths;

import static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder.responseDefinition;

public final class StaticUiTask implements AdminTask {
  public static final String UI_PATH = AdminPaths.ADMIN_ROOT + AdminPaths.UI_ROOT;
  private static final String RESOURCE_ROOT = "/mockhub-ui";

  private static final Map<String, String> TYPES = Map.ofEntries(
      Map.entry("html", "text/html; charset=utf-8"),
      Map.entry("js", "application/javascript; charset=utf-8"),
      Map.entry("mjs", "application/javascript; charset=utf-8"),
      Map.entry("css", "text/css; charset=utf-8"),
      Map.entry("json", "application/json"),
      Map.entry("svg", "image/svg+xml"),
      Map.entry("png", "image/png"),
      Map.entry("ico", "image/x-icon"),
      Map.entry("woff", "font/woff"),
      Map.entry("woff2", "font/woff2"),
      Map.entry("txt", "text/plain; charset=utf-8"),
      Map.entry("map", "application/json"));

  @Override
  public ResponseDefinition execute(Admin admin, ServeEvent serveEvent, PathParams pathParams) {
    String path = AdminPaths.withinAdmin(serveEvent.getRequest().getUrl());
    if (!path.startsWith(AdminPaths.UI_ROOT + "/")) {
      return responseDefinition().withStatus(302).withHeader("Location", UI_PATH + "/").build();
    }
    String file = path.substring(AdminPaths.UI_ROOT.length() + 1);
    if (file.contains("..")) {
      return responseDefinition().withStatus(400).withBody("Bad path").build();
    }

    byte[] content = file.isEmpty() ? null : read(file);
    boolean isAsset = content != null && file.startsWith("assets/");
    if (content == null) {
      if (file.startsWith("assets/")) {
        return responseDefinition().withStatus(404).withBody("Not found").build();
      }
      content = read("index.html");
      file = "index.html";
      if (content == null) {
        return responseDefinition()
            .withStatus(200)
            .withHeader("Content-Type", "text/html; charset=utf-8")
            .withBody("<h1>MockHub</h1><p>The UI was not bundled into this build. Run <code>npm run build</code> in <code>ui/</code> and rebuild.</p>"
                + "<p>The API is available at <code>/__admin/mockhub/api</code>.</p>")
            .build();
      }
    }
    return responseDefinition()
        .withStatus(200)
        .withHeader("Content-Type", contentType(file))
        .withHeader("Cache-Control", isAsset ? "public, max-age=31536000, immutable" : "no-cache")
        .withBody(content)
        .build();
  }

  private static byte[] read(String file) {
    try (InputStream in = StaticUiTask.class.getResourceAsStream(RESOURCE_ROOT + "/" + file)) {
      return in == null ? null : in.readAllBytes();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static String contentType(String file) {
    int dot = file.lastIndexOf('.');
    return dot < 0 ? "application/octet-stream" : TYPES.getOrDefault(file.substring(dot + 1).toLowerCase(), "application/octet-stream");
  }
}
