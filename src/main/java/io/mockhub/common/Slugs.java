package io.mockhub.common;

import java.util.regex.Pattern;

public final class Slugs {
  public static final Pattern SLUG = Pattern.compile("[a-z0-9]([a-z0-9-]{0,62}[a-z0-9])?");

  private Slugs() {}

  public static String slugify(String name, String fallback) {
    String s = name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
    if (s.length() > 64) s = s.substring(0, 64).replaceAll("-+$", "");
    return s.isEmpty() ? fallback : s;
  }

  public static boolean valid(String slug) {
    return SLUG.matcher(slug).matches();
  }
}
