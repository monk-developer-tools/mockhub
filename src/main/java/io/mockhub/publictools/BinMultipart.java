package io.mockhub.publictools;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class BinMultipart {
  static final int MAX_PARTS = 100;
  private static final Pattern BOUNDARY = Pattern.compile("boundary=\"?([^\";]+)\"?", Pattern.CASE_INSENSITIVE);
  private static final Pattern DISPOSITION = Pattern.compile("content-disposition:([^\\r\\n]*)", Pattern.CASE_INSENSITIVE);
  private static final Pattern NAME = Pattern.compile("\\bname=\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);
  private static final Pattern FILENAME = Pattern.compile("filename=\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);
  private static final Pattern TYPE = Pattern.compile("content-type:\\s*([^\\r\\n]*)", Pattern.CASE_INSENSITIVE);

  private BinMultipart() {}

  record Part(String name, String filename, String type, byte[] bytes) {}

  static List<Part> parts(byte[] body, String contentType) {
    if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("multipart/")) return List.of();
    Matcher b = BOUNDARY.matcher(contentType);
    if (!b.find()) return List.of();
    byte[] marker = ("--" + b.group(1)).getBytes(StandardCharsets.UTF_8);
    byte[] blank = "\r\n\r\n".getBytes(StandardCharsets.UTF_8);
    List<Part> out = new ArrayList<>();
    int at = indexOf(body, marker, 0);
    while (at >= 0 && out.size() < MAX_PARTS) {
      int start = at + marker.length;
      if (start + 1 < body.length && body[start] == '-' && body[start + 1] == '-') break;
      int next = indexOf(body, marker, start);
      if (next < 0) break;
      int headEnd = indexOf(body, blank, start);
      if (headEnd < 0 || headEnd > next) break;
      String head = new String(body, start, headEnd - start, StandardCharsets.UTF_8);
      String disposition = first(DISPOSITION, head);
      int end = next >= 2 && body[next - 2] == '\r' && body[next - 1] == '\n' ? next - 2 : next;
      out.add(new Part(first(NAME, disposition), first(FILENAME, disposition), first(TYPE, head).trim(),
          Arrays.copyOfRange(body, headEnd + blank.length, Math.max(headEnd + blank.length, end))));
      at = next;
    }
    return List.copyOf(out);
  }

  private static String first(Pattern p, String text) {
    Matcher m = p.matcher(text);
    return m.find() ? m.group(1) : "";
  }

  private static int indexOf(byte[] bytes, byte[] needle, int from) {
    outer:
    for (int i = Math.max(0, from); i <= bytes.length - needle.length; i++) {
      for (int j = 0; j < needle.length; j++) if (bytes[i + j] != needle[j]) continue outer;
      return i;
    }
    return -1;
  }
}
