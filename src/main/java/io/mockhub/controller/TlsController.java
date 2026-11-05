package io.mockhub.controller;

import com.fasterxml.jackson.databind.JsonNode;
import io.mockhub.api.ApiException;
import io.mockhub.api.ApiRouter;
import io.mockhub.api.Ctx;
import io.mockhub.tls.TlsException;
import io.mockhub.tls.TlsService;
import java.util.function.Function;

public final class TlsController implements ApiController {
  private final TlsService tls;

  public TlsController(TlsService tls) {
    this.tls = tls;
  }

  @Override
  public void register(ApiRouter r) {
    r.get("/tls", ctx -> tls.view());
    r.put("/tls/server", ctx -> guarded(tls::setServer, pem(ctx)));
    r.delete("/tls/server", ctx -> tls.clearServer());
    r.put("/tls/client-authorities", ctx -> guarded(tls::setClients, pem(ctx)));
    r.delete("/tls/client-authorities", ctx -> tls.clearClients());
  }

  private Object guarded(Function<String, Object> change, String pem) {
    if (!tls.enabled()) throw new ApiException(409, "The HTTPS port is off. Start MockHub with MOCKHUB_HTTPS_PORT to use certificates");
    try {
      return change.apply(pem);
    } catch (TlsException e) {
      throw ApiException.badRequest(e.getMessage());
    }
  }

  private static String pem(Ctx ctx) {
    JsonNode body = ctx.json();
    return body == null ? null : body.path("pem").asText(null);
  }
}
