package io.mockhub.api;

public final class AdminPaths {

  public static final String ADMIN_ROOT = "/__admin";
  public static final String API_ROOT = "/mockhub/api";
  public static final String UI_ROOT = "/mockhub/ui";

  private AdminPaths() {}

  public static String withinAdmin(String url) {
    String path = url;
    int q = path.indexOf('?');
    if (q >= 0) path = path.substring(0, q);
    if (path.startsWith(ADMIN_ROOT)) path = path.substring(ADMIN_ROOT.length());
    return path;
  }

  public static String withinApi(String url) {
    String path = withinAdmin(url);
    if (path.startsWith(API_ROOT)) path = path.substring(API_ROOT.length());
    if (path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 1);
    return path;
  }
}
