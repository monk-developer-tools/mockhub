package io.mockhub.graphql;

import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeUtil;
import graphql.schema.GraphQLUnionType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ExampleOperations {
  static final int MAX_DEPTH = 2;
  static final int MAX_FIELDS = 12;
  static final int MAX_UNION_MEMBERS = 3;

  public record Operation(String kind, String field, String operationName, String description, String query, Map<String, Object> variables) {}

  private final MockSchemas schemas;

  public ExampleOperations(MockSchemas schemas) {
    this.schemas = schemas;
  }

  public List<Operation> list(String sdl) {
    GraphQLSchema schema = schemas.schema(sdl);
    List<Operation> out = new ArrayList<>();
    Set<String> names = new HashSet<>();
    add(out, names, "query", schema.getQueryType());
    add(out, names, "mutation", schema.getMutationType());
    return out;
  }

  private void add(List<Operation> out, Set<String> names, String kind, GraphQLObjectType root) {
    if (root == null) return;
    for (GraphQLFieldDefinition field : root.getFieldDefinitions()) {
      if (field.getName().startsWith("__")) continue;
      String name = unique(names, pascal(field.getName()));
      List<String> declared = new ArrayList<>();
      List<String> passed = new ArrayList<>();
      Map<String, Object> variables = new LinkedHashMap<>();
      for (GraphQLArgument a : field.getArguments()) {
        if (!GraphQLTypeUtil.isNonNull(a.getType()) || a.hasSetDefaultValue()) continue;
        declared.add("$" + a.getName() + ": " + GraphQLTypeUtil.simplePrint(a.getType()));
        passed.add(a.getName() + ": $" + a.getName());
        variables.put(a.getName(), schemas.values().input(a.getType(), a.getName(), MockValues.mix(7, field.getName(), a.getName()), 0));
      }
      StringBuilder q = new StringBuilder(kind).append(' ').append(name);
      if (!declared.isEmpty()) q.append('(').append(String.join(", ", declared)).append(')');
      q.append(" {\n  ").append(field.getName());
      if (!passed.isEmpty()) q.append('(').append(String.join(", ", passed)).append(')');
      q.append(selection(field.getType(), 1)).append("\n}\n");
      out.add(new Operation(kind, field.getName(), name, field.getDescription() == null ? "" : field.getDescription(), q.toString(), variables));
    }
  }

  private String selection(GraphQLType type, int depth) {
    GraphQLType t = GraphQLTypeUtil.unwrapAll(type);
    if (GraphQLTypeUtil.isLeaf(t)) return "";
    String indent = "  ".repeat(depth + 1);
    String close = "\n" + "  ".repeat(depth) + "}";
    List<String> lines = new ArrayList<>();
    if (t instanceof GraphQLUnionType union) {
      lines.add("__typename");
      union.getTypes().stream().limit(MAX_UNION_MEMBERS).forEach(member -> {
        String inner = fields((GraphQLFieldsContainer) member, depth + 1, true);
        if (!inner.isEmpty()) lines.add("... on " + member.getName() + " {" + inner + "\n" + indent + "}");
      });
      return " {\n" + indent + String.join("\n" + indent, lines) + close;
    }
    String inner = fields((GraphQLFieldsContainer) t, depth, depth >= MAX_DEPTH);
    if (!(t instanceof GraphQLObjectType)) inner = "\n" + indent + "__typename" + inner;
    return inner.isEmpty() ? " {\n" + indent + "__typename" + close : " {" + inner + close;
  }

  private String fields(GraphQLFieldsContainer container, int depth, boolean leavesOnly) {
    String indent = "  ".repeat(depth + 1);
    StringBuilder out = new StringBuilder();
    int count = 0;
    for (GraphQLFieldDefinition f : container.getFieldDefinitions()) {
      if (count >= MAX_FIELDS) break;
      boolean needsArguments = f.getArguments().stream().anyMatch(a -> GraphQLTypeUtil.isNonNull(a.getType()) && !a.hasSetDefaultValue());
      if (needsArguments) continue;
      boolean leaf = GraphQLTypeUtil.isLeaf(GraphQLTypeUtil.unwrapAll(f.getType()));
      if (!leaf && leavesOnly) continue;
      out.append('\n').append(indent).append(f.getName()).append(leaf ? "" : selection(f.getType(), depth + 1));
      count++;
    }
    return out.toString();
  }

  private static String pascal(String name) {
    return name.isEmpty() ? "Operation" : name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
  }

  private static String unique(Set<String> names, String name) {
    String candidate = name;
    for (int i = 2; !names.add(candidate); i++) candidate = name + i;
    return candidate;
  }
}
