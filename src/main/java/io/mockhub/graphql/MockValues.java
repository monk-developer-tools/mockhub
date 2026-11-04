package io.mockhub.graphql;

import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNamedType;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeUtil;
import graphql.schema.GraphQLUnionType;
import io.mockhub.importer.SampleValues;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

final class MockValues {
  static final String SEED = "__seed";
  static final String TYPENAME = "__typename";
  static final int DEFAULT_LIST_SIZE = 3;
  static final int MAX_LIST_SIZE = 20;
  private static final List<String> SIZE_ARGUMENTS = List.of("first", "last", "limit", "take", "size", "count", "pageSize", "perPage");

  private final ThreadLocal<SampleValues> samples = ThreadLocal.withInitial(() -> new SampleValues(0));

  static long mix(long seed, Object... parts) {
    long h = seed * 0x9E3779B97F4A7C15L + 0x632BE59BD9B4E019L;
    for (Object p : parts) h = (h ^ (p == null ? 0 : p.hashCode())) * 0xBF58476D1CE4E5B9L;
    return h ^ (h >>> 31);
  }

  Object output(GraphQLSchema schema, GraphQLType type, String name, Map<String, Object> arguments, long seed) {
    GraphQLType t = GraphQLTypeUtil.unwrapNonNull(type);
    if (t instanceof GraphQLList list) {
      int size = listSize(arguments);
      List<Object> out = new ArrayList<>(size);
      for (int i = 0; i < size; i++) out.add(output(schema, list.getWrappedType(), name, null, mix(seed, i)));
      return out;
    }
    if (t instanceof GraphQLScalarType scalar) return scalar(scalar.getName(), name, seed);
    if (t instanceof GraphQLEnumType e) return e.getValues().get(new Random(seed).nextInt(e.getValues().size())).getName();
    if (t instanceof GraphQLObjectType object) return object(object, arguments, seed);
    if (t instanceof GraphQLInterfaceType || t instanceof GraphQLUnionType) {
      List<GraphQLObjectType> options = t instanceof GraphQLUnionType u
          ? u.getTypes().stream().filter(GraphQLObjectType.class::isInstance).map(GraphQLObjectType.class::cast).toList()
          : schema.getImplementations((GraphQLInterfaceType) t);
      if (options.isEmpty()) return null;
      GraphQLObjectType chosen = options.get(new Random(seed).nextInt(options.size()));
      Map<String, Object> out = object(chosen, arguments, seed);
      out.put(TYPENAME, chosen.getName());
      return out;
    }
    return null;
  }

  Object input(GraphQLInputType type, String name, long seed, int depth) {
    GraphQLType t = GraphQLTypeUtil.unwrapNonNull(type);
    if (t instanceof GraphQLList list) return List.of(input((GraphQLInputType) list.getWrappedType(), name, mix(seed, 0), depth));
    if (t instanceof GraphQLScalarType scalar) return scalar(scalar.getName(), name, seed);
    if (t instanceof GraphQLEnumType e) return e.getValues().get(0).getName();
    if (t instanceof GraphQLInputObjectType object) {
      Map<String, Object> out = new LinkedHashMap<>();
      if (depth > 3) return out;
      int optional = 0;
      for (GraphQLInputObjectField f : object.getFieldDefinitions()) {
        boolean required = GraphQLTypeUtil.isNonNull(f.getType()) && !f.hasSetDefaultValue();
        boolean leaf = GraphQLTypeUtil.isLeaf(GraphQLTypeUtil.unwrapAll(f.getType()));
        if (required || (leaf && optional++ < 6)) out.put(f.getName(), input(f.getType(), f.getName(), mix(seed, f.getName()), depth + 1));
      }
      return out;
    }
    return null;
  }

  private Map<String, Object> object(GraphQLObjectType type, Map<String, Object> arguments, long seed) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put(SEED, seed);
    if (arguments != null) echo(type, arguments, out);
    return out;
  }

  private static void echo(GraphQLObjectType type, Map<String, Object> arguments, Map<String, Object> out) {
    for (Map.Entry<String, Object> a : arguments.entrySet()) {
      if (a.getValue() instanceof Map<?, ?> nested) {
        nested.forEach((k, v) -> copyLeaf(type, String.valueOf(k), v, out));
      } else {
        copyLeaf(type, a.getKey(), a.getValue(), out);
      }
    }
  }

  private static void copyLeaf(GraphQLObjectType type, String name, Object value, Map<String, Object> out) {
    GraphQLFieldDefinition field = type.getFieldDefinition(name);
    if (field == null || value == null || value instanceof Map<?, ?>) return;
    GraphQLNamedType target = (GraphQLNamedType) GraphQLTypeUtil.unwrapAll(field.getType());
    if (GraphQLTypeUtil.isLeaf(target) && GraphQLTypeUtil.isList(GraphQLTypeUtil.unwrapNonNull(field.getType())) == value instanceof List<?>) out.put(name, value);
  }

  private Object scalar(String scalar, String name, long seed) {
    SampleValues s = samples.get().reseed(seed);
    String key = scalar.toLowerCase(Locale.ROOT);
    return switch (key) {
      case "id" -> String.valueOf(s.integer("id"));
      case "int" -> (int) s.integer(name);
      case "float" -> s.number(name);
      case "boolean" -> s.bool();
      case "string" -> s.text(name, "");
      case "datetime", "timestamp", "instant", "offsetdatetime", "zoneddatetime" -> s.text(name, "date-time");
      case "date", "localdate" -> s.text(name, "date");
      case "time", "localtime" -> LocalTime.of(8 + new Random(seed).nextInt(10), new Random(seed + 1).nextInt(4) * 15).toString() + ":00";
      case "uuid" -> new UUID(seed, mix(seed, "uuid")).toString();
      case "url", "uri" -> s.text(name, "uri");
      case "email", "emailaddress" -> s.text(name, "email");
      case "json", "jsonobject", "object", "map" -> new LinkedHashMap<String, Object>();
      case "long", "bigint", "short", "byte" -> s.integer(name);
      case "bigdecimal", "decimal", "money", "currency" -> s.number(name);
      default -> s.text(name, "");
    };
  }

  private static int listSize(Map<String, Object> arguments) {
    if (arguments != null) {
      for (String name : SIZE_ARGUMENTS) {
        if (arguments.get(name) instanceof Number n) return Math.max(0, Math.min(MAX_LIST_SIZE, n.intValue()));
      }
    }
    return DEFAULT_LIST_SIZE;
  }
}
