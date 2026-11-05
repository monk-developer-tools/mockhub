package io.mockhub.controller;

import io.mockhub.api.ApiException;
import io.mockhub.api.ApiRouter;
import io.mockhub.tunnel.TunnelException;
import io.mockhub.tunnel.TunnelHub;

public final class TunnelController implements ApiController {
  private final TunnelHub tunnels;

  public TunnelController(TunnelHub tunnels) {
    this.tunnels = tunnels;
  }

  @Override
  public void register(ApiRouter r) {
    r.get("/tunnels", ctx -> tunnels.list(ctx.principal()));
    r.delete("/tunnels/{name}", ctx -> {
      try {
        tunnels.close(ctx.path("name"), ctx.principal());
      } catch (TunnelException e) {
        throw new ApiException(e.status(), e.getMessage());
      }
      return null;
    });
  }
}
