package io.mockhub.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import io.mockhub.api.ApiException;
import io.mockhub.api.ApiRouter;
import io.mockhub.api.Ctx;
import io.mockhub.api.Jsons;
import io.mockhub.fake.DataGenerator;
import io.mockhub.playground.PlaygroundException;
import io.mockhub.playground.PlaygroundHistory;
import io.mockhub.playground.PlaygroundRequest;
import io.mockhub.playground.PlaygroundService;
import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class PlaygroundController implements ApiController {
  private final PlaygroundService playground;
  private final PlaygroundHistory history;
  private final DataGenerator generator;
  private final String publicUrl;

  public PlaygroundController(PlaygroundService playground, PlaygroundHistory history, DataGenerator generator, String publicUrl) {
    this.playground = playground;
    this.history = history;
    this.generator = generator;
    this.publicUrl = publicUrl;
  }

  @Override
  public void register(ApiRouter r) {
    r.post("/playground/send", ctx -> {
      PlaygroundRequest request = Jsons.MAPPER.convertValue(ctx.json(), PlaygroundRequest.class);
      try {
        return Jsons.MAPPER.valueToTree(playground.send(user(ctx), request, selfHosts(ctx)));
      } catch (PlaygroundException e) {
        throw ApiException.badRequest(e.getMessage());
      }
    });
    r.get("/playground/history", ctx -> Jsons.MAPPER.valueToTree(history.list(user(ctx))));
    r.delete("/playground/history", ctx -> {
      history.clear(user(ctx));
      return null;
    });
    r.delete("/playground/history/{entryId}", ctx -> {
      if (!history.delete(user(ctx), ctx.path("entryId"))) throw ApiException.notFound("History entry " + ctx.path("entryId"));
      return null;
    });
    r.post("/fake/generate", ctx -> {
      JsonNode body = ctx.json();
      List<DataGenerator.Field> fields = Jsons.MAPPER.convertValue(body.path("fields"), new TypeReference<>() {});
      String locale = body.path("locale").asText("");
      return Jsons.object().set("records", generator.generate(fields, body.path("count").asInt(10), locale.isBlank() ? null : locale));
    });
  }

  private static String user(Ctx ctx) {
    return ctx.principal() == null || ctx.principal().anonymous() ? "anonymous" : ctx.principal().id();
  }

  private Set<String> selfHosts(Ctx ctx) {
    Set<String> hosts = new LinkedHashSet<>();
    String host = ctx.request.getHeader("Host");
    if (host != null && !host.isBlank()) hosts.add(host.toLowerCase(Locale.ROOT));
    String configured = authority(publicUrl);
    if (configured != null) hosts.add(configured.toLowerCase(Locale.ROOT));
    return hosts;
  }

  private static String authority(String url) {
    if (url == null || url.isBlank()) return null;
    try {
      return URI.create(url).getRawAuthority();
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}
