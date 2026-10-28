package io.mockhub.importer;

import io.mockhub.api.ApiException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public record WireMockArchive(Map<String, String> mappings, Map<String, byte[]> files) {
  public static final int MAX_ENTRIES = 20_000;
  public static final long MAX_TOTAL_BYTES = 200L * 1024 * 1024;
  public static final long MAX_ENTRY_BYTES = 10L * 1024 * 1024;
  private static final String MAPPINGS = "mappings/";
  private static final String FILES = "__files/";

  public static WireMockArchive read(byte[] zip) {
    if (zip == null || zip.length == 0) throw ApiException.badRequest("Send a .zip file as the request body");
    Map<String, String> mappings = new LinkedHashMap<>();
    Map<String, byte[]> files = new LinkedHashMap<>();
    long total = 0;
    int entries = 0;
    try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
      ZipEntry entry;
      while ((entry = in.getNextEntry()) != null) {
        if (++entries > MAX_ENTRIES) throw ApiException.badRequest("The archive has more than " + MAX_ENTRIES + " entries");
        String name = entry.getName().replace('\\', '/');
        if (entry.isDirectory() || ignored(name)) continue;
        byte[] content = readLimited(in, name);
        total += content.length;
        if (total > MAX_TOTAL_BYTES) throw new ApiException(413, "The archive unpacks to more than 200 MB");
        int filesAt = name.lastIndexOf(FILES);
        int mappingsAt = name.lastIndexOf(MAPPINGS);
        if (filesAt >= 0 && filesAt >= mappingsAt && atSegmentStart(name, filesAt)) {
          files.put(name.substring(filesAt + FILES.length()), content);
        } else if (mappingsAt >= 0 && atSegmentStart(name, mappingsAt) && name.toLowerCase().endsWith(".json")) {
          mappings.put(name.substring(mappingsAt + MAPPINGS.length()), new String(content, StandardCharsets.UTF_8));
        }
      }
    } catch (IOException e) {
      throw ApiException.badRequest("Not a readable .zip file: " + e.getMessage());
    }
    if (mappings.isEmpty()) {
      throw ApiException.badRequest("No mappings found. Zip the folder that holds the mappings and __files folders.");
    }
    return new WireMockArchive(mappings, files);
  }

  private static boolean atSegmentStart(String name, int index) {
    return index == 0 || name.charAt(index - 1) == '/';
  }

  private static boolean ignored(String name) {
    if (name.startsWith("__MACOSX/") || name.contains("/__MACOSX/")) return true;
    String file = name.substring(name.lastIndexOf('/') + 1);
    return file.isEmpty() || file.startsWith(".");
  }

  private static byte[] readLimited(InputStream in, String name) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buffer = new byte[8192];
    long size = 0;
    int read;
    while ((read = in.read(buffer)) > 0) {
      size += read;
      if (size > MAX_ENTRY_BYTES) throw new ApiException(413, name + " is larger than 10 MB");
      out.write(buffer, 0, read);
    }
    return out.toByteArray();
  }
}
