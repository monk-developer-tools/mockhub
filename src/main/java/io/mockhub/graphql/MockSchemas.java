package io.mockhub.graphql;

import graphql.GraphQL;
import graphql.analysis.MaxQueryDepthInstrumentation;
import graphql.language.Directive;
import graphql.language.DirectiveDefinition;
import graphql.language.Document;
import graphql.language.Node;
import graphql.language.NodeVisitorStub;
import graphql.language.AstTransformer;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.errors.SchemaProblem;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;
import graphql.util.TreeTransformerUtil;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class MockSchemas {
  static final int MAX_DEPTH = 15;
  private static final int CACHE_SIZE = 32;
  private static final Set<String> BUILT_IN_DIRECTIVES = Set.of("skip", "include", "deprecated", "specifiedBy", "oneOf", "defer",
      "experimental_disableErrorPropagation");

  private final MockValues values = new MockValues();
  private final Map<String, GraphQL> cache = new LinkedHashMap<>(16, 0.75f, true) {
    @Override
    protected boolean removeEldestEntry(Map.Entry<String, GraphQL> eldest) {
      return size() > CACHE_SIZE;
    }
  };

  public GraphQL engine(String sdl) {
    String key = digest(sdl);
    synchronized (cache) {
      GraphQL found = cache.get(key);
      if (found != null) return found;
    }
    GraphQL built = GraphQL.newGraphQL(schema(sdl)).instrumentation(new MaxQueryDepthInstrumentation(MAX_DEPTH)).build();
    synchronized (cache) {
      cache.put(key, built);
    }
    return built;
  }

  public GraphQLSchema schema(String sdl) {
    Document document = withoutUnknownDirectives(SchemaSource.parse(sdl));
    try {
      TypeDefinitionRegistry registry = new SchemaParser().buildRegistry(document);
      if (registry.getType("Query").isEmpty() && registry.schemaDefinition().isEmpty()) {
        throw new GraphQlException("The schema needs a Query type");
      }
      return new SchemaGenerator().makeExecutableSchema(registry, RuntimeWiring.newRuntimeWiring().wiringFactory(new MockWiring(values)).build());
    } catch (SchemaProblem e) {
      throw new GraphQlException("The schema has problems: " + e.getErrors().stream().map(Object::toString).limit(3).collect(Collectors.joining("; ")));
    } catch (GraphQlException e) {
      throw e;
    } catch (RuntimeException e) {
      throw new GraphQlException("The schema couldn't be built: " + e.getMessage());
    }
  }

  MockValues values() {
    return values;
  }

  private static Document withoutUnknownDirectives(Document document) {
    Set<String> declared = new HashSet<>(BUILT_IN_DIRECTIVES);
    document.getDefinitionsOfType(DirectiveDefinition.class).forEach(d -> declared.add(d.getName()));
    NodeVisitorStub visitor = new NodeVisitorStub() {
      @Override
      public TraversalControl visitDirective(Directive node, TraverserContext<Node> context) {
        return declared.contains(node.getName()) ? TraversalControl.CONTINUE : TreeTransformerUtil.deleteNode(context);
      }
    };
    return (Document) new AstTransformer().transform(document, visitor);
  }

  private static String digest(String sdl) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(sdl.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
