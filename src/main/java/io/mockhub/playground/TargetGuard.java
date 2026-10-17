package io.mockhub.playground;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

public final class TargetGuard {
  public interface Resolver {
    InetAddress[] resolve(String host) throws UnknownHostException;
  }

  private final boolean allowPrivate;
  private final Resolver resolver;

  public TargetGuard(boolean allowPrivate, Resolver resolver) {
    this.allowPrivate = allowPrivate;
    this.resolver = resolver;
  }

  public void check(URI uri) {
    String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
    if (!scheme.equals("http") && !scheme.equals("https")) throw new PlaygroundException("Only http and https URLs can be called");
    String host = uri.getHost();
    if (host == null || host.isBlank()) throw new PlaygroundException("The URL needs a host, e.g. https://api.example.com/users");
    if (allowPrivate) return;
    InetAddress[] addresses;
    try {
      addresses = resolver.resolve(host);
    } catch (UnknownHostException e) {
      throw new PlaygroundException("Can't find the host " + host);
    }
    for (InetAddress address : addresses) {
      if (isInternal(address)) {
        throw new PlaygroundException(host + " is a private or local address. An admin can allow these with MOCKHUB_PLAYGROUND_ALLOW_PRIVATE=true.");
      }
    }
  }

  static boolean isInternal(InetAddress a) {
    if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress() || a.isSiteLocalAddress() || a.isMulticastAddress()) {
      return true;
    }
    byte[] b = a.getAddress();
    if (a instanceof Inet4Address) {
      int first = b[0] & 0xff;
      int second = b[1] & 0xff;
      return first == 0 || (first == 100 && second >= 64 && second <= 127) || (first == 198 && (second == 18 || second == 19)) || first >= 240;
    }
    if (a instanceof Inet6Address) {
      if ((b[0] & 0xfe) == 0xfc) return true;
      boolean mapped = true;
      for (int i = 0; i < 10; i++) mapped &= b[i] == 0;
      mapped &= (b[10] & 0xff) == 0xff && (b[11] & 0xff) == 0xff;
      if (mapped) {
        try {
          return isInternal(InetAddress.getByAddress(new byte[] {b[12], b[13], b[14], b[15]}));
        } catch (UnknownHostException e) {
          return true;
        }
      }
    }
    return false;
  }
}
