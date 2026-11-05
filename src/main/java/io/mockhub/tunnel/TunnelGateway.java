package io.mockhub.tunnel;

import io.mockhub.playground.Header;
import java.net.URI;
import java.time.Duration;
import java.util.List;

public interface TunnelGateway {
  String SCHEME = "tunnel";

  TunnelGateway NONE = (projectId, uri, method, headers, body, timeout) -> {
    throw new TunnelException(502, "Tunnels aren't available on this server");
  };

  TunnelMessages.Response send(String projectId, URI uri, String method, List<Header> headers, byte[] body, Duration timeout);

  static boolean isTunnel(String url) {
    return url != null && url.regionMatches(true, 0, SCHEME + "://", 0, SCHEME.length() + 3);
  }
}
