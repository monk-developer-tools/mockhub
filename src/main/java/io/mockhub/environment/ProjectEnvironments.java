package io.mockhub.environment;

import com.fasterxml.jackson.databind.JsonNode;
import io.mockhub.api.ApiException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

public record ProjectEnvironments(String active, List<Environment> environments) {
  public static final String HEADER = "X-MockHub-Environment";
  public static final int MAX_ENVIRONMENTS = 10;
  public static final int MAX_VARIABLES = 100;
  public static final int MAX_VALUE = 4096;
  public static final ProjectEnvironments NONE = new ProjectEnvironments(null, List.of());
  private static final Pattern NAME = Pattern.compile("[a-z0-9][a-z0-9-]{0,29}");
  private static final Pattern VARIABLE = Pattern.compile("[A-Za-z_][A-Za-z0-9_.-]{0,63}");

  public ProjectEnvironments {
    environments = environments == null ? List.of() : List.copyOf(environments);
    String wanted = active;
    if (environments.isEmpty()) active = null;
    else if (wanted == null || environments.stream().noneMatch(e -> e.name().equals(wanted))) active = environments.get(0).name();
  }

  public boolean isEmpty() {
    return environments.isEmpty();
  }

  public Optional<Environment> find(String name) {
    return name == null ? Optional.empty() : environments.stream().filter(e -> e.name().equals(name)).findFirst();
  }

  public Optional<Environment> effective(String requested) {
    String wanted = requested == null ? "" : requested.trim().toLowerCase(java.util.Locale.ROOT);
    return find(wanted).or(() -> find(active));
  }

  public ProjectEnvironments withActive(String name) {
    if (find(name).isEmpty()) throw ApiException.notFound("Environment " + name);
    return new ProjectEnvironments(name, environments);
  }

  public static ProjectEnvironments parse(JsonNode body) {
    if (body == null || !body.isObject()) throw ApiException.badRequest("Send {\"active\": \"dev\", \"environments\": [{\"name\": \"dev\", \"variables\": {}}]}");
    JsonNode list = body.path("environments");
    if (!list.isMissingNode() && !list.isArray()) throw ApiException.badRequest("'environments' must be a list");
    if (list.size() > MAX_ENVIRONMENTS) throw ApiException.badRequest("A project can have at most " + MAX_ENVIRONMENTS + " environments");
    List<Environment> out = new ArrayList<>();
    Set<String> names = new HashSet<>();
    for (JsonNode e : list) {
      String name = e.path("name").asText("").trim();
      if (!NAME.matcher(name).matches()) {
        throw ApiException.badRequest("'" + name + "' isn't a valid environment name: use lowercase letters, digits and dashes, like qa or staging-eu");
      }
      if (!names.add(name)) throw ApiException.badRequest("There are two environments called " + name);
      JsonNode vars = e.path("variables");
      if (!vars.isMissingNode() && !vars.isNull() && !vars.isObject()) throw ApiException.badRequest("The variables of " + name + " must be an object");
      if (vars.size() > MAX_VARIABLES) throw ApiException.badRequest("An environment can have at most " + MAX_VARIABLES + " variables");
      Map<String, String> variables = new LinkedHashMap<>();
      vars.properties().forEach(v -> {
        if (!VARIABLE.matcher(v.getKey()).matches()) throw ApiException.badRequest("'" + v.getKey() + "' isn't a valid variable name, like baseUrl or tenant_id");
        String value = v.getValue().isValueNode() ? v.getValue().asText() : v.getValue().toString();
        if (value.length() > MAX_VALUE) throw ApiException.badRequest("Variable " + v.getKey() + " in " + name + " is longer than 4 KB");
        variables.put(v.getKey(), value);
      });
      out.add(new Environment(name, variables));
    }
    String active = body.path("active").asText("").trim();
    if (!active.isEmpty() && !names.contains(active)) throw ApiException.badRequest("The active environment " + active + " isn't in the list");
    return new ProjectEnvironments(active.isEmpty() ? null : active, out);
  }
}
