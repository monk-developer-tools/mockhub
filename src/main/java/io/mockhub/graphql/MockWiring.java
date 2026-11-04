package io.mockhub.graphql;

import graphql.GraphQLContext;
import graphql.TypeResolutionEnvironment;
import graphql.execution.CoercedVariables;
import graphql.language.ArrayValue;
import graphql.language.BooleanValue;
import graphql.language.EnumValue;
import graphql.language.FloatValue;
import graphql.language.IntValue;
import graphql.language.ObjectField;
import graphql.language.ObjectValue;
import graphql.language.StringValue;
import graphql.language.Value;
import graphql.language.VariableReference;
import graphql.schema.Coercing;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.TypeResolver;
import graphql.schema.idl.FieldWiringEnvironment;
import graphql.schema.idl.InterfaceWiringEnvironment;
import graphql.schema.idl.ScalarWiringEnvironment;
import graphql.schema.idl.UnionWiringEnvironment;
import graphql.schema.idl.WiringFactory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

final class MockWiring implements WiringFactory {
  static final String BUDGET = "mockhub.budget";
  static final int MAX_VALUES = 50_000;

  private final MockValues values;

  MockWiring(MockValues values) {
    this.values = values;
  }

  @Override
  public boolean providesScalar(ScalarWiringEnvironment environment) {
    return !ScalarInfo.isGraphqlSpecifiedScalar(environment.getScalarTypeDefinition().getName());
  }

  @Override
  public GraphQLScalarType getScalar(ScalarWiringEnvironment environment) {
    String name = environment.getScalarTypeDefinition().getName();
    return GraphQLScalarType.newScalar().name(name).description("Mocked scalar " + name).coercing(new PassThrough()).build();
  }

  @Override
  public boolean providesTypeResolver(InterfaceWiringEnvironment environment) {
    return true;
  }

  @Override
  public TypeResolver getTypeResolver(InterfaceWiringEnvironment environment) {
    return MockWiring::resolve;
  }

  @Override
  public boolean providesTypeResolver(UnionWiringEnvironment environment) {
    return true;
  }

  @Override
  public TypeResolver getTypeResolver(UnionWiringEnvironment environment) {
    return MockWiring::resolve;
  }

  @Override
  public boolean providesDataFetcher(FieldWiringEnvironment environment) {
    return true;
  }

  @Override
  public DataFetcher<?> getDataFetcher(FieldWiringEnvironment environment) {
    return this::fetch;
  }

  private Object fetch(graphql.schema.DataFetchingEnvironment env) {
    String name = env.getFieldDefinition().getName();
    Object source = env.getSource();
    if (source instanceof Map<?, ?> map && map.containsKey(name)) return map.get(name);
    AtomicInteger budget = env.getGraphQlContext().get(BUDGET);
    if (budget != null && budget.incrementAndGet() > MAX_VALUES) {
      throw new GraphQlException("The answer would hold more than " + MAX_VALUES + " values; ask for fewer fields or smaller lists");
    }
    long parent = source instanceof Map<?, ?> map && map.get(MockValues.SEED) instanceof Long l ? l : 17L;
    long seed = MockValues.mix(parent, name, String.valueOf(env.getArguments()));
    return values.output(env.getGraphQLSchema(), env.getFieldType(), name, env.getArguments(), seed);
  }

  private static GraphQLObjectType resolve(TypeResolutionEnvironment env) {
    Object object = env.getObject();
    if (object instanceof Map<?, ?> map && map.get(MockValues.TYPENAME) instanceof String type) {
      GraphQLObjectType found = env.getSchema().getObjectType(type);
      if (found != null) return found;
    }
    List<GraphQLObjectType> all = env.getFieldType() instanceof graphql.schema.GraphQLInterfaceType i
        ? env.getSchema().getImplementations(i)
        : env.getFieldType() instanceof graphql.schema.GraphQLUnionType u
            ? u.getTypes().stream().filter(GraphQLObjectType.class::isInstance).map(GraphQLObjectType.class::cast).toList()
            : List.of();
    return all.isEmpty() ? null : all.get(0);
  }

  private static final class PassThrough implements Coercing<Object, Object> {
    @Override
    public Object serialize(Object value, GraphQLContext context, Locale locale) {
      return value;
    }

    @Override
    public Object parseValue(Object value, GraphQLContext context, Locale locale) {
      return value;
    }

    @Override
    public Object parseLiteral(Value<?> value, CoercedVariables variables, GraphQLContext context, Locale locale) {
      return literal(value, variables.toMap());
    }

    private static Object literal(Value<?> value, Map<String, Object> variables) {
      if (value instanceof StringValue s) return s.getValue();
      if (value instanceof IntValue i) return i.getValue().longValue();
      if (value instanceof FloatValue f) return f.getValue().doubleValue();
      if (value instanceof BooleanValue b) return b.isValue();
      if (value instanceof EnumValue e) return e.getName();
      if (value instanceof VariableReference v) return variables.get(v.getName());
      if (value instanceof ArrayValue a) {
        List<Object> out = new ArrayList<>();
        a.getValues().forEach(v -> out.add(literal(v, variables)));
        return out;
      }
      if (value instanceof ObjectValue o) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (ObjectField f : o.getObjectFields()) out.put(f.getName(), literal(f.getValue(), variables));
        return out;
      }
      return null;
    }
  }
}
