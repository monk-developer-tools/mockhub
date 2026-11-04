package io.mockhub.graphql;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import graphql.introspection.IntrospectionResultToSchema;
import graphql.language.AstPrinter;
import graphql.language.Document;
import graphql.parser.InvalidSyntaxException;
import graphql.parser.Parser;
import graphql.parser.ParserOptions;
import io.mockhub.api.Jsons;
import java.util.Map;

public final class SchemaSource {
  public static final int MAX_SCHEMA_CHARS = 2 * 1024 * 1024;

  private SchemaSource() {}

  public static String toSdl(String input) {
    if (input == null || input.isBlank()) throw new GraphQlException("Paste a GraphQL schema (SDL) or an introspection result");
    if (input.length() > MAX_SCHEMA_CHARS) throw new GraphQlException("The schema is larger than 2 MB");
    String text = input.strip();
    if (text.startsWith("{")) return fromIntrospection(text);
    parse(text);
    return text;
  }

  static Document parse(String sdl) {
    try {
      ParserOptions options = ParserOptions.getDefaultSdlParserOptions().transform(o -> o.maxTokens(Integer.MAX_VALUE).maxCharacters(MAX_SCHEMA_CHARS));
      return Parser.parse(graphql.parser.ParserEnvironment.newParserEnvironment().document(sdl).parserOptions(options).build());
    } catch (InvalidSyntaxException e) {
      throw new GraphQlException("The schema isn't valid GraphQL: " + e.getMessage());
    }
  }

  private static String fromIntrospection(String json) {
    JsonNode root;
    try {
      root = Jsons.MAPPER.readTree(json);
    } catch (Exception e) {
      throw new GraphQlException("The introspection result isn't valid JSON");
    }
    JsonNode data = root.has("data") ? root.get("data") : root;
    if (!data.has("__schema")) throw new GraphQlException("The JSON has no __schema; send the result of an introspection query");
    Map<String, Object> map = Jsons.MAPPER.convertValue(data, new TypeReference<Map<String, Object>>() {});
    try {
      Document document = new IntrospectionResultToSchema().createSchemaDefinition(map);
      return AstPrinter.printAst(document);
    } catch (RuntimeException e) {
      throw new GraphQlException("The introspection result couldn't be read: " + e.getMessage());
    }
  }
}
