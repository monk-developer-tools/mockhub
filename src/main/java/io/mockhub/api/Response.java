package io.mockhub.api;

import com.github.tomakehurst.wiremock.http.ResponseDefinition;

import static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder.responseDefinition;

public final class Response {
  private Response() {}

  public static ResponseDefinition json(Object body) {
    return json(200, body);
  }

  public static ResponseDefinition json(int status, Object body) {
    return responseDefinition()
        .withStatus(status)
        .withHeader("Content-Type", "application/json")
        .withHeader("Cache-Control", "no-store")
        .withBody(body instanceof String s ? s : Jsons.write(body))
        .build();
  }

  public static ResponseDefinition noContent() {
    return responseDefinition().withStatus(204).build();
  }

  public static ResponseDefinition error(int status, String message) {
    return json(status, Jsons.object().put("error", message));
  }

  public static ResponseDefinition download(String filename, String contentType, byte[] content) {
    return responseDefinition()
        .withStatus(200)
        .withHeader("Content-Type", contentType)
        .withHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"")
        .withHeader("Cache-Control", "no-store")
        .withBody(content)
        .build();
  }
}
