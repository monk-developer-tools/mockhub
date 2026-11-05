package io.mockhub.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import io.mockhub.api.ApiException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public record IpAllowList(List<String> entries) {
  private static final Pattern V4 = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}");
  private static final Pattern V6 = Pattern.compile("[0-9a-fA-F:.]*:[0-9a-fA-F:.]*");
  private static final int MAX_ENTRIES = 100;

  public IpAllowList {
    entries = entries == null ? List.of() : List.copyOf(entries);
  }

  static IpAllowList parse(JsonNode node) {
    List<String> out = new ArrayList<>();
    if (node == null || !node.isArray()) return new IpAllowList(out);
    for (JsonNode item : node) {
      String entry = item.asText("").trim();
      if (entry.isEmpty()) continue;
      if (Range.parse(entry) == null) throw ApiException.badRequest("'" + entry + "' isn't an IP address or CIDR range like 10.0.0.0/8");
      if (!out.contains(entry)) out.add(entry);
    }
    if (out.size() > MAX_ENTRIES) throw ApiException.badRequest("The IP allow list can have at most " + MAX_ENTRIES + " entries");
    return new IpAllowList(out);
  }

  public static IpAllowList ofText(String commaSeparated) {
    List<String> out = new ArrayList<>();
    for (String part : commaSeparated.split(",")) {
      String entry = part.trim();
      if (entry.isEmpty()) continue;
      if (Range.parse(entry) == null) throw new IllegalArgumentException("'" + entry + "' isn't an IP address or CIDR range like 10.0.0.0/8");
      out.add(entry);
    }
    return new IpAllowList(out);
  }

  public boolean isEmpty() {
    return entries.isEmpty();
  }

  public boolean allows(String ip) {
    if (entries.isEmpty()) return true;
    byte[] address = Range.bytes(ip == null ? "" : ip.trim());
    if (address == null) return false;
    return entries.stream().map(Range::parse).anyMatch(r -> r != null && r.contains(address));
  }

  record Range(byte[] network, int prefix) {
    static Range parse(String entry) {
      int slash = entry.indexOf('/');
      byte[] network = bytes(slash < 0 ? entry : entry.substring(0, slash));
      if (network == null) return null;
      int bits = network.length * 8;
      int prefix = bits;
      if (slash >= 0) {
        try {
          prefix = Integer.parseInt(entry.substring(slash + 1));
        } catch (NumberFormatException e) {
          return null;
        }
        if (prefix < 0 || prefix > bits) return null;
      }
      return new Range(network, prefix);
    }

    static byte[] bytes(String literal) {
      String host = literal.startsWith("[") && literal.endsWith("]") ? literal.substring(1, literal.length() - 1) : literal;
      boolean v4 = V4.matcher(host).matches();
      if (!v4 && !V6.matcher(host).matches()) return null;
      if (v4) {
        for (String part : host.split("\\.")) if (Integer.parseInt(part) > 255) return null;
      }
      try {
        byte[] raw = InetAddress.getByName(host).getAddress();
        if (raw.length == 16 && isMappedV4(raw)) return new byte[] {raw[12], raw[13], raw[14], raw[15]};
        return raw;
      } catch (UnknownHostException e) {
        return null;
      }
    }

    private static boolean isMappedV4(byte[] raw) {
      for (int i = 0; i < 10; i++) if (raw[i] != 0) return false;
      return raw[10] == (byte) 0xff && raw[11] == (byte) 0xff;
    }

    boolean contains(byte[] address) {
      if (address.length != network.length) return false;
      int full = prefix / 8;
      for (int i = 0; i < full; i++) if (address[i] != network[i]) return false;
      int rest = prefix % 8;
      if (rest == 0) return true;
      int mask = 0xff << (8 - rest) & 0xff;
      return (address[full] & mask) == (network[full] & mask);
    }
  }
}
