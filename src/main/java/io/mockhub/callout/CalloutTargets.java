package io.mockhub.callout;

import com.github.tomakehurst.wiremock.http.Request;
import io.mockhub.playground.PlaygroundException;
import io.mockhub.playground.TargetGuard;
import io.mockhub.tunnel.TunnelGateway;
import java.net.URI;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.IntSupplier;

public final class CalloutTargets {
  public record Target(URI uri, boolean local) {}

  private final TargetGuard guard;
  private final IntSupplier port;
  private final String publicUrl;

  public CalloutTargets(TargetGuard guard, IntSupplier port, String publicUrl) {
    this.guard = guard;
    this.port = port;
    this.publicUrl = publicUrl;
  }

  public Target resolve(String url, Request incoming) {
    if (url.isBlank()) throw new CalloutException("The callout has no URL");
    if (url.startsWith("/")) return local(url);
    if (TunnelGateway.isTunnel(url)) {
      try {
        return new Target(URI.create(url), false);
      } catch (IllegalArgumentException e) {
        throw new CalloutException("\"" + url + "\" isn't a valid tunnel address, like tunnel://laptop/hooks");
      }
    }
    URI uri;
    try {
      uri = URI.create(url.contains("://") ? url : "https://" + url);
    } catch (IllegalArgumentException e) {
      throw new CalloutException("\"" + url + "\" isn't a valid URL");
    }
    String authority = uri.getRawAuthority() == null ? "" : uri.getRawAuthority().toLowerCase(Locale.ROOT);
    if (selfHosts(incoming).contains(authority)) {
      String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
      return local(path + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery()));
    }
    try {
      guard.check(uri);
    } catch (PlaygroundException e) {
      throw new CalloutException(e.getMessage());
    }
    return new Target(uri, false);
  }

  private Target local(String pathAndQuery) {
    URI uri;
    try {
      uri = URI.create("http://127.0.0.1:" + port.getAsInt() + pathAndQuery).normalize();
    } catch (IllegalArgumentException e) {
      throw new CalloutException("\"" + pathAndQuery + "\" isn't a valid path");
    }
    String path = String.valueOf(uri.getPath()).replaceAll("/{2,}", "/").toLowerCase(Locale.ROOT);
    if (path.startsWith("/__admin")) throw new CalloutException("Callouts can't call MockHub's admin API");
    return new Target(uri, true);
  }

  private Set<String> selfHosts(Request incoming) {
    Set<String> hosts = new LinkedHashSet<>();
    int p = port.getAsInt();
    hosts.add("localhost:" + p);
    hosts.add("127.0.0.1:" + p);
    String configured = authority(publicUrl);
    if (configured != null) hosts.add(configured);
    if (incoming != null) {
      for (String name : new String[] {"Host", "X-Forwarded-Host"}) {
        String value = incoming.getHeader(name);
        if (value != null && !value.isBlank()) hosts.add(value.split(",")[0].trim().toLowerCase(Locale.ROOT));
      }
    }
    return hosts;
  }

  private static String authority(String url) {
    if (url == null || url.isBlank()) return null;
    try {
      String a = URI.create(url).getRawAuthority();
      return a == null ? null : a.toLowerCase(Locale.ROOT);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}
