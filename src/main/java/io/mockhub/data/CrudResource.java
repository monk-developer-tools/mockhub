package io.mockhub.data;

import io.mockhub.api.ApiException;
import java.util.Locale;
import java.util.regex.Pattern;

public record CrudResource(String name, String idField, IdType idType) {
  private static final Pattern NAME = Pattern.compile("[a-z0-9][a-z0-9-]*(/[a-z0-9][a-z0-9-]*)*");
  private static final Pattern FIELD = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

  public enum IdType {
    UUID,
    NUMBER;

    public String id() {
      return name().toLowerCase(Locale.ROOT);
    }

    public static IdType parse(String value) {
      return value != null && value.equalsIgnoreCase("number") ? NUMBER : UUID;
    }
  }

  public CrudResource {
    name = name == null ? "" : name.trim().replaceAll("^/+|/+$", "").toLowerCase(Locale.ROOT);
    idField = idField == null || idField.isBlank() ? "id" : idField.trim();
    idType = idType == null ? IdType.UUID : idType;
    if (!NAME.matcher(name).matches()) {
      throw ApiException.badRequest("Resource name must be lowercase letters, digits and dashes, e.g. customers or v1/orders");
    }
    if (!FIELD.matcher(idField).matches()) throw ApiException.badRequest("The id field must be a plain field name, e.g. id");
  }
}
