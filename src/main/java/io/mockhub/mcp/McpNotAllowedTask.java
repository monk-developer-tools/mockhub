package io.mockhub.mcp;

import com.github.tomakehurst.wiremock.admin.AdminTask;
import com.github.tomakehurst.wiremock.common.url.PathParams;
import com.github.tomakehurst.wiremock.core.Admin;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import io.mockhub.api.Jsons;

import static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder.responseDefinition;

public final class McpNotAllowedTask implements AdminTask {
  @Override
  public ResponseDefinition execute(Admin admin, ServeEvent serveEvent, PathParams pathParams) {
    return responseDefinition()
        .withStatus(405)
        .withHeader("Allow", "POST")
        .withHeader("Content-Type", "application/json")
        .withBody(Jsons.write(Jsons.object().put("error", "This MCP server answers POST requests only; it has no event stream")))
        .build();
  }
}
