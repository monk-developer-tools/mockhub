package io.mockhub.grpc;

import java.util.regex.Pattern;

public final class DescriptorNames {
  private static final Pattern VALID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,99}\\.(dsc|desc|pb|protoset|bin|proto)");
  private static final Pattern PROJECT = Pattern.compile("[A-Za-z0-9_-]{1,100}");

  private DescriptorNames() {}

  public static boolean isValid(String name) {
    return name != null && VALID.matcher(name).matches() && !name.contains("..");
  }

  public static String require(String name) {
    if (!isValid(name)) {
      throw new GrpcException("Name the descriptor like library.dsc: letters, digits, dots, dashes, ending in .proto, .dsc, .desc, .pb, .protoset or .bin");
    }
    return name;
  }

  static String safe(String projectId) {
    if (projectId == null || !PROJECT.matcher(projectId).matches()) throw new IllegalArgumentException("Invalid project id");
    return projectId;
  }
}
